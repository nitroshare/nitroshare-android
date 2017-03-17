package net.nitroshare.android.transfer;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

import net.nitroshare.android.R;
import net.nitroshare.android.bundle.Bundle;
import net.nitroshare.android.bundle.FileItem;
import net.nitroshare.android.discovery.Device;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Stack;

/**
 * Receive incoming transfers and initiate outgoing transfers
 *
 * This service listens for new connections and instantiates Transfer instances
 * to process them. It will also initiate a transfer when the appropriate
 * intent is supplied.
 */
public class TransferService extends Service {

    private static final String TAG = "TransferService";

    public static final String ACTION_START_LISTENING = "start_listening";
    public static final String ACTION_STOP_LISTENING = "stop_listening";

    public static final String ACTION_START_TRANSFER = "start_transfer";
    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_URIS = "urls";

    public static final String ACTION_STOP_TRANSFER = "stop_transfer";
    public static final String EXTRA_TRANSFER = "transfer";

    /**
     * Start or stop the service
     * @param context context to use for sending the intent
     * @param start true to start the service; false to stop it
     */
    public static void startStopService(Context context, boolean start) {
        Intent intent = new Intent(context, TransferService.class);
        if (start) {
            Log.i(TAG, "sending intent to start service");
            intent.setAction(ACTION_START_LISTENING);
        } else {
            Log.i(TAG, "sending intent to stop service");
            intent.setAction(ACTION_STOP_LISTENING);
        }
        context.startService(intent);
    }

    private TransferNotificationManager mTransferNotificationManager;
    private TransferServer mTransferServer;
    private SharedPreferences mSharedPreferences;

    @Override
    public void onCreate() {
        super.onCreate();
        mTransferNotificationManager = new TransferNotificationManager(this);
        try {
            mTransferServer = new TransferServer(this, mTransferNotificationManager);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
    }

    /**
     * Start the transfer server
     */
    private int startListening() {
        mTransferServer.start();
        return START_REDELIVER_INTENT;
    }

    /**
     * Stop the transfer server
     */
    private int stopListening() {
        mTransferServer.stop();
        mTransferNotificationManager.stop();
        return START_NOT_STICKY;
    }

    /**
     * Attempt to resolve the provided URI
     * @param uri URI to resolve
     * @return file descriptor
     * @throws IOException
     */
    private AssetFileDescriptor getAssetFileDescriptor(Uri uri) throws IOException {
        AssetFileDescriptor assetFileDescriptor;
        try {
            assetFileDescriptor = getContentResolver().openAssetFileDescriptor(uri, "r");
        } catch (FileNotFoundException e) {
            throw new IOException(String.format("unable to resolve \"%s\"", uri.toString()));
        }
        if (assetFileDescriptor == null) {
            throw new IOException(String.format("no file descriptor for \"%s\"", uri.toString()));
        }
        return assetFileDescriptor;
    }

    /**
     * Determine the appropriate filename for a URI
     * @param uri URI to use for filename
     * @return filename
     */
    private String getFilename(Uri uri) {
        String filename = uri.getLastPathSegment();
        String[] projection = {
                MediaStore.MediaColumns.DISPLAY_NAME,
        };
        Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            try {
                String columnValue = cursor.getString(cursor.getColumnIndexOrThrow(
                        MediaStore.MediaColumns.DISPLAY_NAME));
                if (columnValue != null) {
                    filename = new File(columnValue).getName();
                }
            } catch (IllegalArgumentException ignored) {
            }
            cursor.close();
        }
        return filename;
    }

    /**
     * Traverse a directory tree and add all files to the bundle
     * @param root the directory to which all filenames will be relative
     * @param bundle target for all files that are found
     * @throws IOException
     */
    private void traverseDirectory(File root, Bundle bundle) throws IOException {
        Stack<File> stack = new Stack<>();
        stack.push(root);
        while (!stack.empty()) {
            File topOfStack = stack.pop();
            for (File f : topOfStack.listFiles()) {
                if (f.isDirectory()) {
                    stack.push(f);
                } else {
                    String relativeFilename = f.getAbsolutePath().substring(
                            root.getParentFile().getAbsolutePath().length() + 1);
                    bundle.addItem(new FileItem(f, relativeFilename));
                }
            }
        }
    }

    /**
     * Create a bundle from the list of URIs
     * @param uriList list of URIs to add
     * @return newly created bundle
     * @throws IOException
     */
    private Bundle createBundle(ArrayList<Parcelable> uriList) throws IOException {
        Bundle bundle = new Bundle();
        for (Parcelable parcelable : uriList) {
            Uri uri = (Uri) parcelable;
            switch (uri.getScheme()) {
                case ContentResolver.SCHEME_ANDROID_RESOURCE:
                case ContentResolver.SCHEME_CONTENT:
                    bundle.addItem(new FileItem(
                            getAssetFileDescriptor(uri),
                            getFilename(uri)
                    ));
                    break;
                case ContentResolver.SCHEME_FILE:
                    File file = new File(uri.getPath());
                    if (file.isDirectory()) {
                        traverseDirectory(file, bundle);
                    } else {
                        bundle.addItem(new FileItem(file));
                    }
                    break;
            }
        }
        return bundle;
    }

    /**
     * Start a transfer using the provided intent
     */
    private int startTransfer(Intent intent) {

        // Build the parameters needed to start the transfer
        Device device = (Device) intent.getSerializableExtra(EXTRA_DEVICE);
        String deviceName = mSharedPreferences.getString(
                getString(R.string.setting_device_name), "");
        if (deviceName.isEmpty()) {
            deviceName = Build.MODEL;
        }

        // Add each of the items to the bundle and send it
        try {
            Bundle bundle = createBundle(intent.getParcelableArrayListExtra(EXTRA_URIS));
            new TransferWrapper(
                    this,
                    new Transfer(device, deviceName, bundle),
                    mTransferNotificationManager
            ).run();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
            mTransferNotificationManager.stop();
        }

        return START_NOT_STICKY;
    }

    /**
     * Stop a transfer in progress
     */
    private int stopTransfer(Intent intent) {
        TransferWrapper.stopTransfer(intent.getIntExtra(EXTRA_TRANSFER, -1));
        return START_NOT_STICKY;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (intent.getAction()) {
            case ACTION_START_LISTENING:
                return startListening();
            case ACTION_STOP_LISTENING:
                return stopListening();
            case ACTION_START_TRANSFER:
                return startTransfer(intent);
            case ACTION_STOP_TRANSFER:
                return stopTransfer(intent);
        }
        return 0;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
