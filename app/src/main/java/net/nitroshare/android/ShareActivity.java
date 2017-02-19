package net.nitroshare.android;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ListView;

import net.nitroshare.android.discovery.DeviceAdapter;

/**
 * Display a list of devices available for receiving a transfer
 *
 * mDNS (multicast DNS) is used to find other peers capable of receiving the transfer. Once a
 * device is selected, the transfer service is provided with the device information and the file.
 */
public class ShareActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);
        ((ListView)findViewById(R.id.selectList)).setAdapter(new DeviceAdapter(this));
    }
}
