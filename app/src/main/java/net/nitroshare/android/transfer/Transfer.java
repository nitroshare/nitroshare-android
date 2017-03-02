package net.nitroshare.android.transfer;

import com.google.gson.Gson;
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
        void onConnected();
        void onDeviceName();
        void onProgress(int progress);
        void onSuccess();
        void onError(String message);
        void onFinish();
    }

    // Direction of transfer relative to the current device
    private enum Direction {
        Receive,
        Send,
    }

    // State of the transfer
    private enum State {
        TransferHeader,
        ItemHeader,
        ItemContent,
        Finished,
    }

    private SocketChannel mSocketChannel;

    private Device mDevice;
    private String mDeviceName;
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
     * @param defaultDeviceName default device name
     * @param listener listener for transfer events
     */
    public Transfer(SocketChannel socketChannel, String defaultDeviceName, Listener listener) {
        mSocketChannel = socketChannel;
        mDeviceName = defaultDeviceName;
        mListener = listener;
        mDirection = Direction.Receive;
    }

    /**
     * Create a transfer for sending items
     * @param device device to connect to
     * @param deviceName device name to send to the remote device
     * @param bundle bundle to transfer
     * @param listener listener for transfer events
     */
    public Transfer(Device device, String deviceName, Bundle bundle, Listener listener) {
        mDevice = device;
        mDeviceName = deviceName;
        mBundle = bundle;
        mListener = listener;
        mDirection = Direction.Send;
        mTransferItems = mBundle.size();
        mTransferBytesTotal = mBundle.getTotalSize();
    }

    /**
     * Retrieve the name of the remote device
     * @return remote device name
     */
    public String getRemoteDeviceName() {
        return mDirection == Direction.Receive ? mDeviceName : mDevice.getName();
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
     */
    private void processTransferHeader() {
        @SuppressWarnings("unused")
        class TransferHeader {
            private String name;
            private String count;
            private String size;
        }
        TransferHeader transferHeader = mGson.fromJson(new String(
                mReceivingPacket.getBuffer().array(), StandardCharsets.UTF_8),
                TransferHeader.class);
        mState = mItemIndex == mTransferItems ? State.Finished : State.ItemHeader;
        mDeviceName = transferHeader.name;
        mTransferItems = Integer.parseInt(transferHeader.count);
        mTransferBytesTotal = Long.parseLong(transferHeader.size);
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
                mItem = new FileItem(map);
                break;
            default:
                throw new IOException("unrecognized item type");
        }
        if (mItem.getSize() != 0) {
            mState = State.ItemContent;
            mItem.open(Item.Mode.Write);
            mItemBytesRemaining = mItem.getSize();
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
        if (mItem.getSize() != 0) {
            mState = State.ItemContent;
            mItem.open(Item.Mode.Read);
            mItemBytesRemaining = mItem.getSize();
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

            // For a sending transfer, connect to the remote device first
            if (mDirection == Direction.Send) {
                mSocketChannel = SocketChannel.open();
                mSocketChannel.connect(new InetSocketAddress(mDevice.getHost(), mDevice.getPort()));
                mListener.onConnected();
            }

            // Ensure that all operations are non-blocking
            mSocketChannel.configureBlocking(false);

            // Indicate which operations select() should select for
            Selector selector = Selector.open();
            SelectionKey selectionKey = mSocketChannel.register(selector,
                    mDirection == Direction.Receive ? SelectionKey.OP_READ :
                            SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            // Continue to process events from the socket until terminated
            while (true) {
                selector.select();
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

            // Close the socket and indicate success
            mSocketChannel.close();
            mListener.onSuccess();

        } catch (IOException e) {
            mListener.onError(e.getMessage());
        }

        mListener.onFinish();
    }
}
