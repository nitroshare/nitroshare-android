package net.nitroshare.android;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import net.nitroshare.android.discovery.Device;
import net.nitroshare.android.discovery.DiscoveryAdapter;
import net.nitroshare.android.transfer.TransferService;

import java.util.ArrayList;

/**
 * Display a list of devices available for receiving a transfer
 *
 * mDNS (multicast DNS) is used to find other peers capable of receiving the transfer. Once a
 * device is selected, the transfer service is provided with the device information and the file.
 */
public class ShareActivity extends Activity {

    private static final String TAG = "ShareActivity";

    private DiscoveryAdapter mAdapter;

    /**
     * Given a SEND intent, determine the absolute paths to the items to send
     * @param intent intent to resolve
     * @return list of absolute filenames
     */
    private String[] resolveIntent(Intent intent) {
        ArrayList<Uri> unresolvedUris = new ArrayList<>();
        switch (intent.getAction()) {
            case "android.intent.action.SEND":
                unresolvedUris.add((Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM));
                break;
            case "android.intent.action.SEND_MULTIPLE":
                unresolvedUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                break;
        }
        Log.i(TAG, String.format("received intent with %d URIs", unresolvedUris.size()));
        ArrayList<String> resolvedFilenames = new ArrayList<>();
        for (Uri uri : unresolvedUris) {
            switch (uri.getScheme()) {
                case "content":
                    Cursor cursor = getContentResolver().query(uri, null, null, null, null);
                    if (cursor != null) {
                        cursor.moveToFirst();
                        resolvedFilenames.add(cursor.getString(
                                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)));
                        cursor.close();
                    }
                    break;
                case "file":
                    resolvedFilenames.add(uri.getPath());
                    break;
            }
        }
        Log.i(TAG, String.format("successfully resolved %d URIs", resolvedFilenames.size()));
        return resolvedFilenames.toArray(new String[resolvedFilenames.size()]);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

        mAdapter = new DiscoveryAdapter(this);
        mAdapter.start();

        final String[] filenames = resolveIntent(getIntent());

        final ListView listView = (ListView) findViewById(R.id.selectList);
        listView.setAdapter(mAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Device device = (Device) mAdapter.getDevice(position);

                Intent startTransfer = new Intent(ShareActivity.this, TransferService.class);
                startTransfer.setAction(TransferService.ACTION_START_TRANSFER);
                startTransfer.putExtra(TransferService.EXTRA_DEVICE, device);
                startTransfer.putExtra(TransferService.EXTRA_FILENAMES, filenames);
                startService(startTransfer);

                // Close the activity
                ShareActivity.this.finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        mAdapter.stop();
        super.onDestroy();
    }
}
