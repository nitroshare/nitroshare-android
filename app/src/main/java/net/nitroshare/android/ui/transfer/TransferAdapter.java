package net.nitroshare.android.ui.transfer;

import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import net.nitroshare.android.R;
import net.nitroshare.android.transfer.TransferStatus;
import net.nitroshare.android.ui.TintableButton;

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
        }
    }

    private SparseArray<TransferStatus> mStatuses = new SparseArray<>();

    /**
     * Update the information for a transfer in the sparse array
     */
    void update(TransferStatus transferStatus) {
        mStatuses.put(transferStatus.getId(), transferStatus);
    }

    /**
     * Remove the specified transfer from the sparse array
     */
    void remove(int id) {
        //...
    }

    @Override
    public TransferAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_transfer_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(TransferAdapter.ViewHolder holder, int position) {
        holder.mIcon.setImageResource(R.drawable.stat_download);
        holder.mDevice.setText("My Device");
        holder.mState.setText("Doing nothing");
        holder.mProgress.setProgress(25);
        holder.mBytes.setText("12 MB / 48 MB");
        holder.mStop.setIcon(R.drawable.ic_action_stop);
        holder.mStop.setText(R.string.adapter_transfer_stop);
    }

    @Override
    public int getItemCount() {
        //return mStatuses.size();
        return 8;
    }
}