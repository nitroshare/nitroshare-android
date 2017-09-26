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
import net.nitroshare.android.transfer.TransferManager;
import net.nitroshare.android.transfer.TransferService;
import net.nitroshare.android.transfer.TransferStatus;
import net.nitroshare.android.ui.TintableButton;
import net.nitroshare.android.util.Settings;

/**
 * Adapter for transfers that are in progress
 */
class TransferAdapter extends ArrayAdapter<TransferStatus> {

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
        TransferStatus transferStatus = (TransferStatus) intent.getParcelableExtra(TransferManager.EXTRA_STATUS);
        if (transferStatus.getId() != 0) {
            for (int i = 0; i < getCount(); i++) {
                //noinspection ConstantConditions
                if (transferStatus.getId() == getItem(i).getId()) {
                    remove(getItem(i));
                    insert(transferStatus, i);
                    return;
                }
            }
        }
        insert(transferStatus, 0);
    }

    @NonNull
    @Override
    @SuppressWarnings("ConstantConditions")
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        convertView = super.getView(position, convertView, parent);

        // Retrieve the underlying transfer data
        final TransferStatus transferStatus = getItem(position);

        // Set the icon, device name, and progress
        ((ImageView) convertView.findViewById(R.id.transfer_icon)).setImageResource(
                transferStatus.getDirection() == TransferStatus.Direction.Receive ?
                        R.drawable.stat_download : R.drawable.stat_upload);
        ((TextView) convertView.findViewById(R.id.transfer_device)).setText(
                transferStatus.getRemoteDeviceName());
        ((ProgressBar) convertView.findViewById(R.id.transfer_progress))
                .setProgress(transferStatus.getProgress());

        // Find the other controls
        TextView stateTextView = (TextView) convertView.findViewById(R.id.transfer_state);
        TintableButton actionButton = (TintableButton) convertView.findViewById(R.id.transfer_action);

        // Set the appropriate attributes
        switch (transferStatus.getState()) {
            case Connecting:
            case Transferring:
                if (transferStatus.getState() == TransferStatus.State.Connecting) {
                    stateTextView.setText(R.string.adapter_transfer_connecting);
                } else {
                    stateTextView.setText(getContext().getString(
                            R.string.adapter_transfer_transferring,
                            transferStatus.getProgress()));
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
                                .putExtra(TransferService.EXTRA_TRANSFER, transferStatus.getId());
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
                        R.string.adapter_transfer_failed, transferStatus.getError()));
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
