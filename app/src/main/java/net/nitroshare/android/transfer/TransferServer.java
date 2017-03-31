package net.nitroshare.android.transfer;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import net.nitroshare.android.ui.MainActivity;
import net.nitroshare.android.R;
import net.nitroshare.android.discovery.Device;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.UUID;

/**
 * Listen for new connections and create TransferWrappers for them
 */
class TransferServer implements Runnable {

    private static final String TAG = "TransferServer";
    private static final int NOTIFICATION_ID = 1;

    private Thread mThread = new Thread(this);
    private boolean mStop;

    private Context mContext;
    private TransferNotificationManager mTransferNotificationManager;
    private SharedPreferences mSharedPreferences;
    private Selector mSelector = Selector.open();

    private NsdManager.RegistrationListener mRegistrationListener =
            new NsdManager.RegistrationListener() {
        @Override
        public void onServiceRegistered(NsdServiceInfo serviceInfo) {
            Log.i(TAG, "service registered");
        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
            Log.i(TAG, "service unregistered");
        }
        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.e(TAG, String.format("registration failed: %d", errorCode));
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
            Log.e(TAG, String.format("unregistration failed: %d", errorCode));
        }
    };

    /**
     * Create a new transfer server
     * @param context context for retrieving string resources
     * @param transferNotificationManager notification manager
     * @throws IOException
     */
    TransferServer(Context context, TransferNotificationManager transferNotificationManager) throws IOException {
        mContext = context;
        mTransferNotificationManager = transferNotificationManager;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    /**
     * Start the server if it is not already running
     */
    void start() {
        if (!mThread.isAlive()) {
            mStop = false;
            mThread.start();
        }
    }

    /**
     * Stop the transfer server if it is running and wait for it to finish
     */
    void stop() {
        if (mThread.isAlive()) {
            mStop = true;
            mSelector.wakeup();
            try {
                mThread.join();
            } catch (InterruptedException e) {
                Log.e(TAG, e.getMessage());
            }
        }
    }

    // TODO: this method could use some refactoring

    @Override
    public void run() {
        Log.i(TAG, "starting server...");

        // Retrieve the UUID (generating a new one if necessary) and name
        String deviceUuidKey = mContext.getString(R.string.setting_device_uuid);
        String deviceUuid = mSharedPreferences.getString(deviceUuidKey, "");
        if (deviceUuid.isEmpty()) {
            deviceUuid = String.format("{%s}", UUID.randomUUID().toString());
            mSharedPreferences.edit().putString(deviceUuidKey, deviceUuid).apply();
        }
        String deviceName = mSharedPreferences.getString(mContext.getString(
                R.string.setting_device_name), "");
        if (deviceName.isEmpty()) {
            deviceName = Build.MODEL;
        }
        NsdManager nsdManager = null;

        // Create the notification shown while the server is running
        PendingIntent mainIntent = PendingIntent.getActivity(mContext, 0,
                new Intent(mContext, MainActivity.class), 0);
        mTransferNotificationManager.start(
                NOTIFICATION_ID,
                new Notification.Builder(mContext)
                        .setContentIntent(mainIntent)
                        .setContentTitle(mContext.getString(R.string.service_transfer_server_title))
                        .setContentText(mContext.getString(R.string.service_transfer_server_text))
                        .setPriority(Notification.PRIORITY_MIN)
                        .setSmallIcon(R.drawable.ic_stat_transfer)
                        .build()
        );

        try {
            // Create a server and attempt to bind to a port
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(40818));
            serverSocketChannel.configureBlocking(false);

            Log.i(TAG, String.format("server bound to port %d",
                    serverSocketChannel.socket().getLocalPort()));

            // Register the service
            nsdManager = (NsdManager) mContext.getSystemService(Context.NSD_SERVICE);
            nsdManager.registerService(
                    new Device(deviceUuid, deviceName, null, 40818).toServiceInfo(),
                    NsdManager.PROTOCOL_DNS_SD,
                    mRegistrationListener
            );

            // Register the server with the selector
            SelectionKey selectionKey = serverSocketChannel.register(mSelector,
                    SelectionKey.OP_ACCEPT);

            // Create Transfers and TransferWrappers as new connections come in
            while (true) {
                mSelector.select();
                if (mStop) {
                    break;
                }
                if (selectionKey.isAcceptable()) {
                    Log.i(TAG, "accepting incoming connection");
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    String transferDirectory = mSharedPreferences.getString(
                            mContext.getString(R.string.setting_transfer_directory),
                            mContext.getString(R.string.setting_transfer_directory_default)
                    );
                    boolean overwrite = mSharedPreferences.getBoolean(
                            mContext.getString(R.string.setting_behavior_overwrite),
                            false
                    );
                    String unknownDeviceName = mContext.getString(
                            R.string.service_transfer_unknown_device);
                    new TransferWrapper(
                            mContext,
                            new Transfer(
                                    socketChannel,
                                    transferDirectory,
                                    overwrite,
                                    unknownDeviceName
                            ),
                            mTransferNotificationManager,
                            null
                    );
                }
            }

            // Close the server socket
            serverSocketChannel.close();

        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }

        // Unregister the service
        if (nsdManager != null) {
            nsdManager.unregisterService(mRegistrationListener);
        }

        // Close the notification
        mTransferNotificationManager.stop(NOTIFICATION_ID);

        Log.i(TAG, "server stopped");
    }
}
