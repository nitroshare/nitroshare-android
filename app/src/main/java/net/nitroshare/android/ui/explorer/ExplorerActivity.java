package net.nitroshare.android.ui.explorer;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import net.nitroshare.android.R;
import net.nitroshare.android.ui.ShareActivity;
import net.nitroshare.android.util.Settings;

import java.io.File;
import java.util.ArrayList;

/**
 * Explorer for browsing directories
 */
public class ExplorerActivity extends AppCompatActivity implements ExplorerFragment.Listener {

    private static final String TAG = "ExplorerActivity";
    private static final int SHARE_REQUEST = 1;

    /**
     * Storage directory (internal, SD card, etc.)
     */
    private class StorageDirectory {

        private String mName;
        private String mPath;

        StorageDirectory(String name, String path) {
            mName = name;
            mPath = path;
        }

        String name() {
            return mName;
        }

        String path() {
            return mPath;
        }
    }

    private ExplorerFragment mFragment;
    private boolean mShowHidden = false;
    private ArrayList<StorageDirectory> mDirectories;

    /**
     * Create and display a fragment for the specified directory
     * @param directory path to directory
     * @param clearStack clear the back stack with this item
     */
    private void showDirectory(String directory, boolean clearStack) {
        mFragment = new ExplorerFragment();

        // If directory is not empty, pass it along to the fragment
        if (directory != null) {
            Bundle arguments = new Bundle();
            arguments.putString(ExplorerFragment.DIRECTORY, directory);
            arguments.putBoolean(ExplorerFragment.SHOW_HIDDEN, mShowHidden);
            mFragment.setArguments(arguments);
        }

        // Begin a transaction to insert the fragment
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        // If the stack isn't being cleared, animate the transaction
        if (!clearStack) {
            transaction.setCustomAnimations(
                    R.anim.enter_from_right,
                    R.anim.exit_to_left,
                    R.anim.enter_from_left,
                    R.anim.exit_to_right
            );
        }

        // Insert the new fragment
        transaction.replace(R.id.directory_container, mFragment);

        // If the stack isn't being cleared, add this one to the back
        if (!clearStack) {
            transaction.addToBackStack(null);
        }

        // Commit the transaction
        transaction.commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(new Settings(this).getTheme());
        setContentView(R.layout.activity_explorer);

        if(savedInstanceState == null) {
            showDirectory(null, true);
            Toast.makeText(this, getText(R.string.activity_explorer_hint),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {

        // (Re)initialize the list of directories
        mDirectories = new ArrayList<>();

        // Finding the path to the storage directories is very difficult - for
        // API 19+, use the getExternalFilesDirs() method and extract the path
        // from the app-specific path returned; older devices cannot access
        // removably media :(

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

            // Enumerate all of the storage directories
            File files[] = getExternalFilesDirs(null);
            for (int i = 0; i < files.length; ++i) {

                // If the directory is usable, retrieve its path
                if (files[i] == null) {
                    continue;
                }
                String path = files[i].getAbsolutePath();

                // The path should contain Android/data and the portion of the
                // path preceding that is the root (a hack, but it works)
                int rootIndex = path.indexOf("Android/data");
                if (rootIndex == -1) {
                    continue;
                }
                path = path.substring(0, rootIndex);

                // Assume that the first directory is for internal storage and
                // the others are removable (either card slots or USB OTG)

                Log.i(TAG, String.format("found storage directory: \"%s\"", path));

                mDirectories.add(new StorageDirectory(
                        i == 0 ?
                                getString(R.string.activity_explorer_internal) :
                                getString(R.string.activity_explorer_removable, new File(path).getName()),
                        path
                ));
            }
        }

        // Invoke the parent method
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_explorer_options, menu);

        // Create menu options for each of the storage directories
        if (mDirectories.size() > 1) {
            for (int i = 0; i < mDirectories.size(); ++i) {
                menu.add(Menu.NONE, i, Menu.NONE, mDirectories.get(i).name());
            }
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case R.id.option_show_hidden:
                mShowHidden = !item.isChecked();
                item.setChecked(mShowHidden);
                mFragment.showHidden(mShowHidden);
                return true;
        }
        if (itemId < mDirectories.size()) {
            showDirectory(mDirectories.get(itemId).path(), true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBrowseDirectory(String directory) {
        showDirectory(directory, false);
    }

    @Override
    public void onSendUris(ArrayList<Uri> uris) {
        Intent shareIntent = new Intent(this, ShareActivity.class);
        shareIntent.setAction("android.intent.action.SEND_MULTIPLE");
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        startActivityForResult(shareIntent, SHARE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SHARE_REQUEST) {
            if (resultCode == RESULT_OK) {
                finish();
            }
        }
    }
}
