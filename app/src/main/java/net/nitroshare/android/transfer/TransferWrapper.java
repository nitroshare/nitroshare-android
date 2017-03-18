package net.nitroshare.android.transfer;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.preference.PreferenceManager;
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

    private int mId = sNotificationId.incrementAndGet();
    private Context mContext;
    private Transfer mTransfer;
    private SharedPreferences mSharedPreferences;
    private TransferNotificationManager mTransferNotificationManager;
    private MediaScannerConnection mMediaScannerConnection;
    private Notification.Builder mNotificationBuilder;

    /**
     * Create a notification using the provided text
     * @param playSound whether a notification sound should be played or not
     * @return newly created notification
     */
    private Notification.Builder createNotification(boolean playSound) {
        Notification.Builder notificationBuilder = new Notification.Builder(mContext)
                .setContentTitle(mContext.getString(R.string.service_transfer_title));
        if (playSound && mSharedPreferences.getBoolean(mContext.getString(
                R.string.setting_notification_sound), false)) {
            notificationBuilder.setDefaults(Notification.DEFAULT_SOUND);
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
            Notification.Builder notificationBuilder = createNotification(true)
                    .setContentText(
                            mContext.getString(
                                    R.string.service_transfer_status_success,
                                    mTransfer.getRemoteDeviceName()
                            )
                    )
                    .setSmallIcon(icon(true));
            mTransferNotificationManager.update(
                    sNotificationId.incrementAndGet(), notificationBuilder.build()
            );
        }

        @Override
        public void onError(String message) {
            Log.i(TAG, String.format("transfer #%d failed: %s", mId, message));
            mTransferNotificationManager.update(
                    sNotificationId.incrementAndGet(),
                    createNotification(true)
                            .setContentText(
                                    mContext.getString(
                                            R.string.service_transfer_status_error,
                                            mTransfer.getRemoteDeviceName(),
                                            message
                                    )
                            )
                            .setSmallIcon(icon(true))
                            .build()
            );
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
     * Create a pending intent for stopping the transfer
     * @return newly created pending intent
     */
    private PendingIntent createStopPendingIntent() {
        Intent stopIntent = new Intent(mContext, TransferService.class)
                .setAction(TransferService.ACTION_STOP_TRANSFER)
                .putExtra(TransferService.EXTRA_TRANSFER, mId);
        return PendingIntent.getService(mContext, 0, stopIntent, 0);
    }

    /**
     * Create a wrapper for the provided transfer
     * @param context context for retrieving string resources
     * @param transfer transfer to wrap
     * @param transferNotificationManager notification manager
     */
    TransferWrapper(Context context, Transfer transfer, TransferNotificationManager transferNotificationManager) {
        mContext = context;
        mTransfer = transfer;
        mTransfer.setListener(new TransferListener());
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        mTransferNotificationManager = transferNotificationManager;
        mNotificationBuilder = createNotification(false)
                .addAction(
                        R.drawable.ic_action_stop,
                        mContext.getString(R.string.service_transfer_action_stop),
                        createStopPendingIntent()
                )
                .setContentText(
                        mContext.getString(
                                mTransfer.getDirection() == Transfer.Direction.Receive ?
                                        R.string.service_transfer_status_receiving :
                                        R.string.service_transfer_status_connecting,
                                mTransfer.getRemoteDeviceName()
                        )
                )
                .setSmallIcon(icon(false))
                .setProgress(0, 0, true);
        synchronized (sActiveTransfers) {
            sActiveTransfers.append(mId, this);
        }
        mTransferNotificationManager.start(mId, mNotificationBuilder.build());
        Log.i(TAG, String.format("created transfer #%d", mId));

        // When receiving items, connect to the media scanner first so that the
        // the files can be passed to it
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
