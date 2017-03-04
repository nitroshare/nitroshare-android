package net.nitroshare.android.transfer;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import net.nitroshare.android.R;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

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

    @Override
    public void run() {
        Log.i(TAG, "starting server...");
        mTransferNotificationManager.start(
                NOTIFICATION_ID,
                new Notification.Builder(mContext)
                        .setCategory(Notification.CATEGORY_SERVICE)
                        .setContentTitle(mContext.getString(R.string.service_transfer_server_title))
                        .setContentText(mContext.getString(R.string.service_transfer_server_text))
                        .setSmallIcon(R.drawable.ic_stat_transfer)
                        .build()
        );
        try {
            // Create a server and attempt to bind to a port
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(40818));

            Log.i(TAG, String.format("server bound to port %d",
                    serverSocketChannel.socket().getLocalPort()));

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
                    String unknownDeviceName = mContext.getString(
                            R.string.service_transfer_unknown_device);
                    new TransferWrapper(mContext, new Transfer(
                            socketChannel, transferDirectory, unknownDeviceName));
                }
            }

        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        mTransferNotificationManager.stop(NOTIFICATION_ID);
        Log.i(TAG, "server stopped");
    }
}
