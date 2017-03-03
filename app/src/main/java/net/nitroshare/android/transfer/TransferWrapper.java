package net.nitroshare.android.transfer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
    private static AtomicInteger sNotificationId = new AtomicInteger(0);

    /**
     * Maintain a list of active transfers
     */
    private static final SparseArray<TransferWrapper> sActiveTransfers = new SparseArray<>();

    /**
     * Stop the transfer with the specified ID
     * @param transferId ID of service to stop
     */
    static void stopTransfer(int transferId) {
        TransferWrapper transferWrapper = sActiveTransfers.get(transferId);
        if (transferWrapper != null) {
            transferWrapper.mTransfer.stop();
        }
    }

    private int mId = sNotificationId.incrementAndGet();
    private Service mService;
    private Transfer mTransfer;
    private Notification.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;

    /**
     * Create a notification using the provided text
     * @return newly created notification
     */
    private Notification.Builder createNotification() {
        return new Notification.Builder(mService)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentTitle(mService.getString(R.string.service_transfer_title))
                .setSmallIcon(R.drawable.ic_stat_transfer);
    }

    /**
     * Use this transfer for the foreground notification
     */
    private void setForeground() {
        Log.d(TAG, String.format("setting #%d as the foreground transfer", mId));
        mService.startForeground(mId, mNotificationBuilder.build());
    }

    /**
     * Listener for transfer events
     */
    private class TransferListener implements Transfer.Listener {

        @Override
        public void onConnect() {
            mNotificationBuilder.setContentText(
                    mService.getString(
                        R.string.service_transfer_status_sending,
                        mTransfer.getRemoteDeviceName()
                    )
            );
            mNotificationManager.notify(mId, mNotificationBuilder.build());
        }

        @Override
        public void onDeviceName() {
            mNotificationManager.notify(mId, mNotificationBuilder.build());
        }

        @Override
        public void onProgress(int progress) {
            mNotificationBuilder.setProgress(100, progress, false);
            mNotificationManager.notify(mId, mNotificationBuilder.build());
        }

        @Override
        public void onSuccess() {
            Log.i(TAG, String.format("transfer #%d succeeded", mId));
            mNotificationManager.notify(
                    sNotificationId.incrementAndGet(),
                    createNotification().setContentText(
                            mService.getString(
                                    R.string.service_transfer_status_success,
                                    mTransfer.getRemoteDeviceName()
                            )
                    ).build()
            );
        }

        @Override
        public void onError(String message) {
            Log.i(TAG, String.format("transfer #%d failed: %s", mId, message));
            mNotificationManager.notify(
                    sNotificationId.incrementAndGet(),
                    createNotification().setContentText(
                            mService.getString(
                                    R.string.service_transfer_status_error,
                                    mTransfer.getRemoteDeviceName(),
                                    message
                            )
                    ).build()
            );
        }

        @Override
        public void onFinish() {
            synchronized (sActiveTransfers) {
                sActiveTransfers.remove(mId);
                if (sActiveTransfers.size() > 0) {
                    sActiveTransfers.valueAt(0).setForeground();
                    mNotificationManager.cancel(mId);
                } else {
                    Log.i(TAG, "all transfers finished; stopping service");
                    mService.stopSelf();
                }
            }
        }
    }

    /**
     * Create an action for stopping the transfer
     * @return newly created action
     */
    private Notification.Action createStopAction() {
        Intent stopIntent = new Intent(mService, TransferService.class)
                .setAction(TransferService.ACTION_STOP_TRANSFER)
                .putExtra(TransferService.EXTRA_TRANSFER, mId);
        PendingIntent pendingIntent =  PendingIntent.getService(
                mService, 0, stopIntent, 0);
        //noinspection deprecation
        return new Notification.Action.Builder(R.drawable.ic_action_stop,
                mService.getString(R.string.service_transfer_action_stop),
                pendingIntent).build();
    }

    /**
     * Create a wrapper for the provided transfer
     * @param service service hosting the transfer
     * @param transfer transfer to wrap
     */
    TransferWrapper(Service service, Transfer transfer) {
        mService = service;
        mTransfer = transfer;
        mTransfer.setListener(new TransferListener());
        mNotificationBuilder = createNotification()
                .addAction(createStopAction())
                .setContentText(
                        mService.getString(
                                mTransfer.getDirection() == Transfer.Direction.Receive ?
                                        R.string.service_transfer_status_receiving :
                                        R.string.service_transfer_status_connecting
                        )
                )
                .setProgress(0, 0, true);
        mNotificationManager = (NotificationManager) mService.getSystemService(
                Service.NOTIFICATION_SERVICE);
        Log.i(TAG, String.format("created transfer #%d", mId));
        synchronized (sActiveTransfers) {
            sActiveTransfers.put(mId, this);
            setForeground();
        }
    }

    /**
     * Create a new thread and run the transfer in it
     */
    void run() {
        new Thread(mTransfer).start();
    }
}
