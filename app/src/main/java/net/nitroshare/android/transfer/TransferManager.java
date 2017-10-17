package net.nitroshare.android.transfer;

import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.util.Log;
import android.util.SparseArray;

import net.nitroshare.android.bundle.FileItem;
import net.nitroshare.android.bundle.Item;
import net.nitroshare.android.bundle.UrlItem;

import java.io.IOException;

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

        // Grab the initial status
        TransferStatus transferStatus = transfer.getStatus();

        Log.i(TAG, String.format("starting transfer #%d...", transferStatus.getId()));

        // Add a listener for status change events
        transfer.addStatusChangedListener(new Transfer.StatusChangedListener() {
            @Override
            public void onStatusChanged(TransferStatus transferStatus) {

                // Broadcast transfer status
                broadcastTransferStatus(transferStatus);

                // Update the transfer notification manager
                mTransferNotificationManager.updateTransfer(transferStatus, intent);
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
                } else if (item instanceof UrlItem) {
                    try {
                        mTransferNotificationManager.showUrl(((UrlItem) item).getUrl());
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            }
        });

        // Add the transfer to the list
        synchronized (mTransfers) {
            mTransfers.append(transferStatus.getId(), transfer);
        }

        // Add the transfer to the notification manager and immediately update it
        mTransferNotificationManager.addTransfer(transferStatus);
        mTransferNotificationManager.updateTransfer(transferStatus, intent);

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
