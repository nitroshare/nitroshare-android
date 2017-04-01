package net.nitroshare.android.transfer;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import net.nitroshare.android.R;
import net.nitroshare.android.bundle.FileItem;
import net.nitroshare.android.bundle.Item;

/**
 * Threaded wrapper for the Transfer class.
 *
 * This class wraps the Transfer class, taking care of UI notifications and
 * providing an interface for interacting with the transfer through the UI.
 */
class TransferWrapper implements
        Transfer.ConnectListener,
        Transfer.HeaderListener,
        Transfer.ItemListener,
        Transfer.ProgressListener,
        Transfer.FinishListener {

    private static final String TAG = "TransferWrapper";

    private int mId;
    private Context mContext;
    private Transfer mTransfer;
    private TransferNotificationManager mTransferNotificationManager;

    // TODO: do this in the adapter
    private MediaScannerConnection mMediaScannerConnection;

    private NotificationCompat.Builder mBuilder;
    private long mLastTimestamp = 0;

    /**
     * Retrieve the correct icon to display for transfers
     */
    public int icon(boolean done) {
        return mTransfer.getDirection() == Transfer.Direction.Receive ?
                (done ? android.R.drawable.stat_sys_download_done :
                        android.R.drawable.stat_sys_download) :
                (done ? android.R.drawable.stat_sys_upload_done :
                        android.R.drawable.stat_sys_upload);
    }

    /**
     * Create the ongoing notification to show during the transfer
     *
     * I haven't figured out how to avoid the deprecation warning with
     * addAction() without breaking backwards compatibility.
     */
    private void createNotification() {

        // Intent for stopping this particular service
        Intent stopIntent = new Intent(mContext, TransferService.class)
                .setAction(TransferService.ACTION_STOP_TRANSFER)
                .putExtra(TransferService.EXTRA_TRANSFER, mId);

        // Create the notification
        mBuilder = new NotificationCompat.Builder(mContext)
                .setContentTitle(mContext.getString(R.string.service_transfer_title))
                .setSmallIcon(icon(false))
                .setProgress(0, 0, true)
                .addAction(
                        R.drawable.ic_action_stop,
                        mContext.getString(R.string.service_transfer_action_stop),
                        PendingIntent.getService(mContext, 0, stopIntent, 0)
                );

        // Display it initially
        mTransferNotificationManager.start(mId, mBuilder.build());
    }

    /**
     * Update the ongoing notification
     */
    private void updateNotification() {
        mTransferNotificationManager.update(mId, mBuilder.build());
    }

    /**
     * Start the transfer in a separate thread
     */
    private void startTransfer() {
        new Thread(mTransfer).start();
    }

    /**
     * Connect to the media scanner and start the transfer when complete
     */
    private void connectToMediaScanner() {
        mMediaScannerConnection = new MediaScannerConnection(mContext, new MediaScannerConnection.MediaScannerConnectionClient() {
            @Override
            public void onMediaScannerConnected() {
                Log.i(TAG, "connected to media scanner");
                startTransfer();
            }

            @Override
            public void onScanCompleted(String path, Uri uri) {
            }
        });
        mMediaScannerConnection.connect();
    }

    /**
     * Create a transfer wrapper for the provided transfer
     */
    TransferWrapper(int id, Context context, Transfer transfer, TransferNotificationManager transferNotificationManager) {
        mId = id;
        mContext = context;
        mTransfer = transfer;
        mTransferNotificationManager = transferNotificationManager;

        // Create the ongoing notification to be displayed
        createNotification();

        // Register this instance for event callbacks
        mTransfer.addConnectListener(this);
        mTransfer.addHeaderListener(this);
        mTransfer.addItemListener(this);
        mTransfer.addProgressListener(this);

        // Start the transfer, waiting for the media scanner connection if the
        // transfer is receiving files
        if (mTransfer.getDirection() == Transfer.Direction.Receive) {
            connectToMediaScanner();
        } else {
            startTransfer();
        }
    }

    /**
     * Retrieve the ID of the transfer
     */
    public int getId() {
        return mId;
    }

    /**
     * Retrieve the transfer being wrapped
     */
    public Transfer getTransfer() {
        return mTransfer;
    }

    @Override
    public void onConnect() {
        Log.i(TAG, String.format("connected to %s", mTransfer.getRemoteDeviceName()));

        mBuilder.setContentText(
                mContext.getString(
                        R.string.service_transfer_status_sending,
                        mTransfer.getRemoteDeviceName()
                )
        );
        updateNotification();
    }

    @Override
    public void onHeader() {
        Log.i(TAG, "transfer header received");

        mBuilder.setContentText(
                mContext.getString(
                        R.string.service_transfer_status_receiving,
                        mTransfer.getRemoteDeviceName()
                )
        );
        updateNotification();
    }

    @Override
    public void onItem(Item item) {
        if (item instanceof FileItem) {
            String path = ((FileItem) item).getPath();
            mMediaScannerConnection.scanFile(path, null);
        }
    }

    @Override
    public void onProgress() {
        long currentTimestamp = System.currentTimeMillis();
        if (currentTimestamp - mLastTimestamp >= 1000) {
            mBuilder.setProgress(100, mTransfer.getProgress(), false);
            mLastTimestamp = currentTimestamp;
            updateNotification();
        }
    }

    @Override
    public void onFinish() {
        if (mMediaScannerConnection != null) {
            mMediaScannerConnection.disconnect();
        }
    }
}
