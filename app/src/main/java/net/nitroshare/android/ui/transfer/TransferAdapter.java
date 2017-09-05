package net.nitroshare.android.ui.transfer;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.nitroshare.android.R;
import net.nitroshare.android.transfer.Transfer;
import net.nitroshare.android.transfer.TransferManager;
import net.nitroshare.android.transfer.TransferService;
import net.nitroshare.android.ui.TintableButton;
import net.nitroshare.android.util.Settings;

/**
 * Adapter for transfers that are in progress
 */
class TransferAdapter extends ArrayAdapter<TransferAdapter.TransferData> {

    /**
     * Data for a specific transfer
     */
    class TransferData {

        int mId;
        Transfer.Direction mDirection;
        String mDeviceName;
        Transfer.State mState;
        int mProgress;
        String mError;

        /**
         * Create an instance from the data in an intent
         */
        TransferData(Intent intent) {
            mId = intent.getIntExtra(TransferManager.EXTRA_ID, 0);
            mDirection = (Transfer.Direction) intent.getSerializableExtra(TransferManager.EXTRA_DIRECTION);
            mDeviceName = intent.getStringExtra(TransferManager.EXTRA_DEVICE_NAME);
            mState = (Transfer.State) intent.getSerializableExtra(TransferManager.EXTRA_STATE);
            mProgress = intent.getIntExtra(TransferManager.EXTRA_PROGRESS, 0);
            mError = intent.getStringExtra(TransferManager.EXTRA_ERROR);
        }
    }

    private Settings mSettings;

    /**
     * Create a new transfer adapter
     */
    TransferAdapter(Context context) {
        super(context, R.layout.view_transfer_item, R.id.transfer_device);
        mSettings = new Settings(context);
    }

    /**
     * Process an intent
     *
     * If a transfer with the ID already exists, it is replaced. If not, the
     * new transfer is inserted at the front of the list.
     */
    void processIntent(Intent intent) {
        TransferData transferData = new TransferData(intent);
        if (transferData.mId != 0) {
            for (int i = 0; i < getCount(); i++) {
                //noinspection ConstantConditions
                if (transferData.mId == getItem(i).mId) {
                    remove(getItem(i));
                    insert(transferData, i);
                    return;
                }
            }
        }
        insert(transferData, 0);
    }

    @NonNull
    @Override
    @SuppressWarnings("ConstantConditions")
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        convertView = super.getView(position, convertView, parent);

        // Retrieve the underlying transfer data
        final TransferData transferData = getItem(position);

        // Set the icon, device name, and progress
        ((ImageView) convertView.findViewById(R.id.transfer_icon)).setImageResource(
                transferData.mDirection == Transfer.Direction.Receive ?
                        R.drawable.stat_download : R.drawable.stat_upload);
        ((TextView) convertView.findViewById(R.id.transfer_device)).setText(
                transferData.mDeviceName);
        ((ProgressBar) convertView.findViewById(R.id.transfer_progress))
                .setProgress(transferData.mProgress);

        // Find the other controls
        TextView stateTextView = (TextView) convertView.findViewById(R.id.transfer_state);
        TintableButton actionButton = (TintableButton) convertView.findViewById(R.id.transfer_action);

        // Set the appropriate attributes
        switch (transferData.mState) {
            case Connecting:
            case Transferring:
                if (transferData.mState == Transfer.State.Connecting) {
                    stateTextView.setText(R.string.adapter_transfer_connecting);
                } else {
                    stateTextView.setText(getContext().getString(
                            R.string.adapter_transfer_transferring,
                            transferData.mProgress));
                }
                stateTextView.setTextColor(ContextCompat.getColor(getContext(),
                        android.R.color.darker_gray));
                actionButton.setVisibility(View.VISIBLE);
                actionButton.setIcon(R.drawable.ic_action_stop);
                actionButton.setText(R.string.adapter_transfer_stop);
                actionButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent stopIntent = new Intent(getContext(), TransferService.class)
                                .setAction(TransferService.ACTION_STOP_TRANSFER)
                                .putExtra(TransferService.EXTRA_TRANSFER, transferData.mId);
                        getContext().startService(stopIntent);
                    }
                });
                break;
            case Succeeded:
                stateTextView.setText(R.string.adapter_transfer_succeeded);
                stateTextView.setTextColor(ContextCompat.getColor(getContext(),
                        mSettings.getTheme() == R.style.LightTheme ?
                                R.color.colorSuccess : R.color.colorSuccessDark));
                actionButton.setVisibility(View.GONE);
                break;
            case Failed:
                stateTextView.setText(getContext().getString(
                        R.string.adapter_transfer_failed, transferData.mError));
                stateTextView.setTextColor(ContextCompat.getColor(getContext(),
                        mSettings.getTheme() == R.style.LightTheme ?
                                R.color.colorError : R.color.colorErrorDark));
                /*
                if (transferData.mDirection == Transfer.Direction.Send) {
                    actionButton.setVisibility(View.VISIBLE);
                    actionButton.setIcon(R.drawable.ic_action_retry);
                    actionButton.setText(R.string.adapter_transfer_retry);
                    actionButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // TODO: retry transfer
                        }
                    });
                } else {
                */
                    actionButton.setVisibility(View.GONE);
                /*
                }
                */
                break;
        }

        return convertView;
    }
}
