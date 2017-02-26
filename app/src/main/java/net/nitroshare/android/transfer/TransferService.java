package net.nitroshare.android.transfer;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import net.nitroshare.android.R;
import net.nitroshare.android.bundle.Bundle;
import net.nitroshare.android.bundle.FileItem;
import net.nitroshare.android.discovery.Device;

import java.util.concurrent.atomic.AtomicInteger;

import static android.R.attr.port;

public class TransferService extends Service {

    private static final String TAG = "TransferService";

    public static final String ACTION_INITIATE_TRANSFER = "initiate_transfer";
    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_URLS = "urls";
    public static final String EXTRA_FILENAMES = "filenames";

    private int mNotificationId = 0;
    private final AtomicInteger mNumActiveTransfers = new AtomicInteger(0);

    /**
     * Create a notification to show status of an ongoing transfer
     * @param device remote device used for the transfer
     * @return newly created Notification.Builder
     *
     * The notification initially displays the "connecting..." message and
     * should be shown with startForeground().
     */
    private Notification.Builder buildSendTransferNotification(Device device) {
        return new Notification.Builder(this)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setContentTitle(getText(R.string.transfer_title))
                .setContentText(getText(R.string.transfer_status_connecting))
                .setSmallIcon(R.drawable.ic_stat_transfer)
                .setProgress(0, 0, true);
    }

    /**
     * Create a notification indicating a transfer has succeeded
     * @param device remote device used for the transfer
     */
    private void showSuccessNotification(Device device) {
        String contentText = String.format(
                getString(R.string.transfer_status_success),
                device.getName()
        );
        new Notification.Builder(this)
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentTitle(getText(R.string.transfer_title))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_stat_success)
                .build()
                .notify();
    }

    // TODO: error notification should include an action to retry the transfer
    //   this is simpler than it sounds since we can just resend the original
    //   intent that started the transfer

    /**
     * Create a notification indicating that a transfer failed
     * @param device remote device used for the transfer
     * @param errorMessage description of the error that occurred
     */
    private void showErrorNotification(Device device, String errorMessage) {
        String contentText = String.format(
                getString(R.string.transfer_status_error),
                device.getName(),
                errorMessage
        );
        new Notification.Builder(this)
                .setCategory(Notification.CATEGORY_ERROR)
                .setContentTitle(getText(R.string.transfer_title))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_stat_error)
                .build()
                .notify();
    }

    // TODO: it makes more sense to create the bundle and pass it to the intent
    //   however, the bundle would need to be serializable, which is not
    //   possible currently, given the way it is implemented

    /**
     * Create a bundle from the provided list of URLs and files
     * @param urls list of URLs
     * @param filenames list of filenames
     * @return newly created bundle
     */
    private Bundle createBundle(String[] urls, String[] filenames) {
        Bundle bundle = new Bundle();
        for (String url : urls) {
            // TODO: add URL
        }
        for (String filename : filenames) {
            bundle.add(new FileItem(filename));
        }
        return bundle;
    }

    /**
     * Start a transfer using the provided intent
     * @param intent
     */
    private void startTransfer(Intent intent) {

        // Retrieve the parameters from the intent
        final Device device = (Device) intent.getSerializableExtra(EXTRA_DEVICE);
        String[] urls = intent.getStringArrayExtra(EXTRA_URLS);
        String[] filenames = intent.getStringArrayExtra(EXTRA_FILENAMES);

        // Create the bundle and persistent notification
        Bundle bundle = createBundle(urls, filenames);
        final Notification.Builder builder = buildSendTransferNotification(device);

        // Generate a unique ID for the transfer and show the notification
        final int transferId = mNotificationId++;
        startForeground(transferId, builder.build());

        Log.i(TAG, String.format("starting transfer %d (%d items to %s)",
                transferId, bundle.size(), device.getName()));

        final Transfer transfer = new Transfer(
                device,
                bundle,
                new Transfer.Listener() {
                    @Override
                    public void onDeviceName(String name) {}

                    @Override
                    public void onProgress(int progress) {
                        builder.setContentText(getText(R.string.transfer_status_transferring));
                        builder.setProgress(100, progress, false);
                        startForeground(transferId, builder.build());
                    }

                    @Override
                    public void onSuccess() {
                        Log.e(TAG, String.format("transfer %d succeeded", transferId));
                        showSuccessNotification(device);
                    }

                    @Override
                    public void onError(String message) {
                        Log.e(TAG, String.format("transfer %d failed: %s",
                                transferId, message));
                        showErrorNotification(device, message);
                    }

                    @Override
                    public void onFinish() {
                        if (mNumActiveTransfers.decrementAndGet() == 0) {
                            stopForeground(false);
                            stopSelf();
                        }
                    }
                }
        );

        // Increment the number of active transfers (no way to avoid the get??)
        mNumActiveTransfers.incrementAndGet();

        // Run in a new thread
        Thread thread = new Thread() {
            @Override
            public void run() {
                transfer.run();
            }
        };
        thread.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        switch (intent.getAction()) {
            case ACTION_INITIATE_TRANSFER:
                startTransfer(intent);
                break;
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
