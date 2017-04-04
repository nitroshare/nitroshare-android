package net.nitroshare.android.transfer;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.util.SparseArray;

import net.nitroshare.android.R;
import net.nitroshare.android.bundle.FileItem;
import net.nitroshare.android.bundle.Item;

/**
 * Manage active transfers
 */
class TransferManager {

    private static final String TAG = "TransferManager";

    private static final String TRANSFER_UPDATED = "net.nitroshare.android.TRANSFER_UPDATED";
    private static final String EXTRA_ID = "net.nitroshare.android.ID";
    private static final String EXTRA_DIRECTION = "net.nitroshare.android.DIRECTION";
    private static final String EXTRA_DEVICE_NAME = "net.nitroshare.android.DEVICE_NAME";
    private static final String EXTRA_STATE = "net.nitroshare.android.STATE";
    private static final String EXTRA_PROGRESS = "net.nitroshare.android.PROGRESS";
    private static final String EXTRA_ERROR = "net.nitroshare.android.ERROR";

    private Context mContext;
    private TransferNotificationManager mTransferNotificationManager;


    private final SparseArray<Transfer> mTransfers = new SparseArray<>();

    private MediaScannerConnection mMediaScannerConnection;

    /**
     * Create a new transfer manager
     */
    TransferManager(Context context, TransferNotificationManager transferNotificationManager) {
        mContext = context;
        mTransferNotificationManager = transferNotificationManager;

        mMediaScannerConnection = new MediaScannerConnection(mContext, new MediaScannerConnection.MediaScannerConnectionClient() {
            @Override
            public void onMediaScannerConnected() {
                Log.i(TAG, "connected to media scanner");
            }

            @Override
            public void onScanCompleted(String path, Uri uri) {
            }
        });
    }

    /**
     * Create the ongoing notification for a transfer
     * @param transfer create notification for this transfer
     * @return notification builder prepared for the transfer
     */
    private NotificationCompat.Builder createNotification(Transfer transfer) {

        // Intent for stopping this particular service
        Intent stopIntent = new Intent(mContext, TransferService.class)
                .setAction(TransferService.ACTION_STOP_TRANSFER)
                .putExtra(TransferService.EXTRA_TRANSFER, transfer.getId());

        // Create the notification
        return new NotificationCompat.Builder(mContext)
                .setContentTitle(mContext.getString(R.string.service_transfer_title))
                .setSmallIcon(transfer.getDirection() == Transfer.Direction.Receive ?
                        android.R.drawable.stat_sys_download :
                        android.R.drawable.stat_sys_upload
                )
                .setProgress(0, 0, true)
                .addAction(new NotificationCompat.Action.Builder(
                        R.drawable.ic_action_stop,
                        mContext.getString(R.string.service_transfer_action_stop),
                        PendingIntent.getService(mContext, 0, stopIntent, 0)
                ).build());
    }

    /**
     * Send a broadcast with the information for the specified transfer
     * @param transfer use this transfer to initialize the broadcast
     */
    private void broadcastUpdate(Transfer transfer) {
        Intent intent = new Intent();
        intent.setAction(TRANSFER_UPDATED);
        intent.putExtra(EXTRA_ID, transfer.getId());
        intent.putExtra(EXTRA_DIRECTION, transfer.getDirection());
        intent.putExtra(EXTRA_DEVICE_NAME, transfer.getRemoteDeviceName());
        intent.putExtra(EXTRA_STATE, transfer.getState());
        intent.putExtra(EXTRA_PROGRESS, transfer.getProgress());
        intent.putExtra(EXTRA_ERROR, transfer.getError());
        mContext.sendBroadcast(intent);
    }

