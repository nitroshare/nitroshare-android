package net.nitroshare.android.transfer;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.SparseArray;

import net.nitroshare.android.R;
import net.nitroshare.android.bundle.FileItem;
import net.nitroshare.android.bundle.Item;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Integrate the Transfer class with the TransferService
 *
 * This class manages notifications for the transfer and ensures that the
 * service is in the foreground whenever there is an active transfer.
 */
class TransferWrapper {

    private static final String TAG = "TransferWrapper";

    /**
     * Simplify the process of generating unique IDs for notifications
     */
    private static AtomicInteger sNotificationId = new AtomicInteger(1);

    /**
     * Maintain a list of active transfers
     */
    private static final SparseArray<TransferWrapper> sActiveTransfers = new SparseArray<>();

    /**
     * Stop the transfer with the specified ID
     * @param transferId ID of service to stop
     */
    static void stopTransfer(int transferId) {
        synchronized (sActiveTransfers) {
            TransferWrapper transferWrapper = sActiveTransfers.get(transferId);
            if (transferWrapper != null) {
                transferWrapper.mTransfer.stop();
            }
        }
    }

    private int mId;
    private Context mContext;
    private Transfer mTransfer;
    private SharedPreferences mSharedPreferences;
    private NotificationCompat.Builder mNotificationBuilder;
    private TransferNotificationManager mTransferNotificationManager;
    private Intent mRetryIntent;
    private MediaScannerConnection mMediaScannerConnection;

    /**
     * Create a notification using the provided text
     * @return newly created notification
     */
    private NotificationCompat.Builder createNotification() {
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(mContext)
                .setContentTitle(mContext.getString(R.string.service_transfer_title));
        if (mSharedPreferences.getBoolean(mContext.getString(
                R.string.setting_notification_sound), false)) {
            notificationBuilder.setDefaults(NotificationCompat.DEFAULT_SOUND);
        }
        return notificationBuilder;
    }

    /**
     * Retrieve the correct icon to display for transfers
     * @param done if the transfer is completed
     */
    private int icon(boolean done) {
        return mTransfer.getDirection() == Transfer.Direction.Receive ?
                (done ? android.R.drawable.stat_sys_download_done :
                        android.R.drawable.stat_sys_download) :
                (done ? android.R.drawable.stat_sys_upload_done :
                        android.R.drawable.stat_sys_upload);
    }

    /**
     * Listener for transfer events
     */
    private class TransferListener implements Transfer.Listener {

        private long mLastTimestamp = 0;

        @Override
        public void onConnect() {
            mNotificationBuilder.setContentText(
                    mContext.getString(
                            R.string.service_transfer_status_sending,
                            mTransfer.getRemoteDeviceName()
                    )
            );
            mTransferNotificationManager.update(mId, mNotificationBuilder.build());
        }

        @Override
        public void onTransferHeader(long count) {
            Log.i(TAG, String.format("incoming transfer contains %d items", count));

            mNotificationBuilder.setContentText(
                    mContext.getString(
                            R.string.service_transfer_status_receiving,
                            mTransfer.getRemoteDeviceName()
                    )
            );
            mTransferNotificationManager.update(mId, mNotificationBuilder.build());
        }

        @Override
        public void onProgress(int progress) {
            long currentTimestamp = System.currentTimeMillis();
            if (currentTimestamp - mLastTimestamp >= 1000) {
                mNotificationBuilder.setProgress(100, progress, false);
                mTransferNotificationManager.update(mId, mNotificationBuilder.build());
                mLastTimestamp = currentTimestamp;
            }
        }

        @Override
        public void onItemReceived(Item item) {
            if (item instanceof FileItem) {
                String path = ((FileItem) item).getPath();
                Log.d(TAG, String.format("submitting \"%s\" to media scanner", path));
                mMediaScannerConnection.scanFile(path, null);
            }
        }

