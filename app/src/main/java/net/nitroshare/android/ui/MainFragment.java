package net.nitroshare.android.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.View;

import net.nitroshare.android.R;
import net.nitroshare.android.transfer.TransferService;

/**
 * Display list of current transfers
 *
 * Information is exchanged with the TransferWrapper for each transfer by
 * binding to the
 */
public class MainFragment extends ListFragment {

    private static final String TAG = "MainFragment";

    private ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, "connected to transfer service");

            TransferService.TransferBinder binder = (TransferService.TransferBinder) service;
            setListAdapter(binder.getAdapter());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, "disconnected from transfer service");

            setListAdapter(null);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();

        Intent intent = new Intent(getContext(), TransferService.class);
        getContext().bindService(intent, mServiceConnection, 0);
    }

    @Override
    public void onStop() {
        super.onStop();

        getContext().unbindService(mServiceConnection);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setEmptyText(getString(R.string.activity_main_empty_text));
    }
}
