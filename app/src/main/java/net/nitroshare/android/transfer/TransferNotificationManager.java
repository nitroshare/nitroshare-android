package net.nitroshare.android.transfer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import net.nitroshare.android.R;
import net.nitroshare.android.ui.transfer.TransferActivity;
import net.nitroshare.android.util.Settings;

/**
 * Manage notifications and service lifecycle
 *
 * A persistent notification is shown as long as the transfer service is
 * running. A notification is also shown for each transfer in progress,
 * enabling it to be individually cancelled or retried.
 */
class TransferNotificationManager {

    private static final String CHANNEL_ID = "transfer";
    private static final int NOTIFICATION_ID = 1;

    private Service mService;
    private Settings mSettings;

    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mBuilder;
    private PendingIntent mIntent;

    private boolean mListening = false;
    private int mNumTransfers = 0;

    private int mNextId = 2;

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

        // Create the builder
        mBuilder = createBuilder()
                .setContentIntent(mIntent)
                .setContentTitle(mService.getString(R.string.service_transfer_server_title))
                .setSmallIcon(R.drawable.ic_stat_transfer);

        // Set the priority
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mBuilder.setPriority(NotificationManagerCompat.IMPORTANCE_MIN);
        } else {
            mBuilder.setPriority(NotificationCompat.PRIORITY_MIN);
        }
    }

    /**
     * Create a new notification using the method appropriate to the build
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
     *
     * The notification with ID equal to 1 is for the persistent notification
     * shown while the service is active.
     */
    synchronized int nextId() {
        return mNextId++;
    }

    /**
     * Indicate that the server is listening for transfers
     */
    synchronized void startListening() {
        mListening = true;
        updateNotification();
    }

    /**
     * Indicate that the server has stopped listening for transfers
     */
    synchronized void stopListening() {
        mListening = false;
        stop();
    }

    /**
     * Stop the service if no tasks are active
     */
    synchronized void stopService() {
        stop();
    }

    /**
     * Add a new transfer
     */
    synchronized void addTransfer() {
        mNumTransfers++;
        updateNotification();
    }

    /**
     * Update a transfer in progress
     */
    synchronized void updateTransfer(TransferStatus transferStatus) {
        if (transferStatus.isFinished()) {

            // Prepare an appropriate notification for the transfer
            CharSequence contentText;
            int icon;

            // TODO: retry intent

            if (transferStatus.getState() == TransferStatus.State.Succeeded) {
                contentText = mService.getString(
                        R.string.service_transfer_status_success,
                        transferStatus.getRemoteDeviceName()
                );
                icon = R.drawable.ic_stat_success;
            } else {
                contentText = mService.getString(
                        R.string.service_transfer_status_error,
                        transferStatus.getRemoteDeviceName(),
                        transferStatus.getError()
                );
                icon = R.drawable.ic_stat_error;
            }

            // Show the notification
            boolean notifications = mSettings.getBoolean(Settings.Key.TRANSFER_NOTIFICATION);
            mNotificationManager.notify(
                    transferStatus.getId(),
                    createBuilder()
                            .setDefaults(notifications ? NotificationCompat.DEFAULT_ALL : 0)
                            .setContentIntent(mIntent)
                            .setContentTitle(mService.getString(R.string.service_transfer_server_title))
                            .setContentText(contentText)
                            .setSmallIcon(icon)
                            .build()
            );

            mNumTransfers--;

            // Stop the service if there are no active tasks
            if (stop()) {
                return;
            }

            // Update the notification
            updateNotification();

        } else {

            // Prepare the appropriate text for the transfer
            CharSequence contentText;
            int icon;

            if (transferStatus.getDirection() == TransferStatus.Direction.Receive) {
                contentText = mService.getString(
                        R.string.service_transfer_status_receiving,
                        transferStatus.getRemoteDeviceName()
                );
                icon = android.R.drawable.stat_sys_download;
            } else {
                contentText = mService.getString(
                        R.string.service_transfer_status_sending,
                        transferStatus.getRemoteDeviceName()
                );
                icon = android.R.drawable.stat_sys_upload;
            }

            // Intent for stopping this particular service
            Intent stopIntent = new Intent(mService, TransferService.class)
                    .setAction(TransferService.ACTION_STOP_TRANSFER)
                    .putExtra(TransferService.EXTRA_TRANSFER, transferStatus.getId());

            // Update the notification
            mNotificationManager.notify(
                    transferStatus.getId(),
                    createBuilder()
                            .setContentIntent(mIntent)
                            .setContentTitle(mService.getString(R.string.service_transfer_title))
                            .setContentText(contentText)
                            .setProgress(100, transferStatus.getProgress(), false)
                            .setSmallIcon(icon)
                            .addAction(
                                    new NotificationCompat.Action.Builder(
                                            R.drawable.ic_action_stop,
                                            mService.getString(R.string.service_transfer_action_stop),
                                            PendingIntent.getService(mService, transferStatus.getId(), stopIntent, 0)
                                    ).build()
                            )
                            .build()
            );
        }
    }

    private void updateNotification() {
        if (mNumTransfers == 0) {
            mBuilder.setContentText(mService.getString(
                    R.string.service_transfer_server_listening_text));
        } else {
            mBuilder.setContentText(mService.getResources().getQuantityString(
                    R.plurals.service_transfer_server_transferring_text,
                    mNumTransfers, mNumTransfers));
        }
        mService.startForeground(NOTIFICATION_ID, mBuilder.build());
    }

    private boolean stop() {
        if (!mListening && mNumTransfers == 0) {
            mService.stopSelf();
            return true;
        }
        return false;
    }
}