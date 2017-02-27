package net.nitroshare.android.transfer;

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
         * @param name device name
         */
        void onDeviceName(String name);

        /**
         * Progress of transfer has changed
         * @param progress number between 0 and 100 inclusive
         */
        void onProgress(int progress);

        /**
         * Transfer completed successfully
         */
        void onSuccess();

        /**
         * Error has occurred during transfer
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

    private Packet mPacket;

    private long mBytesTransferred = 0;
    private long mBytesTotal;
    private long mCurrentItemBytesTransferred;
    private long mCurrentItemBytesTotal;

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
        mBytesTotal = bundle.getTotalSize();
    }

    /**
     Attempt to read the next packet
     * @return new packet or null if none was read
     * @throws IOException
     */
    private Packet readNextPacket() throws IOException {
        Packet packet = null;
        if (mPacket == null) {
            mPacket = new Packet();
        }
        mPacket.read(mSocketChannel);
        if (mPacket.isFull()) {
            packet = mPacket;
            mPacket = null;
        }
        return packet;
    }

    /**
     * Write the transfer header
     * @throws IOException
     */
    private void writeTransferHeader() throws IOException {
        Map<String, String> map = new HashMap<>();
        map.put("name", "Android");
        map.put("count", Integer.toString(mBundle.size()));
        map.put("size", Long.toString(mBundle.getTotalSize()));
        Packet packet = new Packet(Packet.JSON, mGson.toJson(map).getBytes(StandardCharsets.UTF_8));
        mSocketChannel.write(packet.getBuffer());
        mState = State.ItemHeader;
    }

    /**
     * Write the header for an item
     * @throws IOException
     */
    private void writeItemHeader() throws IOException {
        mCurrentItem = mIterator.next();
        Packet packet = new Packet(Packet.JSON, mGson.toJson(
                mCurrentItem.getProperties()).getBytes(StandardCharsets.UTF_8));
        mSocketChannel.write(packet.getBuffer());
        mCurrentItemBytesTotal = mCurrentItem.getSize();
        if (mCurrentItemBytesTotal != 0) {
            mState = State.ItemContent;
            mCurrentItemBytesTransferred = 0;
            mCurrentItem.open(Item.Mode.Read);
        } else if(!mIterator.hasNext()) {
            mState = State.Finished;
        }
    }

    /**
     * Write item content
     * @throws IOException
     */
    private void writeItemContent() throws IOException {
        byte buffer[] = new byte[CHUNK_SIZE];
        int numBytes = mCurrentItem.read(buffer);
        Packet packet = new Packet(Packet.BINARY, buffer, numBytes);
        mSocketChannel.write(packet.getBuffer());
        mCurrentItemBytesTransferred += numBytes;
        mBytesTransferred += numBytes;
        updateProgress();
        if (mCurrentItemBytesTransferred == mCurrentItemBytesTotal) {
            mCurrentItem.close();
            mState = mIterator.hasNext() ? State.ItemHeader : State.Finished;
        }
    }

    /**
     * Provide a progress update for the transfer
     */
    private void updateProgress() {
        mListener.onProgress((int) (100.0 * (mBytesTotal != 0 ?
                (double) mBytesTransferred / (double) mBytesTotal : 0.0)));
    }

    @Override
    public void run() {
        try {
            mSocketChannel = SocketChannel.open();
            mSocketChannel.connect(new InetSocketAddress(mDevice.getHost(), mDevice.getPort()));

            // Switch the channel into non-blocking mode so that reads and
            // writes can be done asynchronously
            mSocketChannel.configureBlocking(false);
            Selector selector = Selector.open();
            SelectionKey selectionKey = mSocketChannel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            // Wait for data to be read or written
            while (true) {
                selector.select();
                if (selectionKey.isReadable()) {
                    Packet packet = readNextPacket();
                    if (packet != null) {
                        switch (packet.getType()) {
                            case Packet.ERROR:
                                throw new IOException(new String(packet.getBuffer().array(), StandardCharsets.UTF_8));
                            case Packet.SUCCESS:
                                if (mState == State.Finished) {
                                    break;
                                }
                            default:
                                throw new IOException("unexpected packet");
                        }
                        break;
                    }
                }
                if (selectionKey.isWritable()) {
                    switch (mState) {
                        case TransferHeader:
                            writeTransferHeader();
                            break;
                        case ItemHeader:
                            writeItemHeader();
                            break;
                        case ItemContent:
                            writeItemContent();
                            break;
                        default:
                            // Avoid a spin loop
                            selectionKey.interestOps(SelectionKey.OP_READ);
                            break;
                    }
                }
            }

            // Close the socket
            mSocketChannel.close();

            mListener.onSuccess();

        } catch (IOException e) {
            mListener.onError(e.getMessage());
        }

        mListener.onFinish();
    }
}
