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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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
     * Receive notification for transfer events
     */
    interface Listener {
        void onConnect();
        void onDeviceName();
        void onProgress(int progress);
        void onSuccess();
        void onError(String message);
        void onFinish();
    }

    /**
     * Direction of transfer relative to the current device
     */
    enum Direction {
        Receive,
        Send,
    }

    /**
     * Transfer header
     */
    private class TransferHeader {
        String name;
        String count;
        String size;
    }

    // State of the transfer
    private enum State {
        TransferHeader,
        ItemHeader,
        ItemContent,
        Finished,
    }

    private volatile boolean mStop = false;
    private Selector mSelector = Selector.open();
    private SocketChannel mSocketChannel;

    private Device mDevice;
    private String mDeviceName;
    private String mTransferDirectory;
    private Bundle mBundle;
    private Listener mListener;

    private Direction mDirection;
    private State mState = State.TransferHeader;

    private Packet mReceivingPacket;
    private Packet mSendingPacket;

    private int mTransferItems;
    private long mTransferBytesTotal;
    private long mTransferBytesTransferred;

    private Item mItem;
    private int mItemIndex;
    private long mItemBytesRemaining;

    private int mProgress;

    /**
     * Create a transfer for receiving items
     * @param socketChannel incoming channel
     * @param transferDirectory directory for incoming files
     * @param unknownDeviceName device name shown before being received
     * @throws IOException
     */
    public Transfer(SocketChannel socketChannel, String transferDirectory, String unknownDeviceName) throws IOException {
        mSocketChannel = socketChannel;
        mSocketChannel.configureBlocking(false);
        mDeviceName = unknownDeviceName;
        mTransferDirectory = transferDirectory;
        mDirection = Direction.Receive;
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
        mTransferItems = mBundle.size();
        mTransferBytesTotal = mBundle.getTotalSize();
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
        return mDirection == Direction.Receive ? mDeviceName : mDevice.getName();
    }

    /**
     * Specify the listener that will receive transfer events
     * @param listener listener for transfer events
     */
    void setListener(Listener listener) {
        mListener = listener;
    }

    /**
     * Close the socket, effectively aborting the transfer
     */
    void stop() {
        mStop = true;
        mSelector.wakeup();
    }

    /**
     * Update the progress notification
     */
    private void updateProgress() {
        int oldProgress = mProgress;
        mProgress = (int) (100.0 * (mTransferBytesTotal != 0 ?
                (double) mTransferBytesTransferred / (double) mTransferBytesTotal : 0.0));
        if (mProgress != oldProgress) {
            mListener.onProgress(mProgress);
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
                    mReceivingPacket.getBuffer().array(), StandardCharsets.UTF_8),
                    TransferHeader.class);
        } catch (JsonSyntaxException e) {
            throw new IOException(e.getMessage());
        }
        mDeviceName = transferHeader.name;
        mTransferItems = Integer.parseInt(transferHeader.count);
        mTransferBytesTotal = Long.parseLong(transferHeader.size);
        mState = mItemIndex == mTransferItems ? State.Finished : State.ItemHeader;
        mListener.onDeviceName();
    }

    /**
     * Process the header for an individual item
     * @throws IOException
     */
    private void processItemHeader() throws IOException {
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> map = mGson.fromJson(new String(
                mReceivingPacket.getBuffer().array(), StandardCharsets.UTF_8), type);
        switch ((String) map.get(Item.TYPE)) {
            case FileItem.TYPE_NAME:
                mItem = new FileItem(mTransferDirectory, map);
                break;
            default:
                throw new IOException("unrecognized item type");
        }
        long itemSize = mItem.getLongProperty(Item.SIZE);
        if (itemSize != 0) {
            mState = State.ItemContent;
            mItem.open(Item.Mode.Write);
            mItemBytesRemaining = itemSize;
        } else {
            mItemIndex += 1;
            mState = mItemIndex == mTransferItems ? State.Finished : State.ItemHeader;
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
            mItemIndex += 1;
            mState = mItemIndex == mTransferItems ? State.Finished : State.ItemHeader;
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
                        StandardCharsets.UTF_8));
            }
            if (mDirection == Direction.Receive) {
                if (mState == State.TransferHeader && mReceivingPacket.getType() == Packet.JSON) {
                    processTransferHeader();
                } else if (mState == State.ItemHeader && mReceivingPacket.getType() == Packet.JSON) {
                    processItemHeader();
                } else if (mState == State.ItemContent && mReceivingPacket.getType() == Packet.BINARY) {
                    processItemContent();
                } else {
                    throw new IOException("unexpected packet");
                }
            } else {
                if (mState == State.Finished && mReceivingPacket.getType() == Packet.SUCCESS) {
                    return false;
                } else {
                    throw new IOException("unexpected packet");
                }
            }
            mReceivingPacket = null;
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
                StandardCharsets.UTF_8));
        mState = mItemIndex == mTransferItems ? State.Finished : State.ItemHeader;
    }

    /**
     * Send the header for an individual item
     * @throws IOException
     */
    private void sendItemHeader() throws IOException {
        mItem = mBundle.get(mItemIndex);
        mSendingPacket = new Packet(Packet.JSON, mGson.toJson(
                mItem.getProperties()).getBytes(StandardCharsets.UTF_8));
        long itemSize = mItem.getLongProperty(Item.SIZE);
        if (itemSize != 0) {
            mState = State.ItemContent;
            mItem.open(Item.Mode.Read);
            mItemBytesRemaining = itemSize;
        } else {
            mItemIndex += 1;
            mState = mItemIndex == mTransferItems ? State.Finished : State.ItemHeader;
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
            mState = mItemIndex == mTransferItems ? State.Finished : State.ItemHeader;
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
                switch (mState) {
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
            return mState != State.Finished;
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
                            SelectionKey.OP_CONNECT | SelectionKey.OP_READ |
                                    SelectionKey.OP_WRITE
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
                    mListener.onConnect();
                    selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
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
            mListener.onSuccess();

        } catch (IOException e) {
            mListener.onError(e.getMessage());
        }

        mListener.onFinish();
    }
}
