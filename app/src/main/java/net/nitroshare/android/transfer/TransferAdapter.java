package net.nitroshare.android.transfer;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

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
    private Handler mHandler = new Handler();

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
     * Force an update of the list
     */
    private void update() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyDataSetInvalidated();
            }
        });
    }

    /**
     * Add a transfer to the adapter
     */
    void addTransfer(final Transfer transfer, final Intent intent) {

        // Determine if the transfer should use an existing ID or generate a
        // new unique ID
        int nextId = 0;
        if (intent != null) {
            nextId = intent.getIntExtra(TransferService.EXTRA_ID, 0);
        }
        if (nextId == 0) {
            nextId = mNextId.getAndIncrement();
        }

        final int id = nextId;

        // Update the device name when known
        transfer.addHeaderListener(new Transfer.HeaderListener() {
            @Override
            public void onHeader() {
                update();
            }
        });

        // Update progress when it changes
        transfer.addProgressListener(new Transfer.ProgressListener() {
            @Override
            public void onProgress() {
                update();
            }
        });

        // Show a notification when an error occurs
        transfer.addErrorListener(new Transfer.ErrorListener() {
            @Override
            public void onError() {
                Log.i(TAG, String.format("transfer #%d failed: %s", id, transfer.getError()));

                // Generate a new ID and ensure the next transfer uses it
                int newId = mNextId.getAndIncrement();

                // Generate the text for the error notification
                String contentText = mContext.getString(
                        R.string.service_transfer_status_error,
                        transfer.getRemoteDeviceName(),
                        transfer.getError()
                );

                // Generate the notification
                NotificationCompat.Builder builder = createNotification(
                        transfer.getDirection(), contentText);

                // For sending transfers, offer a retry option
                if (transfer.getDirection() == Transfer.Direction.Send) {
                    //noinspection ConstantConditions
                    intent.putExtra(TransferService.EXTRA_ID, id);
                    builder.addAction(
                            R.drawable.ic_action_retry,
                            mContext.getString(R.string.service_transfer_action_retry),
                            PendingIntent.getService(mContext, 0, intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT)
                    );
                }

                // Show the notification
                mTransferNotificationManager.update(newId, builder.build());
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
                update();
            }
        });

        // Add the new transfer (in the main thread)
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                add(new TransferWrapper(
                        id,
                        mContext,
                        transfer,
                        mTransferNotificationManager
                ));
            }
        });
    }

    void stopTransfer(int id) {
        for (int i = 0; i < getCount(); i++) {
            TransferWrapper transferWrapper = getItem(i);
            //noinspection ConstantConditions
            if (transferWrapper.getId() == id) {
                transferWrapper.getTransfer().stop();
            }
        }
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        convertView = super.getView(position, convertView, parent);

        //noinspection ConstantConditions
        Transfer transfer = getItem(position).getTransfer();

        // Retrieve information about the transfer
        Transfer.Direction direction = transfer.getDirection();
        String remoteDeviceName = transfer.getRemoteDeviceName();
        Transfer.State state = transfer.getState();
        int progress = transfer.getProgress();
        String error = transfer.getError();

        // Populate the views with the data
        ((ImageView) convertView.findViewById(R.id.transfer_icon)).setImageResource(
                direction == Transfer.Direction.Receive ?
                        R.drawable.stat_download : R.drawable.stat_upload
        );
        ((TextView) convertView.findViewById(R.id.transfer_device)).setText(
                remoteDeviceName);
        ProgressBar progressBar = (ProgressBar) convertView.findViewById(R.id.transfer_progress);
        progressBar.setIndeterminate(state == Transfer.State.Connecting);
        progressBar.setProgress(progress);

        // Determine the correct color for the state text view
        TextView stateTextView = (TextView) convertView.findViewById(R.id.transfer_state);
        int color = ContextCompat.getColor(mContext, android.R.color.primary_text_dark);
        switch (state) {
            case Failed:
                color = ContextCompat.getColor(mContext, R.color.colorError);
                break;
            case Succeeded:
                color = ContextCompat.getColor(mContext, R.color.colorSuccess);
                break;
        }
        stateTextView.setTextColor(color);
        switch(state) {
            case Connecting:
                stateTextView.setText(R.string.adapter_transfer_connecting);
                break;
            case Transferring:
                stateTextView.setText(mContext.getString(
                        R.string.adapter_transfer_transferring, transfer.getProgress()
                ));
                break;
            case Failed:
                stateTextView.setText(mContext.getString(
                        R.string.adapter_transfer_failed, error
                ));
                break;
            case Succeeded:
                stateTextView.setText(R.string.adapter_transfer_succeeded);
                break;
        }

        // Show the action button (if possible)
        Button imageButton = (Button) convertView.findViewById(R.id.transfer_action);
        if (state == Transfer.State.Connecting || state == Transfer.State.Transferring) {
            imageButton.setVisibility(View.VISIBLE);
            imageButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_stop, 0, 0, 0);
        } else if (state == Transfer.State.Failed && direction == Transfer.Direction.Send) {
            imageButton.setVisibility(View.VISIBLE);
            imageButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_action_retry, 0, 0, 0);
        } else {
            imageButton.setVisibility(View.GONE);
        }

        return convertView;
    }
}
