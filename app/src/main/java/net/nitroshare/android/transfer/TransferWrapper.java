package net.nitroshare.android.transfer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
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

    private int mId = sNotificationId.incrementAndGet();
    private Service mService;
    private Transfer mTransfer;
    private Notification.Builder mNotificationBuilder;
    private NotificationManager mNotificationManager;

    /**
     * Return a localized string with the remote device name interpolated
     * @param resId resource ID
     * @return localized string
     */
    private String localize(int resId) {
        return mService.getString(resId, mTransfer.getRemoteDeviceName());
    }

    /**
     * Use this transfer for the foreground notification
     */
    private void setForeground() {
        mService.startForeground(mId, mNotificationBuilder.build());
    }

    /**
     * Update the ongoing notification displayed for the transfer
     */
    private void updateNotification(int id) {
        mNotificationManager.notify(id, mNotificationBuilder.build());
    }

    /**
     * Listener for transfer events
     */
    private class TransferListener implements Transfer.Listener {

        @Override
        public void onConnect() {
            mNotificationBuilder.setContentText(localize(
                    R.string.service_transfer_status_sending));
            updateNotification(mId);
        }

        @Override
        public void onDeviceName() {
            updateNotification(mId);
        }

        @Override
        public void onProgress(int progress) {
            mNotificationBuilder.setProgress(100, progress, false);
            updateNotification(mId);
        }

        @Override
        public void onSuccess() {
            mNotificationBuilder
                    .setProgress(0, 0, false)
                    .setContentText(localize(R.string.service_transfer_status_success));
            updateNotification(sNotificationId.incrementAndGet());
        }

        @Override
        public void onError(String message) {
            mNotificationBuilder
                    .setProgress(0, 0, false)
                    .setContentText(localize(
                            R.string.service_transfer_status_error) + " " + message);
            updateNotification(sNotificationId.incrementAndGet());
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
     * Create a wrapper for the provided transfer
     * @param service service hosting the transfer
     * @param transfer transfer to wrap
     */
    TransferWrapper(Service service, Transfer transfer) {
        mService = service;
        mTransfer = transfer;
        mTransfer.setListener(new TransferListener());
        mNotificationBuilder = new Notification.Builder(mService)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentTitle(mService.getString(R.string.service_transfer_title))
                .setContentText(localize(
                        mTransfer.getDirection() == Transfer.Direction.Receive ?
                                R.string.service_transfer_status_receiving :
                                R.string.service_transfer_status_connecting))
                .setSmallIcon(R.drawable.ic_stat_transfer)
                .setProgress(0, 0, true);
        mNotificationManager = (NotificationManager) mService.getSystemService(
                Service.NOTIFICATION_SERVICE);
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
