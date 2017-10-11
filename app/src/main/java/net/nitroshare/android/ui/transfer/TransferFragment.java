package net.nitroshare.android.ui.transfer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.nitroshare.android.transfer.TransferManager;
import net.nitroshare.android.transfer.TransferService;
import net.nitroshare.android.transfer.TransferStatus;

/**
 * Fragment that displays a single RecyclerView
 */
public class TransferFragment extends Fragment {

    private TransferAdapter mAdapter;
    private RecyclerView mRecyclerView;

    private BroadcastReceiver mBroadcastReceiver;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Setup the adapter and recycler view
        mAdapter = new TransferAdapter();
        mRecyclerView = new RecyclerView(getContext());
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));

        // Enable swipe-to-dismiss
        new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START | ItemTouchHelper.END) {
                    @Override
                    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
                        // TODO: handle swipe
                    }
                }
        ).attachToRecyclerView(mRecyclerView);

        // Setup the broadcast receiver
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                TransferStatus transferStatus = intent.getParcelableExtra(TransferManager.EXTRA_STATUS);
                mAdapter.update(transferStatus);
            }
        };

        return mRecyclerView;
    }

    @Override
    public void onStart() {
        super.onStart();

        // Start listening for broadcasts
        getContext().registerReceiver(mBroadcastReceiver,
                new IntentFilter(TransferManager.TRANSFER_UPDATED));

        // Get fresh data from the service
        Intent broadcastIntent = new Intent(getContext(), TransferService.class)
                .setAction(TransferService.ACTION_BROADCAST);
        getContext().startService(broadcastIntent);
    }

    @Override
    public void onStop() {
        super.onStop();

        // Stop listening for broadcasts
        getContext().unregisterReceiver(mBroadcastReceiver);
    }
}