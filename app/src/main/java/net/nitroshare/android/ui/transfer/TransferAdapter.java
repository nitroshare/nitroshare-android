package net.nitroshare.android.ui.transfer;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.nitroshare.android.R;
import net.nitroshare.android.transfer.TransferService;
import net.nitroshare.android.transfer.TransferStatus;
import net.nitroshare.android.ui.TintableButton;
import net.nitroshare.android.util.Settings;

/**
 * Transfer adapter that shows transfers in progress
 */
class TransferAdapter extends RecyclerView.Adapter<TransferAdapter.ViewHolder> {

    /**
     * View holder for individual transfers
     */
    static class ViewHolder extends RecyclerView.ViewHolder {

        private ImageView mIcon;
        private TextView mDevice;
        private TextView mState;
        private ProgressBar mProgress;
        private TextView mBytes;
        private TintableButton mStop;

        ViewHolder(View itemView) {
            super(itemView);

            mIcon = itemView.findViewById(R.id.transfer_icon);
            mDevice = itemView.findViewById(R.id.transfer_device);
            mState = itemView.findViewById(R.id.transfer_state);
            mProgress = itemView.findViewById(R.id.transfer_progress);
            mBytes = itemView.findViewById(R.id.transfer_bytes);
            mStop = itemView.findViewById(R.id.transfer_action);

            // This never changes
            mStop.setIcon(R.drawable.ic_action_stop);
            mStop.setText(R.string.adapter_transfer_stop);
        }
    }

    private Context mContext;
    private Settings mSettings;
    private SparseArray<TransferStatus> mStatuses = new SparseArray<>();

    /**
     * Create a new transfer adapter
     */
    TransferAdapter(Context context) {
        mContext = context;
        mSettings = new Settings(mContext);
    }

    /**
     * Update the information for a transfer in the sparse array
     */
    void update(TransferStatus transferStatus) {
        int index = mStatuses.indexOfKey(transferStatus.getId());
        if (index < 0) {
            mStatuses.put(transferStatus.getId(), transferStatus);
            notifyItemInserted(mStatuses.size());
        } else {
            mStatuses.setValueAt(index, transferStatus);
            notifyItemChanged(index);
        }
    }

    /**
     * Retrieve the status for the specified index
     */
    TransferStatus getStatus(int index) {
        return mStatuses.valueAt(index);
    }

    /**
     * Remove the specified transfer from the sparse array
     */
    void remove(int index) {
        mStatuses.removeAt(index);
        notifyItemRemoved(index);
    }

    @Override
    public TransferAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_transfer_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(TransferAdapter.ViewHolder holder, int position) {
        final TransferStatus transferStatus = mStatuses.valueAt(position);

        // Generate transfer byte string
        CharSequence bytesText;
        if (transferStatus.getBytesTotal() == 0) {
            bytesText = mContext.getString(R.string.adapter_transfer_unknown);
        } else {
            bytesText = mContext.getString(
                    R.string.adapter_transfer_bytes,
                    Formatter.formatShortFileSize(mContext, transferStatus.getBytesTransferred()),
                    Formatter.formatShortFileSize(mContext, transferStatus.getBytesTotal())
            );
        }

        // Set the attributes
        holder.mIcon.setImageResource(R.drawable.stat_download);
        holder.mDevice.setText(transferStatus.getRemoteDeviceName());
        holder.mProgress.setProgress(transferStatus.getProgress());
        holder.mBytes.setText(bytesText);

        // Display the correct state string in the correct style
        switch (transferStatus.getState()) {
            case Connecting:
            case Transferring:
                if (transferStatus.getState() == TransferStatus.State.Connecting) {
                    holder.mState.setText(R.string.adapter_transfer_connecting);
                } else {
                    holder.mState.setText(mContext.getString(R.string.adapter_transfer_transferring,
                            transferStatus.getProgress()));
                }
                holder.mState.setTextColor(ContextCompat.getColor(mContext, android.R.color.darker_gray));
                holder.mStop.setVisibility(View.VISIBLE);
                holder.mStop.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent stopIntent = new Intent(mContext, TransferService.class)
                                .setAction(TransferService.ACTION_STOP_TRANSFER)
                                .putExtra(TransferService.EXTRA_TRANSFER, transferStatus.getId());
                        mContext.startService(stopIntent);
                    }
                });
                break;
            case Succeeded:
                holder.mState.setText(R.string.adapter_transfer_succeeded);
                holder.mState.setTextColor(ContextCompat.getColor(mContext,
                        mSettings.getTheme() == R.style.LightTheme ?
                                R.color.colorSuccess : R.color.colorSuccessDark));
                holder.mStop.setVisibility(View.INVISIBLE);
                break;
            case Failed:
                holder.mState.setText(mContext.getString(R.string.adapter_transfer_failed,
                        transferStatus.getError()));
                holder.mState.setTextColor(ContextCompat.getColor(mContext,
                        mSettings.getTheme() == R.style.LightTheme ?
                                R.color.colorError : R.color.colorErrorDark));
                holder.mStop.setVisibility(View.INVISIBLE);
                break;
        }
    }

    @Override
    public int getItemCount() {
        return mStatuses.size();
    }
}