        @Override
        public void onSuccess() {
            Log.i(TAG, String.format("transfer #%d succeeded", mId));
            String contentText = mContext.getString(
                    R.string.service_transfer_status_success,
                    mTransfer.getRemoteDeviceName()
            );
            NotificationCompat.Builder builder = createNotification()
                    .setContentText(contentText)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                    .setSmallIcon(icon(true));
            mTransferNotificationManager.update(
                    sNotificationId.incrementAndGet(), builder.build()
            );
        }

        @Override
        public void onError(String message) {
            Log.i(TAG, String.format("transfer #%d failed: %s", mId, message));
            int newId = sNotificationId.incrementAndGet();
            mRetryIntent.putExtra(TransferService.EXTRA_ID, newId);
            String contentText = mContext.getString(
                    R.string.service_transfer_status_error,
                    mTransfer.getRemoteDeviceName(),
                    message
            );
            NotificationCompat.Builder builder = createNotification()
                    .addAction(
                            R.drawable.ic_action_retry,
                            mContext.getString(R.string.service_transfer_action_retry),
                            PendingIntent.getService(mContext, 0, mRetryIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT)
                    )
                    .setContentText(contentText)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                    .setSmallIcon(icon(true));
            mTransferNotificationManager.update(newId, builder.build());
        }

        @Override
        public void onFinish() {
            synchronized (sActiveTransfers) {
                sActiveTransfers.remove(mId);
            }
            mTransferNotificationManager.stop(mId);
            if (mTransfer.getDirection() == Transfer.Direction.Receive) {
                mMediaScannerConnection.disconnect();
                Log.i(TAG, "disconnected from media scanner");
            }
        }
    }

    /**
     * Create the ongoing notification to show during the transfer
     *
     * I haven't figured out how to avoid the deprecation warning with
     * addAction() without breaking backwards compatibility.
     */
    private NotificationCompat.Builder createOngoingNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext);
        builder.setContentTitle(mContext.getString(R.string.service_transfer_title));
        Intent stopIntent = new Intent(mContext, TransferService.class)
                .setAction(TransferService.ACTION_STOP_TRANSFER)
                .putExtra(TransferService.EXTRA_TRANSFER, mId);
        builder.addAction(
                R.drawable.ic_action_stop,
                mContext.getString(R.string.service_transfer_action_stop),
                PendingIntent.getService(mContext, 0, stopIntent, 0)
        );
        builder.setSmallIcon(icon(false));
        builder.setProgress(0, 0, true);
        return builder;
    }

    /**
     * Create a wrapper for the provided transfer
     * @param context context for retrieving string resources
     * @param transfer transfer to wrap
     * @param transferNotificationManager notification manager
     * @param intent intent used to start the transfer (or null)
     */
    TransferWrapper(Context context, Transfer transfer, TransferNotificationManager transferNotificationManager, Intent intent) {
        mId = intent.getIntExtra(TransferService.EXTRA_ID, 0);
        if (mId == 0) {
            mId = sNotificationId.incrementAndGet();
        }
        mContext = context;
        mTransfer = transfer;
        mTransfer.setListener(new TransferListener());
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mNotificationBuilder = createOngoingNotification();
        mTransferNotificationManager = transferNotificationManager;
        mTransferNotificationManager.start(mId, mNotificationBuilder.build());
        mRetryIntent = intent;

        Log.i(TAG, String.format("created transfer #%d", mId));
        synchronized (sActiveTransfers) {
            sActiveTransfers.append(mId, this);
        }

        // When receiving items, connect to the media scanner first so that the
        // the files can be passed to it immediately
        if (mTransfer.getDirection() == Transfer.Direction.Receive) {
            mMediaScannerConnection = new MediaScannerConnection(mContext, new MediaScannerConnection.MediaScannerConnectionClient() {
                @Override
                public void onMediaScannerConnected() {
                    Log.i(TAG, "connected to media scanner");
                    new Thread(mTransfer).start();
                }

                @Override
                public void onScanCompleted(String path, Uri uri) {
                }
            });
            mMediaScannerConnection.connect();
        } else {
            new Thread(mTransfer).start();
        }
    }
}
