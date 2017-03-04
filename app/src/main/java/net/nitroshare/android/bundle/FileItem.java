package net.nitroshare.android.bundle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * An individual file for transfer
 *
 * Note that Android's Java doesn't include java.nio.file so only the
 * last_modified property is usable on the platform.
 */
public class FileItem extends Item {

    public static final String TYPE_NAME = "file";

    // Additional properties for files
    private static final String READ_ONLY = "read_only";
    private static final String EXECUTABLE = "executable";
    private static final String LAST_MODIFIED = "last_modified";

    private File mFile;
    private Map<String, Object> mProperties;

    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;

    /**
     * Create a new file item using the provided properties
     * @param transferDirectory directory for the file
     * @param properties map of properties for the file
     * @throws IOException
     */
    public FileItem(String transferDirectory, Map<String, Object> properties) throws IOException {
        mProperties = properties;
        mFile = new File(new File(transferDirectory), getStringProperty(NAME));
    }

    /**
     * Create a new file item from the specified file
     */
    public FileItem(File file) {
        this(file, file.getName());
    }

    /**
     * Create a new file item with the specified filename
     */
    public FileItem(File file, String filename) {
        mFile = file;
        mProperties = new HashMap<>();
        mProperties.put(TYPE, TYPE_NAME);
        mProperties.put(NAME, filename);
        mProperties.put(SIZE, Long.toString(mFile.length()));
        mProperties.put(READ_ONLY, !mFile.canWrite());
        mProperties.put(EXECUTABLE, mFile.canExecute());
        mProperties.put(LAST_MODIFIED, Long.toString(mFile.lastModified()));

        // TODO: these are used for temporary compatibility with 0.3.x
        mProperties.put("created", 0);
        mProperties.put("last_read", 0);
        mProperties.put("directory", false);
    }

    @Override
    public Map<String, Object> getProperties() {
        return mProperties;
    }

    @Override
    public void open(Mode mode) throws IOException {
        switch (mode) {
            case Read:
                mInputStream = new FileInputStream(mFile);
                break;
            case Write:
                if (!mFile.getParentFile().mkdirs()) {
                    throw new IOException("unable to create parent directory");
                }
                mOutputStream = new FileOutputStream(mFile);
                break;
        }
    }

    @Override
    public int read(byte[] data) throws IOException {
        int numBytes = mInputStream.read(data);
        if (numBytes == -1) {
            numBytes = 0;
        }
        return numBytes;
    }

    @Override
    public void write(byte[] data) throws IOException {
        mOutputStream.write(data);
    }

    @Override
    public void close() throws IOException {
        if (mInputStream != null) {
            mInputStream.close();
        }
        if (mOutputStream != null) {
            mOutputStream.close();
            //noinspection ResultOfMethodCallIgnored
            mFile.setWritable(getBooleanProperty(READ_ONLY));
            //noinspection ResultOfMethodCallIgnored
            mFile.setExecutable(getBooleanProperty(EXECUTABLE));
            //noinspection ResultOfMethodCallIgnored
            mFile.setLastModified(getLongProperty(LAST_MODIFIED));
        }
    }
}
