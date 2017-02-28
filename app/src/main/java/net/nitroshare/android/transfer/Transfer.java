package net.nitroshare.android.transfer;

import android.util.Log;

import com.google.gson.Gson;

import net.nitroshare.android.bundle.Bundle;
import net.nitroshare.android.bundle.Item;
import net.nitroshare.android.discovery.Device;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Perform a transfer from one device to another
 *
 * This class takes care of communicating (via socket) with another device to
 * transfer a bundle (list of items) using packets.
 */
public class Transfer implements Runnable {

    private static final String TAG = "Transfer";

    private static final int CHUNK_SIZE = 65536;

    // Direction of transfer relative to the current device
    private enum Direction {
        Send,
    }

    // State of the transfer
    private enum State {
        TransferHeader,
        ItemHeader,
        ItemContent,
        Finished,
    }

    /**
     * Receive notification for events that occur during transfer
     */
    interface Listener {

        /**
         * Name of remote device is provided
         *
         * @param name device name
         */
        void onDeviceName(String name);

        /**
         * Progress of transfer has changed
         *
         * @param progress number between 0 and 100 inclusive
         */
        void onProgress(int progress);

        /**
         * Transfer completed successfully
         */
        void onSuccess();

        /**
         * Error has occurred during transfer
         *
         * @param message error message
         */
        void onError(String message);

        /**
         * Transfer has completed
         */
        void onFinish();
    }

    private final Gson mGson = new Gson();

    private Direction mDirection;
    private State mState;

    private Device mDevice;
    private Bundle mBundle;
    private Listener mListener;

    private SocketChannel mSocketChannel;

    private Iterator<Item> mIterator;
    private Item mCurrentItem;

    private Packet mReceivingPacket;
    private Packet mSendingPacket;

    private long mCurrentItemBytesRemaining;
    private long mTotalBytesTransferred = 0;

    /**
     * Send the specified bundle to the specified device
     */
    public Transfer(Device device, Bundle bundle, Listener listener) {
        mDirection = Direction.Send;
        mState = State.TransferHeader;
        mDevice = device;
        mBundle = bundle;
        mListener = listener;
        mIterator = bundle.iterator();
        mTotalBytesTransferred = bundle.getTotalSize();
    }

    /**
     * Update the current transfer progress
     */
    private void updateProgress() {
        mListener.onProgress((int) (100.0 * (mBundle.getTotalSize() != 0 ?
                (double) mTotalBytesTransferred / (double) mBundle.getTotalSize() : 0.0)));
    }

    // TODO: use actual device name in transfer header

    /**
     * Sent the transfer header
     */
    private void sendTransferHeader() {
        Log.d(TAG, "writing transfer header");
        Map<String, String> map = new HashMap<>();
        map.put("name", "Android");
        map.put("count", Integer.toString(mBundle.size()));
        map.put("size", Long.toString(mBundle.getTotalSize()));
        mSendingPacket = new Packet(Packet.JSON, mGson.toJson(map).getBytes(StandardCharsets.UTF_8));
        mState = mIterator.hasNext() ? State.ItemHeader : State.Finished;
    }

    /**
     * Send the header for the next item
     *
     * @throws IOException
     */
    private void sendItemHeader() throws IOException {
        Log.d(TAG, "writing item header");
        mCurrentItem = mIterator.next();
        mSendingPacket = new Packet(Packet.JSON, mGson.toJson(
                mCurrentItem.getProperties()).getBytes(StandardCharsets.UTF_8));
        if (mCurrentItem.getSize() != 0) {
            mState = State.ItemContent;
            mCurrentItem.open(Item.Mode.Read);
            mCurrentItemBytesRemaining = mCurrentItem.getSize();
        } else {
            mState = mIterator.hasNext() ? State.ItemHeader : State.Finished;
        }
    }

    /**
     * Send data from the current item
     *
     * @throws IOException
     */
    private void sendItemContent() throws IOException {
        Log.d(TAG, "writing item content");
        byte buffer[] = new byte[CHUNK_SIZE];
        int numBytes = mCurrentItem.read(buffer);
        mSendingPacket = new Packet(Packet.BINARY, buffer, numBytes);
        mCurrentItemBytesRemaining -= numBytes;
        mTotalBytesTransferred += numBytes;
        updateProgress();
        if (mCurrentItemBytesRemaining <= 0) {
            mCurrentItem.close();
            mState = mIterator.hasNext() ? State.ItemHeader : State.Finished;
        }
    }

    /**
     * Send the next packet to the remote device (send only)
     *
     * @return false is there are no more packets to write
     * @throws IOException
     */
    private boolean sendNext() throws IOException {
        if (mSendingPacket == null) {
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
                case Finished:
                    return false;
            }
        }
        mSocketChannel.write(mSendingPacket.getBuffer());
        if (mSendingPacket.isFull()) {
            mSendingPacket = null;
        }
        return true;
    }

    private void processTransferHeader() {
        //...
    }

    private void processItemHeader() {
        //...
    }

    private void processItemContent() {
        //...
    }

    /**
     * Process the next packet
     *
     * @return false is there are no more packets to read
     * @throws IOException
     */
    private boolean processNext() throws IOException {
        if (mReceivingPacket == null) {
            mReceivingPacket = new Packet();
        }
        mReceivingPacket.read(mSocketChannel);
        if (mReceivingPacket.isFull()) {
            if (mReceivingPacket.getType() == Packet.ERROR) {
                throw new IOException(new String(mReceivingPacket.getBuffer().array(),
                        StandardCharsets.UTF_8));
            }
            switch (mDirection) {
                case Send:
                    switch (mReceivingPacket.getType()) {
                        case Packet.SUCCESS:
                            return false;
                        default:
                            throw new IOException("unexpected packet");
                    }
            }
            mReceivingPacket = null;
        }
        return true;
    }

    /**
     * Run the transfer
     */
    @Override
    public void run() {
        try {

            // For a sending transfer, connect to the remote device first
            if (mDirection == Direction.Send) {
                mSocketChannel = SocketChannel.open();
                mSocketChannel.connect(new InetSocketAddress(mDevice.getHost(), mDevice.getPort()));
            }

            // Ensure that all operations are non-blocking
            mSocketChannel.configureBlocking(false);

            // We want notifications for reads and writes
            Selector selector = Selector.open();
            SelectionKey selectionKey = mSocketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            while (true) {
                selector.select();
                if (selectionKey.isReadable()) {

                    // If no more data can be read, exit the loop
                    if (!processNext()) {
                        break;
                    }
                }
                if (selectionKey.isWritable()) {

                    // If no more data will be written, remove it from select()
                    if (!sendNext()) {
                        selectionKey.interestOps(SelectionKey.OP_READ);
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
