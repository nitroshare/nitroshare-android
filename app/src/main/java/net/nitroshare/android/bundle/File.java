package net.nitroshare.android.bundle;

/**
 * An individual file for transfer
 */
public class File extends Item {

    // Additional properties for files
    public static final String READ_ONLY = "read_only";
    public static final String EXECUTABLE = "executable";
    public static final String CREATED = "created";
    public static final String LAST_READ = "last_read";
    public static final String LAST_MODIFIED = "last_modified";

    private String mRelativeFilename;
    private long mSize;

    private boolean mReadOnly;
    private boolean mExecutable;

    private long mCreated;
    private long mLastRead;
    private long mLastModified;

    /**
     * Create a new file item from the specified filename
     * @param filename
     */
    public File(String filename) {
        //...
    }

    @Override
    public Object getProperty(String key) {
        switch (key) {
            case TYPE:
                return "file";
            case NAME:
                return mRelativeFilename;
            case SIZE:
                return Long.toString(mSize);
            case READ_ONLY:
                return mReadOnly;
            case EXECUTABLE:
                return mExecutable;
            case CREATED:
                return mCreated;
            case LAST_READ:
                return mLastRead;
            case LAST_MODIFIED:
                return mLastModified;
            default:
                return null;
        }
    }

    @Override
    public boolean open(Mode mode) {
        return false;
    }

    @Override
    public byte[] read() {
        return new byte[0];
    }

    @Override
    public void write(byte[] data) {

    }

    @Override
    public void close() {

    }
}
