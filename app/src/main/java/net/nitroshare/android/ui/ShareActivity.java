package net.nitroshare.android.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.youview.tinydnssd.DiscoverResolver;
import com.youview.tinydnssd.MDNSDiscover;

import net.nitroshare.android.R;
import net.nitroshare.android.discovery.Device;
import net.nitroshare.android.transfer.TransferService;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Display a list of devices available for receiving a transfer
 *
 * mDNS (multicast DNS) is used to find other peers capable of receiving the transfer. Once a
 * device is selected, the transfer service is provided with the device information and the file.
 */
public class ShareActivity extends AppCompatActivity {

    private static final String TAG = "ShareActivity";

    /**
     * Adapter that discovers other devices on the network
     */
    class DeviceAdapter extends ArrayAdapter<String> {

        /**
         * Maintain a mapping of device IDs to discovered devices
         */
        private final Map<String, Device> mDevices = new HashMap<>();

        private DiscoverResolver mDiscoverResolver;
        private String mThisDeviceName;

        DiscoverResolver.Listener mListener = new DiscoverResolver.Listener() {
            @Override
            public void onServicesChanged(Map<String, MDNSDiscover.Result> services) {
                for (final MDNSDiscover.Result result : services.values()) {
                    // We neither need nor want a FQDN
                    final String name = result.srv.fqdn.replaceFirst(
                            String.format("\\.%slocal$", Device.SERVICE_TYPE), "");
                    if (name.equals(mThisDeviceName)) {
                        continue;
                    }
                    if (result.srv.ttl == 0) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                remove(name);
                                mDevices.remove(name);
                            }
                        });
                    }
                    InetAddress ipAddress = null;
                    try {
                        ipAddress = InetAddress.getByName(result.a.ipaddr);
                    } catch (UnknownHostException ignored) {
                    }
                    final InetAddress host = ipAddress;
                    /*
                    final String uuid = result.txt.dict.get(Device.UUID);
                    if (uuid == null) {
                        continue;
                    }
                    */
                    Log.d(TAG, String.format("found new device \"%s\"", name));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Device device = new Device(
                                    "",
                                    name,
                                    host,
                                    result.srv.port
                            );
                            mDevices.put(name, device);
                            add(name);
                        }
                    });
                }
            }
        };

        DeviceAdapter() {
            super(ShareActivity.this, R.layout.view_simple_list_item, android.R.id.text1);

            mDiscoverResolver = new DiscoverResolver(ShareActivity.this, Device.SERVICE_TYPE, mListener);
            SharedPreferences sharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(ShareActivity.this);
            mThisDeviceName = sharedPreferences.getString(getString(
                    R.string.setting_device_name), Build.MODEL);
        }

        void start() {
            mDiscoverResolver.start();
        }

        void stop() {
            mDiscoverResolver.stop();
        }

        /**
         * Retrieve the specified device
         * @param position device index
         * @return device at the specified position
         */
        Device getDevice(int position) {
            return mDevices.get(getItem(position));
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            convertView = super.getView(position, convertView, parent);
            Device device = mDevices.get(getItem(position));
            ((TextView) convertView.findViewById(android.R.id.text1)).setText(device.getName());
            ((TextView) convertView.findViewById(android.R.id.text2)).setText(device.getHost().getHostAddress());
            ((ImageView) convertView.findViewById(android.R.id.icon)).setImageResource(R.drawable.ic_device);
            return convertView;
        }
    }

    private DeviceAdapter mDeviceAdapter;

    /**
     * Given a SEND intent, build a list of URIs
     * @param intent intent received
     * @return list of URIs
     */
    private ArrayList<Uri> buildUriList(Intent intent) {
        if (intent.getAction().equals("android.intent.action.SEND_MULTIPLE")) {
            return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        } else {
            ArrayList<Uri> uriList = new ArrayList<>();
            uriList.add((Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM));
            return uriList;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

        mDeviceAdapter = new DeviceAdapter();
        mDeviceAdapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                findViewById(R.id.progressBar).setVisibility(View.GONE);
            }
        });
        mDeviceAdapter.start();

        final ArrayList<Uri> uriList = buildUriList(getIntent());
        final ListView listView = (ListView) findViewById(R.id.selectList);
        listView.setAdapter(mDeviceAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Device device = mDeviceAdapter.getDevice(position);

                Intent startTransfer = new Intent(ShareActivity.this, TransferService.class);
                startTransfer.setAction(TransferService.ACTION_START_TRANSFER);
                startTransfer.putExtra(TransferService.EXTRA_DEVICE, device);
                startTransfer.putParcelableArrayListExtra(TransferService.EXTRA_URIS, uriList);
                startService(startTransfer);

                // Close the activity
                ShareActivity.this.setResult(RESULT_OK);
                ShareActivity.this.finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        mDeviceAdapter.stop();
        super.onDestroy();
    }
}
