package net.nitroshare.android.transfer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.util.SparseArray;

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
    private NotificationManager mNotificationManager;

    /**
     * Create a notification manager for the specified service
     * @param service service to manage
     */
    TransferNotificationManager(Service service) {
        mService = service;
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
}