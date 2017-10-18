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
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

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

    RecyclerView mRecyclerView;
    TextView mTextView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // Create layout parameters for full expansion
        ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        // Create a container
        ViewGroup parentView = new LinearLayout(getContext());
        parentView.setLayoutParams(layoutParams);

        // Setup the adapter and recycler view
        final TransferAdapter adapter = new TransferAdapter(getContext());
        mRecyclerView = new RecyclerView(getContext());
        mRecyclerView.setAdapter(adapter);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerView.setLayoutParams(layoutParams);
        mRecyclerView.setVisibility(View.GONE);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));
        parentView.addView(mRecyclerView);

        // Setup the empty view
        mTextView = new TextView(getContext());
        mTextView.setGravity(Gravity.CENTER);
        mTextView.setLayoutParams(layoutParams);
        mTextView.setTextColor(mTextView.getTextColors().withAlpha(60));
        mTextView.setText(R.string.activity_transfer_empty_text);
        parentView.addView(mTextView);

        // Hide the FAB when the user scrolls
        final FloatingActionButton fab = getActivity().findViewById(R.id.fab);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
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

                        // If none remain, reshow the empty text
                        if (adapter.getItemCount() == 0) {
                            mRecyclerView.setVisibility(View.GONE);
                            mTextView.setVisibility(View.VISIBLE);
                        }

                        // Remove the item from the service
                        Intent removeIntent = new Intent(getContext(), TransferService.class)
                                .setAction(TransferService.ACTION_REMOVE_TRANSFER)
                                .putExtra(TransferService.EXTRA_TRANSFER, transferStatus.getId());
                        getContext().startService(removeIntent);
                    }
                }
        ).attachToRecyclerView(mRecyclerView);

        // Disable change animations (because they are really, really ugly)
        ((DefaultItemAnimator) mRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        // Setup the broadcast receiver
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                TransferStatus transferStatus = intent.getParcelableExtra(TransferManager.EXTRA_STATUS);
                adapter.update(transferStatus);

                if (adapter.getItemCount() == 1) {
                    mRecyclerView.setVisibility(View.VISIBLE);
                    mTextView.setVisibility(View.GONE);
                }
            }
        };

        return parentView;
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