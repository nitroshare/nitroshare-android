package net.nitroshare.android.ui.transfer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.view.View;

import com.wdullaer.swipeactionadapter.SwipeActionAdapter;
import com.wdullaer.swipeactionadapter.SwipeDirection;

import net.nitroshare.android.R;
import net.nitroshare.android.transfer.TransferManager;
import net.nitroshare.android.transfer.TransferService;

/**
 * Display list of current transfers
 *
 * Information is exchanged with the TransferWrapper for each transfer by
 * binding to the
 */
public class TransferFragment extends ListFragment {

    private BroadcastReceiver mBroadcastReceiver;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final TransferAdapter transferAdapter = new TransferAdapter(getContext());
        SwipeActionAdapter swipeActionAdapter = new SwipeActionAdapter(transferAdapter);
        swipeActionAdapter.setListView(getListView());
        swipeActionAdapter.setFadeOut(true);
        swipeActionAdapter.setSwipeActionListener(new SwipeActionAdapter.SwipeActionListener() {
            @Override
            public boolean hasActions(int position, SwipeDirection direction) {
                return direction.isLeft() || direction.isRight();
            }

            @Override
            public boolean shouldDismiss(int position, SwipeDirection direction) {
                return true;
            }

            @Override
            public void onSwipe(int[] position, SwipeDirection[] direction) {
                for (int i = 0; i < position.length; i++) {
                    TransferAdapter.TransferData transferData = transferAdapter.getItem(i);
                    transferAdapter.remove(transferData);
                    //noinspection ConstantConditions
                    Intent removeIntent = new Intent(getContext(), TransferService.class)
                            .setAction(TransferService.ACTION_REMOVE_TRANSFER)
                            .putExtra(TransferService.EXTRA_TRANSFER, transferData.mId);
                    getContext().startService(removeIntent);
                }
            }
        });
        setListAdapter(swipeActionAdapter);

        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                transferAdapter.processIntent(intent);
            }
        };
    }

    @Override
    public void onStart() {
        super.onStart();

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

        getContext().unregisterReceiver(mBroadcastReceiver);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setEmptyText(getString(R.string.activity_transfer_empty_text));
    }
}
