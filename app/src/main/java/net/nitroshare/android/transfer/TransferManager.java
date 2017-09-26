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
public class TransferManager {

    private static final String TAG = "TransferManager";

    public static final String TRANSFER_UPDATED = "net.nitroshare.android.TRANSFER_UPDATED";
    public static final String EXTRA_STATUS = "net.nitroshare.android.STATUS";

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
     * @param transferStatus status of the transfer
     * @return notification builder prepared for the transfer
     */
    private NotificationCompat.Builder createNotification(TransferStatus transferStatus) {

        // Intent for stopping this particular service
        Intent stopIntent = new Intent(mContext, TransferService.class)
                .setAction(TransferService.ACTION_STOP_TRANSFER)
                .putExtra(TransferService.EXTRA_TRANSFER, transferStatus.getId());

        // Create the notification
        return new NotificationCompat.Builder(mContext)
                .setContentTitle(mContext.getString(R.string.service_transfer_title))
                .setSmallIcon(transferStatus.getDirection() == TransferStatus.Direction.Receive ?
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
     * Broadcast the status of a transfer
     */
    private void broadcastTransferStatus(TransferStatus transferStatus) {
        Intent intent = new Intent();
        intent.setAction(TRANSFER_UPDATED);
        intent.putExtra(EXTRA_STATUS, transferStatus);
        mContext.sendBroadcast(intent);
    }

    /**
     * Add a transfer to the list
     */
    void addTransfer(final Transfer transfer, final Intent intent) {

        // Create the notification for the transfer
        final NotificationCompat.Builder builder = createNotification(transfer.getStatus());

        // Add a listener for status change events
        transfer.addStatusChangedListener(new Transfer.StatusChangedListener() {
            @Override
            public void onStatusChanged(TransferStatus transferStatus) {
                Log.d(TAG, String.format("transfer #%d status changed", transferStatus.getId()));

                // Broadcast transfer status
                broadcastTransferStatus(transferStatus);

                // Deal with finished transfers
                if (transferStatus.isFinished()) {
                    switch (transferStatus.getState()) {
                        case Succeeded:
                            mTransferNotificationManager.show(
                                    mTransferNotificationManager.nextId(),
                                    mContext.getString(
                                            R.string.service_transfer_status_success,
                                            transferStatus.getRemoteDeviceName()
                                    ),
                                    R.drawable.ic_stat_success,
                                    null
                            );
                            break;
                        case Failed:
                            // If the transfer is retried, the ongoing notification needs
                            // to use the same ID as the error notification so that it
                            // is replaced
                            int newId = mTransferNotificationManager.nextId();

                            NotificationCompat.Action action = null;
                            if (transferStatus.getDirection() == TransferStatus.Direction.Send) {
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
                                            transferStatus.getRemoteDeviceName(),
                                            transferStatus.getError()
                                    ),
                                    R.drawable.ic_stat_error,
                                    action != null ? new NotificationCompat.Action[] {action} : null
                            );
                            break;
                    }

                    // Indicate that the transfer has stopped
                    mTransferNotificationManager.stop(transferStatus.getId());
                    return;
                }

                // Update the notification manager
                switch (transferStatus.getState()) {
                    case Connecting:
                        builder.setContentText(mContext.getString(
                                R.string.service_transfer_status_connecting,
                                transferStatus.getRemoteDeviceName()
                        ));
                        break;
                    case Transferring:
                        builder.setProgress(100, transferStatus.getProgress(), false);
                        builder.setContentText(mContext.getString(
                                transferStatus.getDirection() == TransferStatus.Direction.Send ?
                                        R.string.service_transfer_status_sending :
                                        R.string.service_transfer_status_receiving,
                                transferStatus.getRemoteDeviceName()
                        ));
                        break;
                }
                mTransferNotificationManager.update(transferStatus.getId(), builder.build());
            }
        });

        // Add a listener for items being received
        transfer.addItemReceivedListener(new Transfer.ItemReceivedListener() {
            @Override
            public void onItemReceived(Item item) {
                if (mMediaScannerConnection.isConnected() &&
                        item instanceof FileItem) {
                    String path = ((FileItem) item).getPath();
                    mMediaScannerConnection.scanFile(path, null);
                }
            }
        });

        // Add the transfer to the list
        synchronized (mTransfers) {
            mTransfers.append(transfer.getStatus().getId(), transfer);
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
                Log.i(TAG, String.format("stopping transfer #%d...", transfer.getStatus().getId()));
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
                TransferStatus transferStatus = transfer.getStatus();
                if (!transferStatus.isFinished()) {
                    Log.w(TAG, String.format("cannot remove ongoing transfer #%d",
                            transferStatus.getId()));
                    return;
                }
                mTransfers.remove(id);
            }
        }
    }

    /**
     * Trigger a broadcast of all transfers
     */
    void broadcastTransfers() {
        for (int i = 0; i < mTransfers.size(); i++) {
            broadcastTransferStatus(mTransfers.valueAt(i).getStatus());
        }
    }
}
