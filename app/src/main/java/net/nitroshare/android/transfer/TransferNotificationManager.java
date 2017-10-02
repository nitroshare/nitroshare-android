package net.nitroshare.android.transfer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.SparseArray;

import net.nitroshare.android.R;
import net.nitroshare.android.ui.transfer.TransferActivity;
import net.nitroshare.android.util.Settings;

/**
 * Manage notifications and service lifecycle
 *
 * This class manages the notification shown while the server is listening for
 * incoming connections and during file transfers. Additional notifications
 * are shown when transfers complete (either successfully or with error).
 */
class TransferNotificationManager {

    private static final String CHANNEL_ID = "transfer";
    private static final int NOTIFICATION_ID = 1;

    private Service mService;
    private Settings mSettings;

    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mBuilder;
    private PendingIntent mIntent;

    private boolean mActive = false;
    private boolean mListening = false;

    private int mNextId = 2;
    private SparseArray<TransferStatus> mStatuses = new SparseArray<>();

    /**
     * Create a notification manager for the specified service
     * @param service service to manage
     */
    TransferNotificationManager(Service service) {
        mService = service;
        mSettings = new Settings(service);
        mNotificationManager = (NotificationManager) mService.getSystemService(
                Service.NOTIFICATION_SERVICE);

        // Android O requires a notification channel to be specified
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    mService.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription(mService.getString(R.string.notification_channel_description));
            channel.enableLights(true);
            channel.setLightColor(Color.BLUE);
            channel.enableVibration(true);
            mNotificationManager.createNotificationChannel(channel);
        }

        // Create the intent for opening the main activity
        mIntent = PendingIntent.getActivity(
                mService,
                0,
                new Intent(mService, TransferActivity.class),
                0
        );

        // Prepare the notification that will be shown during activity
        mBuilder = createBuilder()
                .setContentIntent(mIntent)
                .setContentTitle(mService.getString(R.string.service_transfer_server_title))
                .setSmallIcon(R.drawable.ic_stat_transfer);
    }

    /**
     * Create a new notification
     * @return notification
     */
    private NotificationCompat.Builder createBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new NotificationCompat.Builder(mService, CHANNEL_ID);
        } else {
            //noinspection deprecation
            return new NotificationCompat.Builder(mService);
        }
    }

    /**
     * Retrieve the next unique integer for a transfer
     * @return new ID
     */
    synchronized int nextId() {
        return mNextId++;
    }

    /**
     * Update the notification to reflect that the server is listening for transfers
     */
    synchronized void startListening() {
        mListening = true;
        updateNotification();
    }

    /**
     * Update the notification to reflect that the server is no longer listening for transfers
     */
    synchronized void stopListening() {
        mListening = false;
        updateNotification();
    }

    /**
     * Attempt to stop the service if there is no activity
     * @return true if the service was stopped
     */
    synchronized boolean stop() {
        if (!mListening && mStatuses.size() == 0) {
            mActive = false;
            mService.stopSelf();
            return true;
        }
        return false;
    }

    /**
     * Update the notification to reflect the new status of a transfer
     */
    synchronized void updateTransfer(TransferStatus transferStatus) {
        if (transferStatus.isFinished()) {
            mStatuses.remove(transferStatus.getId());
        } else {
            mStatuses.put(transferStatus.getId(), transferStatus);
        }
        updateNotification();
    }

    /**
     * Show, hide, or update the notification based on current state
     */
    private synchronized void updateNotification() {

        // Shut the service down if nothing is happening
        if (stop()) {
            return;
        }

        // TODO: show all transfers in progress

        mBuilder.setContentText("This is a test.");

        // If the service hasn't been moved into the foreground yet, do so now
        if (!mActive) {
            mService.startForeground(NOTIFICATION_ID, mBuilder.build());
            mActive = true;
        } else {
            mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        }
    }
}