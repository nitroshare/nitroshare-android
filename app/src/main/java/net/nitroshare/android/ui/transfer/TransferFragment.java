package net.nitroshare.android.ui.transfer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.nitroshare.android.R;
import net.nitroshare.android.transfer.TransferManager;
import net.nitroshare.android.transfer.TransferService;
import net.nitroshare.android.transfer.TransferStatus;

/**
 * Fragment that displays a single RecyclerView
 */
public class TransferFragment extends Fragment {

    private static final String TAG = "TransferFragment";

    private BroadcastReceiver mBroadcastReceiver;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Setup the adapter and recycler view
        final TransferAdapter adapter = new TransferAdapter(getContext());
        RecyclerView recyclerView = new RecyclerView(getContext());
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));

        // Hide the FAB when the user scrolls
        final FloatingActionButton fab = getActivity().findViewById(R.id.fab);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) {
                    fab.hide();
                } else {
                    fab.show();
                }
            }
        });

        // Enable swipe-to-dismiss
        new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.START | ItemTouchHelper.END) {
                    @Override
                    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

                        // Calculate the position of the item and retrieve its status
                        int position = viewHolder.getAdapterPosition();
                        TransferStatus transferStatus = adapter.getStatus(position);

                        // Remove the item from the adapter
                        adapter.remove(position);

                        // Remove the item from the service
                        Intent removeIntent = new Intent(getContext(), TransferService.class)
                                .setAction(TransferService.ACTION_REMOVE_TRANSFER)
                                .putExtra(TransferService.EXTRA_TRANSFER, transferStatus.getId());
                        getContext().startService(removeIntent);
                    }
                }
        ).attachToRecyclerView(recyclerView);

        // Disable change animations (because they are really, really ugly)
        ((DefaultItemAnimator) recyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        // Setup the broadcast receiver
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                TransferStatus transferStatus = intent.getParcelableExtra(TransferManager.EXTRA_STATUS);
                adapter.update(transferStatus);
            }
        };

        return recyclerView;
    }

    @Override
    public void onStart() {
        super.onStart();

        Log.i(TAG, "registering broadcast receiver");

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

        Log.i(TAG, "unregistering broadcast receiver");

        // Stop listening for broadcasts
        getContext().unregisterReceiver(mBroadcastReceiver);
    }
}