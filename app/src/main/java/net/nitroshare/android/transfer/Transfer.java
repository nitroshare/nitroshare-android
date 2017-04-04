package net.nitroshare.android.transfer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import net.nitroshare.android.bundle.Bundle;
import net.nitroshare.android.bundle.FileItem;
import net.nitroshare.android.bundle.Item;
import net.nitroshare.android.discovery.Device;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Perform a transfer from one device to another
 *
 * This class takes care of communicating (via socket) with another device to
 * transfer a bundle (list of items) using packets.
 */
public class Transfer implements Runnable {

    private static final int CHUNK_SIZE = 65536;
    private static final Gson mGson = new Gson();

    /**
     * Listener for confirmation that the remote peer is connected
     */
    interface ConnectListener {
        void onConnect();
    }

    /**
     * Listener for receiving the transfer header
     */
    interface HeaderListener {
        void onHeader();
    }

    /**
     * Listener for transfer progress events
     */
    interface ProgressListener {
        void onProgress();
    }

    /**
     * Listener for receiving individual items
     */
    interface ItemListener {
        void onItem(Item item);
    }

    /**
     * Listener for successful completion of the transfer
     */
    interface SuccessListener {
        void onSuccess();
    }

    /**
     * Listener for an error causing the transfer to abort
     */
    interface ErrorListener {
        void onError();
    }

    /**
     * Listener for transfer completion
     *
     * This callback is always invoked after all SuccessListeners and
     * ErrorListeners have been invoked.
     */
    interface FinishListener {
        void onFinish();
    }

    /**
     * Direction of transfer relative to the current device
     */
    public enum Direction {
        Receive,
        Send,
    }

    /**
     * State of the transfer
     */
    public enum State {
        Connecting,
        Transferring,
        Failed,
        Succeeded,
    };

    /**
     * Transfer header
     */
    private class TransferHeader {
        String name;
        String count;
        String size;
    }

    // Internal state of the transfer
    private enum InternalState {
        TransferHeader,
        ItemHeader,
        ItemContent,
        Finished,
    }

    private final Object mSync = new Object();
    private int mId;

    private volatile boolean mStop = false;
    private Selector mSelector = Selector.open();
    private SocketChannel mSocketChannel;

    private Device mDevice;
    private String mDeviceName;
    private String mTransferDirectory;
    private boolean mOverwrite;
    private Bundle mBundle;

    private Direction mDirection;
    private State mState;
    private InternalState mInternalState = InternalState.TransferHeader;

    private Packet mReceivingPacket;
    private Packet mSendingPacket;

    private int mTransferItems;
    private long mTransferBytesTotal;
    private long mTransferBytesTransferred;

    private Item mItem;
    private int mItemIndex;
    private long mItemBytesRemaining;

    private int mProgress;
    private String mError;

    private final List<ConnectListener> mConnectListeners = new ArrayList<>();
    private final List<HeaderListener> mHeaderListeners = new ArrayList<>();
    private final List<ProgressListener> mProgressListeners = new ArrayList<>();
    private final List<ItemListener> mItemListeners = new ArrayList<>();
    private final List<SuccessListener> mSuccessListeners = new ArrayList<>();
    private final List<ErrorListener> mErrorListeners = new ArrayList<>();
    private final List<FinishListener> mFinishListeners = new ArrayList<>();

    /**
     * Create a transfer for receiving items
     * @param socketChannel incoming channel
     * @param transferDirectory directory for incoming files
     * @param overwrite true to overwrite existing files
     * @param unknownDeviceName device name shown before being received
     * @throws IOException
     */
    public Transfer(SocketChannel socketChannel, String transferDirectory, boolean overwrite, String unknownDeviceName) throws IOException {
        mSocketChannel = socketChannel;
        mSocketChannel.configureBlocking(false);
        mDeviceName = unknownDeviceName;
        mTransferDirectory = transferDirectory;
        mOverwrite = overwrite;
        mDirection = Direction.Receive;
        mState = State.Transferring;
    }

