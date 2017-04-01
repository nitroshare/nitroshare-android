package net.nitroshare.android.transfer;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import net.nitroshare.android.R;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Adapter for transfers in progress
 *
 * This is made available to activities through the binder interface, allowing
 * them to display transfers in progress and let the user interact with them.
 */
public class TransferAdapter extends ArrayAdapter<TransferWrapper> {

    private static final String TAG = "TransferAdapter";

    private Context mContext;
    private TransferNotificationManager mTransferNotificationManager;
    private SharedPreferences mSharedPreferences;

    private AtomicInteger mNextId = new AtomicInteger(2);

    TransferAdapter(Context context, TransferNotificationManager transferNotificationManager) {
        super(context, R.layout.view_transfer_item, R.id.transfer_device);
        mContext = context;
        mTransferNotificationManager = transferNotificationManager;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    /**
     * Create a notification with the specified text
     */
    private NotificationCompat.Builder createNotification(Transfer.Direction direction, CharSequence contentText) {
        boolean notificationSound = mSharedPreferences.getBoolean(
                mContext.getString(R.string.setting_notification_sound), false
        );
        return new NotificationCompat.Builder(mContext)
                .setDefaults(notificationSound ? NotificationCompat.DEFAULT_ALL : 0)
                .setContentTitle(mContext.getString(R.string.service_transfer_title))
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setSmallIcon(
                        direction == Transfer.Direction.Receive ?
                                android.R.drawable.stat_sys_download_done :
                                android.R.drawable.stat_sys_upload_done
                );
    }

    /**
     * Add a transfer to the adapter
     */
    void addTransfer(final Transfer transfer, final Intent intent) {

        // Determine if the transfer should use an existing ID or generate a
        // new unique ID
        int nextId = intent.getIntExtra(TransferService.EXTRA_ID, 0);
        if (nextId == 0) {
            nextId = mNextId.getAndIncrement();
        }

        final int id = nextId;

        // Show a notification when an error occurs
        transfer.addErrorListener(new Transfer.ErrorListener() {
            @Override
            public void onError(String message) {
                Log.i(TAG, String.format("transfer #%d failed: %s", id, message));

                // Generate a new ID and ensure the next transfer uses it
                int newId = mNextId.getAndIncrement();
                intent.putExtra(TransferService.EXTRA_ID, id);

                // Generate the text for the error notification
                String contentText = mContext.getString(
                        R.string.service_transfer_status_error,
                        transfer.getRemoteDeviceName(),
                        message
                );

                // Show the failure notification
                mTransferNotificationManager.update(
                        newId,
                        createNotification(transfer.getDirection(), contentText)
                                .addAction(
                                        R.drawable.ic_action_retry,
                                        mContext.getString(R.string.service_transfer_action_retry),
                                        PendingIntent.getService(mContext, 0, intent,
                                                PendingIntent.FLAG_UPDATE_CURRENT)
                                )
                                .build()
                );
            }
        });

        // Show a notification if the transfer completes successfully
        transfer.addSuccessListener(new Transfer.SuccessListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, String.format("transfer #%d succeeded", id));

                // Generate the text for the notification
                String contentText = mContext.getString(
                        R.string.service_transfer_status_success,
                        transfer.getRemoteDeviceName()
                );

                // Show the notification
                mTransferNotificationManager.update(
                        mNextId.getAndIncrement(),
                        createNotification(transfer.getDirection(), contentText).build()
                );
            }
        });

        // Deal with completed transfers
        transfer.addFinishListener(new Transfer.FinishListener() {
            @Override
            public void onFinish() {
                mTransferNotificationManager.stop(id);
            }
        });

        // Add the new transfer
        add(new TransferWrapper(
                id,
                mContext,
                transfer,
                mTransferNotificationManager
        ));
    }

    void stopTransfer(int id) {
        for (int i = 0; i < getCount(); i++) {
            TransferWrapper transferWrapper = getItem(i);
            if (transferWrapper.getId() == id) {
                transferWrapper.getTransfer().stop();
            }
        }
    }

    /*
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = super.getView(position, convertView, parent);
        TransferWrapper transferWrapper = getItem(position);
        return convertView;
    }
    */
}
