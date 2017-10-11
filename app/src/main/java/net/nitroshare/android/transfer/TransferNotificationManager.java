package net.nitroshare.android.transfer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.SparseArray;
import android.view.ViewGroup;
import android.widget.RemoteViews;

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

    private static final String TAG = "TransferNotificationMgr";

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
     * Indicate that the server is listening for transfers
     */
    synchronized void startListening() {
        mListening = true;
        updateNotification();
    }

    /**
     * Indicate that the server is no longer listening for transfers
     */
    synchronized void stopListening() {
        mListening = false;
        stop();
    }

    /**
     * Attempt to stop the service if there is no activity
     * @return true if the service was stopped
     */
    synchronized boolean stop() {
        if (!mListening && mStatuses.size() == 0) {
            Log.d(TAG, "no more activity; shutting down service...");

            mActive = false;
            mService.stopSelf();
            return true;
        }
        updateNotification();
        return false;
    }

    /**
     * Update the notification to reflect the new status of a transfer
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

            // Remove the transfer from the SparseArray
            mStatuses.remove(transferStatus.getId());

            // Terminate here if nothing else is running
            if (stop()) {
                return;
            }

        } else {
            mStatuses.put(transferStatus.getId(), transferStatus);
        }

        updateNotification();
    }

    /**
     * Create the notification
     */
    private void updateNotification() {

        // Create the parent remote view
        RemoteViews parentView = new RemoteViews(mService.getPackageName(), R.layout.notification);

        if (mStatuses.size() == 0) {

            // Hide the layout
            parentView.setViewVisibility(R.id.notification_layout, ViewGroup.GONE);

        } else {

            // Hide the service text
            parentView.setViewVisibility(R.id.notification_none, ViewGroup.GONE);

            // Create a remote view for each transfer in progress
            for (int i = 0; i < mStatuses.size(); i++) {

                TransferStatus transferStatus = mStatuses.valueAt(i);

                // Create the stop intent for the transfer
                PendingIntent stopIntent = PendingIntent.getService(
                        mService,
                        0,
                        new Intent(mService, TransferService.class)
                                .setAction(TransferService.ACTION_STOP_TRANSFER)
                                .putExtra(TransferService.EXTRA_TRANSFER, transferStatus.getId()),
                        0
                );

                // Create the view
                RemoteViews childView = new RemoteViews(mService.getPackageName(), R.layout.notification_item);
                childView.setImageViewResource(R.id.notification_item_icon,
                        transferStatus.getDirection() == TransferStatus.Direction.Send ?
                                R.drawable.send : R.drawable.receive
                );
                childView.setTextViewText(R.id.notification_item_remote_name,
                        transferStatus.getRemoteDeviceName());
                childView.setTextViewText(R.id.notification_item_progress,
                        String.format("%d%%", transferStatus.getProgress()));
                childView.setOnClickPendingIntent(R.id.notification_item_stop, stopIntent);

                // Add the view to the parent
                parentView.addView(R.id.notification_layout, childView);
            }
        }

        // Update the notification
        mBuilder.setContent(parentView);

        // If the notification was not active, start the service in the
        // foreground; otherwise, update the existing notification
        if (!mActive) {
            mService.startForeground(NOTIFICATION_ID, mBuilder.build());
            mActive = true;
        } else {
            mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
        }
    }
}