    /**
     * Create a transfer for sending items
     * @param device device to connect to
     * @param deviceName device name to send to the remote device
     * @param bundle bundle to transfer
     * @throws IOException
     */
    public Transfer(Device device, String deviceName, Bundle bundle) throws IOException {
        mSocketChannel = SocketChannel.open();
        mSocketChannel.configureBlocking(false);
        mDevice = device;
        mDeviceName = deviceName;
        mBundle = bundle;
        mDirection = Direction.Send;
        mState = State.Connecting;
        mTransferItems = mBundle.size();
        mTransferBytesTotal = mBundle.getTotalSize();
    }

    /**
     * Set the ID for the transfer
     */
    void setId(int id) {
        mId = id;
    }

    /**
     * Retrieve the transfer ID
     */
    int getId() {
        return mId;
    }

    /**
     * Retrieve the direction of the transfer
     * @return transfer direction
     */
    Direction getDirection() {
        return mDirection;
    }

    /**
     * Retrieve the name of the remote device
     * @return remote device name
     */
    String getRemoteDeviceName() {
        synchronized (mSync) {
            return mDirection == Direction.Receive ? mDeviceName : mDevice.getName();
        }
    }

    /**
     * Retrieve the current state of the transfer
     */
    State getState() {
        synchronized (mSync) {
            return mState;
        }
    }

    /**
     * Retrieve the current progress of the transfer
     */
    int getProgress() {
        synchronized (mSync) {
            return mProgress;
        }
    }

    String getError() {
        synchronized (mSync) {
            return mError;
        }
    }

    /**
     * Add a listener for connection events
     */
    void addConnectListener(ConnectListener connectListener) {
        mConnectListeners.add(connectListener);
    }

    /**
     * Add a listener for receiving the transfer header
     */
    void addHeaderListener(HeaderListener headerListener) {
        mHeaderListeners.add(headerListener);
    }

    /**
     * Add a listener for progress events
     */
    void addProgressListener(ProgressListener progressListener) {
        mProgressListeners.add(progressListener);
    }

    /**
     * Add a listener for receiving items
     */
    void addItemListener(ItemListener itemListener) {
        mItemListeners.add(itemListener);
    }

    /**
     * Add a listener for success events
     */
    void addSuccessListener(SuccessListener successListener) {
        mSuccessListeners.add(successListener);
    }

    /**
     * Add a listener for error events
     */
    void addErrorListener(ErrorListener errorListener) {
        mErrorListeners.add(errorListener);
    }

    /**
     * Add a listener for completion events
     */
    void addFinishListener(FinishListener finishListener) {
        mFinishListeners.add(finishListener);
    }

    /**
     * Close the socket and wake the selector, effectively aborting the transfer
     */
    void stop() {
        mStop = true;
        mSelector.wakeup();
    }

    /**
     * Update the progress notification
     */
    private void updateProgress() {
        int newProgress = (int) (100.0 * (mTransferBytesTotal != 0 ?
                (double) mTransferBytesTransferred / (double) mTransferBytesTotal : 0.0));
        if (newProgress != mProgress) {
            synchronized (mSync) {
                mProgress = newProgress;
            }
            for (ProgressListener progressListener : mProgressListeners) {
                progressListener.onProgress();
            }
        }
    }

    /**
     * Process the transfer header
     * @throws IOException
     */
    private void processTransferHeader() throws IOException {
        TransferHeader transferHeader;
        try {
            transferHeader = mGson.fromJson(new String(
                    mReceivingPacket.getBuffer().array(), Charset.forName("UTF-8")),
                    TransferHeader.class);
        } catch (JsonSyntaxException e) {
            throw new IOException(e.getMessage());
        }
        synchronized (mSync) {
            mDeviceName = transferHeader.name;
        }
        mTransferItems = Integer.parseInt(transferHeader.count);
        mTransferBytesTotal = Long.parseLong(transferHeader.size);
        mInternalState = mItemIndex == mTransferItems ? InternalState.Finished : InternalState.ItemHeader;
        for (HeaderListener headerListener : mHeaderListeners) {
            headerListener.onHeader();
        }
    }

