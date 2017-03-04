package net.nitroshare.android.transfer;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.SparseArray;

import net.nitroshare.android.R;

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
    private TransferNotificationManager mTransferNotificationManager;
    private Notification.Builder mNotificationBuilder;

    /**
     * Create a notification using the provided text
     * @return newly created notification
     */
    private Notification.Builder createNotification() {
        return new Notification.Builder(mContext)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentTitle(mContext.getString(R.string.service_transfer_title));
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
        public void onDeviceName() {
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
            mNotificationBuilder.setProgress(100, progress, false);
            mTransferNotificationManager.update(mId, mNotificationBuilder.build());
        }

        @Override
        public void onSuccess() {
            Log.i(TAG, String.format("transfer #%d succeeded", mId));
            mTransferNotificationManager.update(
                    sNotificationId.incrementAndGet(),
                    createNotification()
                            .setContentText(
                                    mContext.getString(
                                            R.string.service_transfer_status_success,
                                            mTransfer.getRemoteDeviceName()
                                    )
                            )
                            .setSmallIcon(icon(true))
                            .build()
            );
        }

        @Override
        public void onError(String message) {
            Log.i(TAG, String.format("transfer #%d failed: %s", mId, message));
            mTransferNotificationManager.update(
                    sNotificationId.incrementAndGet(),
                    createNotification()
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
        }
    }

    /**
     * Create an action for stopping the transfer
     * @return newly created action
     */
    private Notification.Action createStopAction() {
        Intent stopIntent = new Intent(mContext, TransferService.class)
                .setAction(TransferService.ACTION_STOP_TRANSFER)
                .putExtra(TransferService.EXTRA_TRANSFER, mId);
        PendingIntent pendingIntent =  PendingIntent.getService(
                mContext, 0, stopIntent, 0);
        //noinspection deprecation
        return new Notification.Action.Builder(R.drawable.ic_action_stop,
                mContext.getString(R.string.service_transfer_action_stop),
                pendingIntent).build();
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
        mTransferNotificationManager = transferNotificationManager;
        mNotificationBuilder = createNotification()
                .addAction(createStopAction())
                .setContentText(
                        mContext.getString(
                                mTransfer.getDirection() == Transfer.Direction.Receive ?
                                        R.string.service_transfer_status_receiving :
                                        R.string.service_transfer_status_connecting
                        )
                )
                .setSmallIcon(icon(false))
                .setProgress(0, 0, true);
        synchronized (sActiveTransfers) {
            sActiveTransfers.append(mId, this);
        }
        mTransferNotificationManager.start(mId, mNotificationBuilder.build());
        Log.i(TAG, String.format("created transfer #%d", mId));
    }

    /**
     * Create a new thread and run the transfer in it
     */
    void run() {
        new Thread(mTransfer).start();
    }
}
