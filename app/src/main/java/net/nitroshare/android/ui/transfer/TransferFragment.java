package net.nitroshare.android.ui.transfer;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.View;

import net.nitroshare.android.R;

/**
 * Display list of current transfers
 *
 * Information is exchanged with the TransferWrapper for each transfer by
 * binding to the
 */
public class TransferFragment extends ListFragment {

    private static final String TAG = "TransferFragment";

    /*
    TransferService.TransferBinder binder = (TransferService.TransferBinder) service;
    final TransferAdapter transferAdapter = binder.getAdapter();

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
                transferAdapter.remove(transferAdapter.getItem(i));
            }
        }
    });
    setListAdapter(swipeActionAdapter);
    */

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setEmptyText(getString(R.string.activity_main_empty_text));
    }
}
