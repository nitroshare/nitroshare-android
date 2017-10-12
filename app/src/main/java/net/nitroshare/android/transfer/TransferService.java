package net.nitroshare.android.transfer;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;

import net.nitroshare.android.bundle.Bundle;
import net.nitroshare.android.bundle.FileItem;
import net.nitroshare.android.discovery.Device;
import net.nitroshare.android.util.Settings;

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

    public static final String ACTION_START_LISTENING = "net.nitroshare.android.START_LISTENING";
    public static final String ACTION_STOP_LISTENING = "net.nitroshare.android.STOP_LISTENING";

    public static final String ACTION_START_TRANSFER = "net.nitroshare.android.START_TRANSFER";
    public static final String EXTRA_DEVICE = "net.nitroshare.android.DEVICE";
    public static final String EXTRA_URIS = "net.nitroshare.android.URLS";
    public static final String EXTRA_ID = "net.nitroshare.android.ID";

    public static final String ACTION_STOP_TRANSFER = "net.nitroshare.android.STOP_TRANSFER";
    public static final String ACTION_REMOVE_TRANSFER = "net.nitroshare.android.REMOVE_TRANSFER";
    public static final String EXTRA_TRANSFER = "net.nitroshare.android.TRANSFER";

    public static final String ACTION_BROADCAST = "net.nitroshare.android.BROADCAST";

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

        // Android O doesn't allow certain broadcasts to start services as per usual
        if (start && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    private TransferNotificationManager mTransferNotificationManager;
    private TransferServer mTransferServer;
    private Settings mSettings;

    private TransferManager mTransferManager;

    @Override
    public void onCreate() {
        super.onCreate();

        mTransferNotificationManager = new TransferNotificationManager(this);
        try {
            mTransferServer = new TransferServer(this, mTransferNotificationManager, new TransferServer.Listener() {
                @Override
                public void onNewTransfer(Transfer transfer) {
                    transfer.setId(mTransferNotificationManager.nextId());
                    mTransferManager.addTransfer(transfer, null);
                }
            });
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        mSettings = new Settings(this);
        mTransferManager = new TransferManager(this, mTransferNotificationManager);
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
        return START_NOT_STICKY;
    }

    /**
     * Attempt to resolve the provided URI
     * @param uri URI to resolve
     * @return file descriptor
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

        // Add each of the items to the bundle and send it
        try {
            Bundle bundle = createBundle(intent.getParcelableArrayListExtra(EXTRA_URIS));
            int nextId = intent.getIntExtra(EXTRA_ID, 0);
            if (nextId == 0) {
                nextId = mTransferNotificationManager.nextId();
            }
            Transfer transfer = new Transfer(device,
                    mSettings.getString(Settings.Key.DEVICE_NAME), bundle);
            transfer.setId(nextId);
            mTransferManager.addTransfer(transfer, intent);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());

            mTransferNotificationManager.stopService();
        }

        return START_NOT_STICKY;
    }

    /**
     * Stop a transfer in progress
     */
    private int stopTransfer(Intent intent) {
        mTransferManager.stopTransfer(intent.getIntExtra(EXTRA_TRANSFER, -1));
        return START_NOT_STICKY;
    }

    /**
     * Remove (dismiss) a transfer that has completed
     */
    private int removeTransfer(Intent intent) {
        int id = intent.getIntExtra(EXTRA_TRANSFER, -1);
        mTransferManager.removeTransfer(id);
        mTransferNotificationManager.removeNotification(id);
        mTransferNotificationManager.stopService();
        return START_NOT_STICKY;
    }

    /**
     * Trigger a broadcast for all transfers
     */
    private int broadcast() {
        mTransferManager.broadcastTransfers();
        mTransferNotificationManager.stopService();
        return START_NOT_STICKY;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, String.format("received intent: %s", intent.getAction()));

        switch (intent.getAction()) {
            case ACTION_START_LISTENING:
                return startListening();
            case ACTION_STOP_LISTENING:
                return stopListening();
            case ACTION_START_TRANSFER:
                return startTransfer(intent);
            case ACTION_STOP_TRANSFER:
                return stopTransfer(intent);
            case ACTION_REMOVE_TRANSFER:
                return removeTransfer(intent);
            case ACTION_BROADCAST:
                return broadcast();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Log.d(TAG, "service destroyed");
    }


    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
