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
    static final int SUCCESS = 0;

    /**
     * Transfer failed
     *
     * This packet is sent by either end to indicate an error. The content of
     * packet describes the error.
     */
    static final int ERROR = 1;

    /**
     * JSON data
     */
    static final int JSON = 2;

    /**
     * Binary data
     */
    static final int BINARY = 3;

    private int mType;
    private ByteBuffer mBuffer;
    private boolean mHaveSize = false;

    /**
     * Retrieve the packet type
     * @return packet type
     */
    int getType() {
        return mType;
    }

    /**
     * Retrieve the buffer for the packet
     * @return byte array
     */
    ByteBuffer getBuffer() {
        return mBuffer;
    }

    /**
     * Determine if the buffer is full
     * @return true if full
     */
    boolean isFull() {
        return mBuffer.position() == mBuffer.capacity();
    }

    /**
     * Create an empty packet
     */
    Packet() {
        mBuffer = ByteBuffer.allocate(5);
        mBuffer.order(ByteOrder.LITTLE_ENDIAN);
    }

    Packet(int type) {
        this(type, null);
    }

    /**
     * Create a packet of the specified type with the specified data
     */
    Packet(int type, byte[] data) {
        this(type, data, data == null ? 0 : data.length);
    }

    Packet(int type, byte[] data, int length) {
        mBuffer = ByteBuffer.allocate(5 + length);
        mBuffer.order(ByteOrder.LITTLE_ENDIAN);
        mBuffer.putInt(length + 1).put((byte) type);
        if (data != null) {
            mBuffer.put(data, 0, length);
        }
        mBuffer.flip();
    }

    /**
     * Read a packet from a socket channel
     */
    void read(SocketChannel socketChannel) throws IOException {

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
