package net.nitroshare.android.transfer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;

/**
 * Individual packet of information in a transfer
 *
 * Transfers are (at a high level) essentially a stream of packets being
 * exchanged back and forth. The packet format is described here:
 * https://goo.gl/fL890p
 */
class Packet {

    /**
     * Transfer succeeded
     *
     * This packet is sent by the receiver to indicate the transfer succeeded
     * and the connection may be closed.
     */
    public static final int SUCCESS = 0;

    /**
     * Transfer failed
     *
     * This packet is sent by either end to indicate an error. The content of
     * packet describes the error.
     */
    public static final int ERROR = 1;

    /**
     * JSON data
     */
    public static final int JSON = 2;

    /**
     * Binary data
     */
    public static final int BINARY = 3;

    private int mType;
    private ByteBuffer mBuffer;
    private boolean mHaveSize = false;

    /**
     * Retrieve the packet type
     * @return packet type
     */
    public int getType() {
        return mType;
    }

    /**
     * Retrieve the buffer for the packet
     * @return byte array
     */
    public ByteBuffer getBuffer() {
        return mBuffer;
    }

    /**
     * Determine if the buffer is full
     * @return true if full
     */
    public boolean isFull() {
        return mBuffer.position() == mBuffer.capacity();
    }

    /**
     * Create an empty packet of the specified type
     * @param type
     */
    public Packet(int type) {
        mType = type;
        mBuffer = ByteBuffer.allocate(5);
        mBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * Create a packet of the specified type with the specified data
     */
    public Packet(int type, byte[] data) {
        mBuffer = ByteBuffer.allocate(5 + data.length);
        mBuffer.order(ByteOrder.LITTLE_ENDIAN);
        mBuffer.putInt(data.length + 1).put((byte) mType).put(data);
    }

    /**
     * Read a packet from a socket channel
     * @param socketChannel
     */
    public void read(SocketChannel socketChannel) throws IOException {

        // If the 32-bit size hasn't yet been read, do so
        if (!mHaveSize) {
            socketChannel.read(mBuffer);
            if (mBuffer.position() != 5) {
                return;
            }

            // Remaining data is 8-bit type and data
            mBuffer.flip();
            int size = mBuffer.getInt() - 1;
            mType = mBuffer.get();
            mBuffer = ByteBuffer.allocate(size);
            mBuffer.order(ByteOrder.LITTLE_ENDIAN);
            mHaveSize = true;
        }

        // The size is known, read data into the buffer
        socketChannel.read(mBuffer);
    }
}
