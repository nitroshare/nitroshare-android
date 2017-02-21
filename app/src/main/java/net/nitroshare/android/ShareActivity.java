package net.nitroshare.android;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ListView;

import net.nitroshare.android.discovery.DiscoveryAdapter;

/**
 * Display a list of devices available for receiving a transfer
 *
 * mDNS (multicast DNS) is used to find other peers capable of receiving the transfer. Once a
 * device is selected, the transfer service is provided with the device information and the file.
 */
public class ShareActivity extends Activity {

    private DiscoveryAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

        mAdapter = new DiscoveryAdapter(this);
        ((ListView) findViewById(R.id.selectList)).setAdapter(mAdapter);
        mAdapter.start();
    }

    @Override
    protected void onDestroy() {
        mAdapter.stop();
    }
}
