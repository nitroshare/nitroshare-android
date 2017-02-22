package net.nitroshare.android.bundle;

/**
 * Individual item for transfer
 *
 * Every individual file, URL, etc. for transfer must be an instance of an
 * Item-derived class. Items can have any number of properties, but they must
 * implement TYPE, NAME, and SIZE at a minimum.
 *
 * If items contain content (SIZE is nonzero), the I/O functions are used to
 * read and write the contents.
 */
abstract public class Item {

    /**
     * Unique identifier for the type of item
     */
    public static final String TYPE = "type";

    /**
     * Name of the item
     *
     * This value is displayed in some clients during transfer. Files, for
     * example, also use this property for the relative filename.
     */
    public static final String NAME = "name";

    /**
     * Size of the item content during transmission
     *
     * This number is sent over-the-wire as a string to avoid problems with
     * large integers in JSON. This number can be zero if there is no payload.
     */
    public static final String SIZE = "size";

    /**
     * Retrieve the value for the specified key
     * @param key name of property
     * @return value for the property
     */
    abstract public Object getProperty(String key);

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
     * @return true if the item was opened
     */
    abstract public boolean open(Mode mode);

    /**
     * Read data from the item
     * @return array of bytes from the item
     *
     * This method is invoked multiple times until all content has been read.
     * Avoid returning very large chunks of data since this causes excess
     * memory usage.
     */
    abstract public byte[] read();

    /**
     * Write data to the item
     * @param data array of bytes to write
     */
    abstract public void write(byte[] data);

    /**
     * Close the item
     */
    abstract public void close();
}
