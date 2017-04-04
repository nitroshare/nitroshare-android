package net.nitroshare.android.transfer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.SparseArray;

import net.nitroshare.android.R;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manage notifications and service lifecycle
 *
 * This class simplifies what would otherwise be a very messy process.
 * Android requires that a service running in the foreground show a
 * persistent notification. This service runs in the foreground when a
 * transfer is in progress and/or when listening for new connections. This
 * means that startForeground() will be invoked multiple times.
 *
 * Herein lies the problem - stopForeground() will pull only the most
 * recent notification. And it isn't reference counted. That's where this
 * (ugly hack) class comes in.
 *
 * All methods in the class are thread-safe.
 */
class TransferNotificationManager {

    private final SparseArray<Notification> mNotifications = new SparseArray<>();
    private Service mService;
    private SharedPreferences mSharedPreferences;
    private NotificationManager mNotificationManager;
    private AtomicInteger mNextId = new AtomicInteger(2);

    /**
     * Create a notification manager for the specified service
     * @param service service to manage
     */
    TransferNotificationManager(Service service) {
        mService = service;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(service);
        mNotificationManager = (NotificationManager) mService.getSystemService(
                Service.NOTIFICATION_SERVICE);
    }

    /**
     * Start showing a notification in the foreground
     * @param notificationId unique ID for the notification
     * @param notification notification to be shown
     */
    void start(int notificationId, Notification notification) {
        synchronized (mNotifications) {
            mService.startForeground(notificationId, notification);
            mNotifications.append(notificationId, notification);
        }
    }

    /**
     * Update the specified notification
     * @param notificationId unique ID for the notification
     * @param notification notification to be updated
     */
    void update(int notificationId, Notification notification) {
        mNotificationManager.notify(notificationId, notification);
    }

    /**
     * Stop showing a notification and exit foreground if no others are left
     * @param notificationId unique ID for the notification
     *
     * If no notifications remain, the service is stopped. Otherwise, the next
     * most recent notification is then set for the foreground.
     */
    void stop(int notificationId) {
        synchronized (mNotifications) {
            mNotifications.remove(notificationId);
            if (mNotifications.size() == 0) {
                mService.stopSelf();
            } else {
                mService.startForeground(
                        mNotifications.keyAt(mNotifications.size() - 1),
                        mNotifications.valueAt(mNotifications.size() - 1)
                );
                mNotificationManager.cancel(notificationId);
            }
        }
    }

    /**
     * Stop the service if no notifications are being shown
     */
    void stop() {
        synchronized (mNotifications) {
            if (mNotifications.size() == 0) {
                mService.stopSelf();
            }
        }
    }

    /**
     * Show a notification with the specified information
     * @param id unique identifier for the notification
     * @param contentText text to display in the notification
     * @param icon icon to use for the notification
     * @param actions list of actions or null for none
     */
    void show(int id, CharSequence contentText, int icon, NotificationCompat.Action actions[]) {
        boolean notificationSound = mSharedPreferences.getBoolean(
                mService.getString(R.string.setting_notification_sound), false
        );
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mService)
                .setDefaults(notificationSound ? NotificationCompat.DEFAULT_ALL : 0)
                .setContentTitle(mService.getString(R.string.service_transfer_title))
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setSmallIcon(icon);
        if (actions != null) {
            for (NotificationCompat.Action action : actions) {
                builder.addAction(action);
            }
        }
        update(id, builder.build());
    }

    /**
     * Retrieve the next unique integer ID
     */
    int nextId() {
        return mNextId.getAndIncrement();
    }
}