    // TODO: no error handling here :O

    /**
     * Process the header for an individual item
     * @throws IOException
     */
    private void processItemHeader() throws IOException {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> map = mGson.fromJson(new String(
                mReceivingPacket.getBuffer().array(), Charset.forName("UTF-8")), type);
        String itemType = (String) map.get(Item.TYPE);
        if (itemType == null) {
            itemType = FileItem.TYPE_NAME;
        }
        switch (itemType) {
            case FileItem.TYPE_NAME:
                mItem = new FileItem(mTransferDirectory, map, mOverwrite);
                break;
            default:
                throw new IOException("unrecognized item type");
        }
        long itemSize = mItem.getLongProperty(Item.SIZE, true);
        if (itemSize != 0) {
            mInternalState = InternalState.ItemContent;
            mItem.open(Item.Mode.Write);
            mItemBytesRemaining = itemSize;
        } else {
            mItemIndex += 1;
            mInternalState = mItemIndex == mTransferItems ? InternalState.Finished : InternalState.ItemHeader;
        }
    }

    /**
     * Process item contents
     * @throws IOException
     */
    private void processItemContent() throws IOException {
        mItem.write(mReceivingPacket.getBuffer().array());
        int numBytes = mReceivingPacket.getBuffer().capacity();
        mTransferBytesTransferred += numBytes;
        mItemBytesRemaining -= numBytes;
        updateProgress();
        if (mItemBytesRemaining <= 0) {
            mItem.close();
            for (ItemListener itemListener : mItemListeners) {
                itemListener.onItem(mItem);
            }
            mItemIndex += 1;
            mInternalState = mItemIndex == mTransferItems ? InternalState.Finished : InternalState.ItemHeader;
        }
    }

    /**
     * Process the next packet by reading it and then invoking the correct method
     * @return true if there are more packets expected
     * @throws IOException
     */
    private boolean processNextPacket() throws IOException {
        if (mReceivingPacket == null) {
            mReceivingPacket = new Packet();
        }
        mReceivingPacket.read(mSocketChannel);
        if (mReceivingPacket.isFull()) {
            if (mReceivingPacket.getType() == Packet.ERROR) {
                throw new IOException(new String(mReceivingPacket.getBuffer().array(),
                        Charset.forName("UTF-8")));
            }
            if (mDirection == Direction.Receive) {
                if (mInternalState == InternalState.TransferHeader && mReceivingPacket.getType() == Packet.JSON) {
                    processTransferHeader();
                } else if (mInternalState == InternalState.ItemHeader && mReceivingPacket.getType() == Packet.JSON) {
                    processItemHeader();
                } else if (mInternalState == InternalState.ItemContent && mReceivingPacket.getType() == Packet.BINARY) {
                    processItemContent();
                } else {
                    throw new IOException("unexpected packet");
                }
                mReceivingPacket = null;
                return mInternalState != InternalState.Finished;
            } else {
                if (mInternalState == InternalState.Finished && mReceivingPacket.getType() == Packet.SUCCESS) {
                    return false;
                } else {
                    throw new IOException("unexpected packet");
                }
            }
        }
        return true;
    }

    /**
     * Send the transfer header
     */
    private void sendTransferHeader() {
        Map<String, String> map = new HashMap<>();
        map.put("name", mDeviceName);
        map.put("count", Integer.toString(mBundle.size()));
        map.put("size", Long.toString(mBundle.getTotalSize()));
        mSendingPacket = new Packet(Packet.JSON, mGson.toJson(map).getBytes(
                Charset.forName("UTF-8")));
        mInternalState = mItemIndex == mTransferItems ? InternalState.Finished : InternalState.ItemHeader;
    }