    /**
     * Add a transfer to the list
     */
    void addTransfer(final Transfer transfer, final Intent intent) {

        // Create the notification for the transfer
        final NotificationCompat.Builder builder = createNotification(transfer);

        // Add listeners for all of the events

        transfer.addConnectListener(new Transfer.ConnectListener() {
            @Override
            public void onConnect() {
                Log.i(TAG, String.format("connected to %s", transfer.getRemoteDeviceName()));

                builder.setContentText(
                        mContext.getString(
                                R.string.service_transfer_status_sending,
                                transfer.getRemoteDeviceName()
                        )
                );
                mTransferNotificationManager.update(transfer.getId(), builder.build());
                broadcastUpdate(transfer);
            }
        });

        transfer.addHeaderListener(new Transfer.HeaderListener() {
            @Override
            public void onHeader() {
                Log.i(TAG, String.format("transfer header received from %s",
                        transfer.getRemoteDeviceName()));

                builder.setContentText(
                        mContext.getString(
                                R.string.service_transfer_status_receiving,
                                transfer.getRemoteDeviceName()
                        )
                );
                mTransferNotificationManager.update(transfer.getId(), builder.build());
                broadcastUpdate(transfer);
            }
        });

        transfer.addProgressListener(new Transfer.ProgressListener() {
            @Override
            public void onProgress() {
                builder.setProgress(100, transfer.getProgress(), false);
                mTransferNotificationManager.update(transfer.getId(), builder.build());
                broadcastUpdate(transfer);
            }
        });

        transfer.addItemListener(new Transfer.ItemListener() {
            @Override
            public void onItem(Item item) {
                if (mMediaScannerConnection.isConnected() &&
                        item instanceof FileItem) {
                    String path = ((FileItem) item).getPath();
                    mMediaScannerConnection.scanFile(path, null);
                }
            }
        });

        transfer.addSuccessListener(new Transfer.SuccessListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG, String.format("transfer #%d succeeded", transfer.getId()));

                mTransferNotificationManager.show(
                        mTransferNotificationManager.nextId(),
                        mContext.getString(
                                R.string.service_transfer_status_success,
                                transfer.getRemoteDeviceName()
                        ),
                        R.drawable.ic_stat_success,
                        null
                );
                broadcastUpdate(transfer);
            }
        });

        transfer.addErrorListener(new Transfer.ErrorListener() {
            @Override
            public void onError() {
                Log.e(TAG, String.format("transfer #%d failed: %s",
                        transfer.getId(), transfer.getError()));

                // If the transfer is retried, the ongoing notification needs
                // to use the same ID as the error notification so that it
                // is replaced
                int newId = mTransferNotificationManager.nextId();

                NotificationCompat.Action action = null;
                if (transfer.getDirection() == Transfer.Direction.Send) {
                    intent.putExtra(TransferService.EXTRA_ID, newId);
                    action = new NotificationCompat.Action.Builder(
                            R.drawable.ic_action_retry,
                            mContext.getString(R.string.service_transfer_action_retry),
                            PendingIntent.getService(mContext, 0, intent,
                                    PendingIntent.FLAG_UPDATE_CURRENT)
                    ).build();
                }

                mTransferNotificationManager.show(
                        newId,
                        mContext.getString(
                                R.string.service_transfer_status_error,
                                transfer.getRemoteDeviceName(),
                                transfer.getError()
                        ),
                        R.drawable.ic_stat_error,
                        new NotificationCompat.Action[] {action}
                );
                broadcastUpdate(transfer);
            }
        });

        transfer.addFinishListener(new Transfer.FinishListener() {
            @Override
            public void onFinish() {
                mTransferNotificationManager.stop(transfer.getId());
            }
        });

        // Add the transfer to the list
        synchronized (mTransfers) {
            mTransfers.append(transfer.getId(), transfer);
        }

        // Create a new thread and run the transfer in it
        new Thread(transfer).start();
    }

    /**
     * Stop the transfer with the specified ID
     */
    void stopTransfer(int id) {
        synchronized (mTransfers) {
            Transfer transfer = mTransfers.get(id);
            if (transfer != null) {
                Log.i(TAG, String.format("stopping transfer #%d...", transfer.getId()));
                transfer.stop();
            }
        }
    }

    /**
     * Remove the transfer with the specified ID
     *
     * Transfers that are in progress cannot be removed and a warning is logged
     * if this is attempted.
     */
    void removeTransfer(int id) {
        synchronized (mTransfers) {
            Transfer transfer = mTransfers.get(id);
            if (transfer != null) {
                if (transfer.getState() != Transfer.State.Succeeded &&
                        transfer.getState() != Transfer.State.Failed) {
                    Log.w(TAG, String.format("cannot remove ongoing transfer #%d",
                            transfer.getId()));
                    return;
                }
                mTransfers.remove(id);
            }
        }
    }
}
