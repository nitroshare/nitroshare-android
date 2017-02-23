package net.nitroshare.android.bundle;

import java.io.IOException;
import java.util.Map;

/**
 * Individual item for transfer
 *
 * Every individual file, URL, etc. for transfer must be an instance of a
 * class that implements Item. Items can have any number of properties, but
 * they must implement TYPE, NAME, and SIZE at a minimum.
 *
 * If items contain content (SIZE is nonzero), the I/O functions are used to
 * read and write the contents.
 */
abstract public class Item {

    /**
     * Unique identifier for the type of item
     */
    static final String TYPE = "type";

    /**
     * Name of the item
     *
     * This value is displayed in some clients during transfer. Files, for
     * example, also use this property for the relative filename.
     */
    static final String NAME = "name";

    /**
     * Size of the item content during transmission
     *
     * This number is sent over-the-wire as a string to avoid problems with
     * large integers in JSON. This number can be zero if there is no payload.
     */
    static final String SIZE = "size";

    /**
     * Retrieve a map of properties
     * @return property map
     */
    abstract public Map<String, Object> getProperties();

    /**
     * Retrieve the unique type identifier for the item
     */
    public String getType() {
        return (String) getProperties().get(TYPE);
    }

    /**
     * Retrieve the name of the item
     */
    public String getName() {
        return (String) getProperties().get(NAME);
    }

    /**
     * Retrieve the size of the item's content in bytes
     */
    public long getSize() {
        return Long.parseLong((String) getProperties().get(SIZE));
    }

    /**
     * Mode for opening items
     */
    public enum Mode {
        Read,
        Write,
    }

    /**
     * Open the item for reading or writing
     * @param mode open mode
     * @throws IOException
     */
    abstract public void open(Mode mode) throws IOException;

    /**
     * Read data from the item
     * @param data array of bytes to read
     * @return number of bytes read or -1 if EOF
     * @throws IOException
     *
     * This method is invoked multiple times until all content has been read.
     */
    abstract public int read(byte[] data) throws IOException;

    /**
     * Write data to the item
     * @param data array of bytes to write
     * @throws IOException
     */
    abstract public void write(byte[] data) throws IOException;

    /**
     * Close the item
     * @throws IOException
     */
    abstract public void close() throws IOException;
}