    /**
     * Send the header for an individual item
     * @throws IOException
     */
    private void sendItemHeader() throws IOException {
        mItem = mBundle.get(mItemIndex);
        mSendingPacket = new Packet(Packet.JSON, mGson.toJson(
                mItem.getProperties()).getBytes(Charset.forName("UTF-8")));
        long itemSize = mItem.getLongProperty(Item.SIZE, true);
        if (itemSize != 0) {
            mInternalState = InternalState.ItemContent;
            mItem.open(Item.Mode.Read);
            mItemBytesRemaining = itemSize;
        } else {
            mItemIndex += 1;
            mInternalState = mItemIndex == mTransferItems ? InternalState.Finished : InternalState.ItemHeader;
        }
    }

    /**
     * Send item contents
     * @throws IOException
     */
    private void sendItemContent() throws IOException {
        byte buffer[] = new byte[CHUNK_SIZE];
        int numBytes = mItem.read(buffer);
        mSendingPacket = new Packet(Packet.BINARY, buffer, numBytes);
        mTransferBytesTransferred += numBytes;
        mItemBytesRemaining -= numBytes;
        updateProgress();
        if (mItemBytesRemaining <= 0) {
            mItem.close();
            mItemIndex += 1;
            mInternalState = mItemIndex == mTransferItems ? InternalState.Finished : InternalState.ItemHeader;
        }
    }

    /**
     * Send the next packet by evaluating the current state
     * @return true if there are more packets to send
     * @throws IOException
     */
    private boolean sendNextPacket() throws IOException {
        if (mSendingPacket == null) {
            if (mDirection == Direction.Receive) {
                mSendingPacket = new Packet(Packet.SUCCESS);
            } else {
                switch (mInternalState) {
                    case TransferHeader:
                        sendTransferHeader();
                        break;
                    case ItemHeader:
                        sendItemHeader();
                        break;
                    case ItemContent:
                        sendItemContent();
                        break;
                    default:
                        throw new IOException("unreachable code");
                }
            }
        }
        mSocketChannel.write(mSendingPacket.getBuffer());
        if (mSendingPacket.isFull()) {
            mSendingPacket = null;
            return mInternalState != InternalState.Finished;
        }
        return true;
    }

    /**
     * Perform the transfer until it completes or an error occurs
     */
    @Override
    public void run() {
        try {
            // Indicate which operations select() should select for
            SelectionKey selectionKey = mSocketChannel.register(
                    mSelector,
                    mDirection == Direction.Receive ?
                            SelectionKey.OP_READ :
                            SelectionKey.OP_CONNECT
            );

            // For a sending transfer, connect to the remote device
            if (mDirection == Direction.Send) {
                mSocketChannel.connect(new InetSocketAddress(mDevice.getHost(), mDevice.getPort()));
            }

            while (true) {
                mSelector.select();
                if (mStop) {
                    break;
                }
                if (selectionKey.isConnectable()) {
                    mSocketChannel.finishConnect();
                    selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);

                    synchronized (mSync) {
                        mState = State.Transferring;
                    }
                    for (ConnectListener connectListener : mConnectListeners) {
                        connectListener.onConnect();
                    }
                }
                if (selectionKey.isReadable()) {
                    if (!processNextPacket()) {
                        if (mDirection == Direction.Receive) {
                            selectionKey.interestOps(SelectionKey.OP_WRITE);
                        } else {
                            break;
                        }
                    }
                }
                if (selectionKey.isWritable()) {
                    if (!sendNextPacket()) {
                        if (mDirection == Direction.Receive) {
                            break;
                        } else {
                            selectionKey.interestOps(SelectionKey.OP_READ);
                        }
                    }
                }
            }

            // Close the socket
            mSocketChannel.close();

            // If interrupted, throw an error
            if (mStop) {
                throw new IOException("transfer was cancelled");
            }

            // Indicate success
            synchronized (mSync) {
                mState = State.Succeeded;
            }
            for (SuccessListener successListener : mSuccessListeners) {
                successListener.onSuccess();
            }

        } catch (IOException e) {
            synchronized (mSync) {
                mState = State.Failed;
                mError = e.getMessage();
            }
            for (ErrorListener errorListener : mErrorListeners) {
                errorListener.onError();
            }
        }

        // Indicate that *everything* has completed
        for (FinishListener finishListener : mFinishListeners) {
            finishListener.onFinish();
        }
    }
}
