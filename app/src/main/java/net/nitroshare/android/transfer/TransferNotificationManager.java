package net.nitroshare.android.transfer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.annotation.StringRes;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

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

    private static final String TAG = "TransferNotificationMgr";

    private static final String SERVICE_CHANNEL_ID = "service";
    private static final String TRANSFER_CHANNEL_ID = "transfer";
    private static final String NOTIFICATION_CHANNEL_ID = "notification";

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

        // Android O requires the notification channels to be created
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(SERVICE_CHANNEL_ID, R.string.channel_service_name,
                    NotificationManager.IMPORTANCE_MIN, false);
            createChannel(TRANSFER_CHANNEL_ID, R.string.channel_transfer_name,
                    NotificationManager.IMPORTANCE_LOW, false);
            createChannel(NOTIFICATION_CHANNEL_ID, R.string.channel_notification_name,
                    NotificationManager.IMPORTANCE_DEFAULT, true);
        }

        // Create the intent for opening the main activity
        mIntent = PendingIntent.getActivity(
                mService,
                0,
                new Intent(mService, TransferActivity.class),
                0
        );

        // Create the builder
        mBuilder = createBuilder(SERVICE_CHANNEL_ID)
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
     * Create and register a notification channel
     * @param channelId unique ID for the channel
     * @param nameResId string resource for the channel name
     * @param importance notification priority
     * @param flash true to enable lights and vibration
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createChannel(String channelId, @StringRes int nameResId, int importance, boolean flash) {
        NotificationChannel channel = new NotificationChannel(channelId,
                mService.getString(nameResId), importance);
        if (flash) {
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setShowBadge(true);
        }
        mNotificationManager.createNotificationChannel(channel);
    }

    /**
     * Create a new notification using the method appropriate to the build
     * @return notification
     */
    private NotificationCompat.Builder createBuilder(String channelId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return new NotificationCompat.Builder(mService, channelId);
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
     * Remove a notification
     */
    void removeNotification(int id) {
        mNotificationManager.cancel(id);
    }

    /**
     * Add a new transfer
     */
    synchronized void addTransfer(TransferStatus transferStatus) {
        mNumTransfers++;
        updateNotification();

        // Clear any existing notification (this shouldn't be necessary, but it is :P)
        removeNotification(transferStatus.getId());
    }

    /**
     * Update a transfer in progress
     */
    synchronized void updateTransfer(TransferStatus transferStatus, Intent intent) {
        if (transferStatus.isFinished()) {
            Log.i(TAG, String.format("#%d finished", transferStatus.getId()));

            // Close the ongoing notification (yes, again)
            mNotificationManager.cancel(transferStatus.getId());

            // Do not show a notification for successful transfers that contain no content
            if (transferStatus.getState() != TransferStatus.State.Succeeded ||
                    transferStatus.getBytesTotal() > 0) {

                // Prepare an appropriate notification for the transfer
                CharSequence contentText;
                int icon;

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

                // Build the notification
                boolean notifications = mSettings.getBoolean(Settings.Key.TRANSFER_NOTIFICATION);
                NotificationCompat.Builder builder = createBuilder(NOTIFICATION_CHANNEL_ID)
                        .setDefaults(notifications ? NotificationCompat.DEFAULT_ALL : 0)
                        .setContentIntent(mIntent)
                        .setContentTitle(mService.getString(R.string.service_transfer_server_title))
                        .setContentText(contentText)
                        .setSmallIcon(icon);

                // For transfers that send files (and fail), it is possible to retry them
                if (transferStatus.getState() == TransferStatus.State.Failed &&
                        transferStatus.getDirection() == TransferStatus.Direction.Send) {

                    // Ensure the error notification is replaced by the next transfer (I have no idea
                    // why the first line is required but it works :P)
                    intent.setClass(mService, TransferService.class);
                    intent.putExtra(TransferService.EXTRA_ID, transferStatus.getId());

                    // Add the action
                    builder.addAction(
                            new NotificationCompat.Action.Builder(
                                    R.drawable.ic_action_retry,
                                    mService.getString(R.string.service_transfer_action_retry),
                                    PendingIntent.getService(
                                            mService, transferStatus.getId(),
                                            intent, PendingIntent.FLAG_ONE_SHOT
                                    )
                            ).build()
                    );
                }

                // Show the notification
                mNotificationManager.notify(transferStatus.getId(), builder.build());
            }

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
                    createBuilder(TRANSFER_CHANNEL_ID)
                            .setContentIntent(mIntent)
                            .setContentTitle(mService.getString(R.string.service_transfer_title))
                            .setContentText(contentText)
                            .setOngoing(true)
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

    /**
     * Create a notification for URLs
     * @param url full URL
     */
    void showUrl(String url) {
        int id = nextId();
        PendingIntent pendingIntent = PendingIntent.getActivity(
                mService,
                id,
                new Intent(Intent.ACTION_VIEW, Uri.parse(url)),
                0
        );
        mNotificationManager.notify(
                id,
                createBuilder(NOTIFICATION_CHANNEL_ID)
                        .setContentIntent(pendingIntent)
                        .setContentTitle(mService.getString(R.string.service_transfer_notification_url))
                        .setContentText(url)
                        .setSmallIcon(R.drawable.ic_url)
                        .build()
        );
    }

    private void updateNotification() {
        Log.i(TAG, String.format("updating notification with %d transfer(s)...", mNumTransfers));

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
            Log.i(TAG, "not listening and no transfers, shutting down...");

            mService.stopSelf();
            return true;
        }
        return false;
    }
}