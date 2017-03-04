package net.nitroshare.android.transfer;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import net.nitroshare.android.R;
import net.nitroshare.android.bundle.Bundle;
import net.nitroshare.android.bundle.FileItem;
import net.nitroshare.android.discovery.Device;

import java.io.File;
import java.io.IOException;
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
    public static final String EXTRA_URLS = "urls";
    public static final String EXTRA_FILENAMES = "filenames";

    public static final String ACTION_STOP_TRANSFER = "stop_transfer";
    public static final String EXTRA_TRANSFER = "transfer";

    private TransferNotificationManager mTransferNotificationManager;
    private TransferServer mTransferServer;
    private SharedPreferences mSharedPreferences;

    /**
     * Initialize the service
     */
    public TransferService() {
        mTransferNotificationManager = new TransferNotificationManager(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
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
        return START_NOT_STICKY;
    }

    /**
     * Traverse a directory tree and add all files to the bundle
     * @param root the directory to which all filenames will be relative
     * @param bundle target for all files that are found
     */
    private void traverseDirectory(File root, Bundle bundle) {
        Stack<File> stack = new Stack<>();
        stack.push(root);
        while (stack.empty()) {
            File topOfStack = stack.pop();
            for (File f : topOfStack.listFiles()) {
                if (f.isDirectory()) {
                    stack.push(f);
                } else {
                    String relativeFilename = f.getAbsolutePath().substring(
                            root.getAbsolutePath().length() + 1);
                    bundle.addItem(new FileItem(f, relativeFilename));
                }
            }
        }
    }

    /**
     * Create a bundle from the provided list of URLs and files
     * @param urls list of URLs
     * @param filenames list of filenames
     * @return newly created bundle
     */
    private Bundle createBundle(String[] urls, String[] filenames) {
        Bundle bundle = new Bundle();
        if (urls != null) {
            for (String url : urls) {
                // TODO: add URL
            }
        }
        if (filenames != null) {
            for (String filename : filenames) {
                File file = new File(filename);
                if (file.isDirectory()) {
                    traverseDirectory(file, bundle);
                } else {
                    bundle.addItem(new FileItem(file));
                }
            }
        }
        return bundle;
    }

    /**
     * Start a transfer using the provided intent
     */
    private int startTransfer(Intent intent) {

        // Retrieve the parameters from the intent
        final Device device = (Device) intent.getSerializableExtra(EXTRA_DEVICE);
        String[] urls = intent.getStringArrayExtra(EXTRA_URLS);
        String[] filenames = intent.getStringArrayExtra(EXTRA_FILENAMES);

        // Retrieve the name for the device
        String deviceName = mSharedPreferences.getString(
                getString(R.string.setting_device_name), "");
        if (deviceName.isEmpty()) {
            deviceName = Build.MODEL;
        }

        // Create the bundle
        Bundle bundle = createBundle(urls, filenames);

        // Create the transfer and transfer wrapper
        try {
            new TransferWrapper(
                    this,
                    new Transfer(device, deviceName, bundle),
                    mTransferNotificationManager
            ).run();
        } catch (IOException e) {
            e.printStackTrace();
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
