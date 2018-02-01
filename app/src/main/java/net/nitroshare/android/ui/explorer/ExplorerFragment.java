package net.nitroshare.android.ui.explorer;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import net.nitroshare.android.R;
import net.nitroshare.android.util.Settings;

import java.io.File;
import java.util.ArrayList;

/**
 * Display the contents of a directory
 */
public class ExplorerFragment extends ListFragment
        implements ActionMode.Callback, DirectoryAdapter.Listener {

    private static final String TAG = "ExplorerFragment";

    static final String DIRECTORY = "directory";

    interface Listener {
        void onBrowseDirectory(String directory);
        void onSendUris(ArrayList<Uri> uris);
    }

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

    private Listener mListener;
    private DirectoryAdapter mDirectoryAdapter;
    private ActionMode mActionMode;

    private ArrayList<StorageDirectory> mDirectories;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().setTheme(new Settings(getActivity()).getTheme());

        setHasOptionsMenu(true);

        // TODO: it might be better to move parts of this into onResume()

        // (Re)initialize the list of directories
        mDirectories = new ArrayList<>();

        // Finding the path to the storage directories is very difficult - for
        // API 19+, use the getExternalFilesDirs() method and extract the path
        // from the app-specific path returned; older devices cannot access
        // removably media :(

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {

            // Enumerate all of the storage directories
            File files[] = getActivity().getExternalFilesDirs(null);
            for (int i = 0; i < files.length; ++i) {

                String path = files[i].getAbsolutePath();

                // The path should contain Android/data and the portion of the
                // path preceding that is the root (a hack, but it works)
                int rootIndex = path.indexOf("Android/data");
                if (rootIndex == -1) {
                    break;
                }
                path = path.substring(0, rootIndex);

                // Assume that the first directory is for internal storage and
                // the others are removable (either card slots or USB OTG)

                Log.i(TAG, String.format("found storage directory: \"%s\"", path));

                mDirectories.add(new StorageDirectory(
                        i == 0 ?
                                getActivity().getString(R.string.activity_explorer_internal) :
                                getActivity().getString(R.string.activity_explorer_removable, new File(path).getName()),
                        path
                ));
            }
        }

        // Use the directory argument if provided or default to the first one found
        String directory = null;
        if (getArguments() != null) {
            directory = getArguments().getString(DIRECTORY);
        }
        if (directory == null) {
            directory = Environment.getExternalStorageDirectory().getPath();
        }

        // Create the adapter for the directory
        mDirectoryAdapter = new DirectoryAdapter(directory, getActivity(), this);
        setListAdapter(mDirectoryAdapter);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mListener = (Listener) getActivity();

        // Watch for long presses on directories
        getListView().setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                mDirectoryAdapter.activateCheckboxes(position);
                mActionMode = getActivity().startActionMode(ExplorerFragment.this);
                return true;
            }
        });
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        if (mActionMode == null) {
            File file = mDirectoryAdapter.getItem(position);
            //noinspection ConstantConditions
            if (file.isDirectory()) {
                mListener.onBrowseDirectory(file.getPath());
            } else {
                ArrayList<Uri> uris = new ArrayList<>();
                uris.add(Uri.fromFile(file));
                mListener.onSendUris(uris);
            }
        } else {
            mDirectoryAdapter.toggleItem(position);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_explorer_options, menu);

        // Create menu options for each of the storage directories
        if (mDirectories.size() > 1) {
            for (int i = 0; i < mDirectories.size(); ++i) {
                menu.add(Menu.NONE, i, Menu.NONE, mDirectories.get(i).name());
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        switch (itemId) {
            case R.id.option_show_hidden:
                boolean isChecked = !item.isChecked();
                item.setChecked(isChecked);
                mDirectoryAdapter.toggleHidden(isChecked);
                return true;
        }
        if (itemId < mDirectories.size()) {
            mListener.onBrowseDirectory(mDirectories.get(itemId).path());
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        MenuInflater inflator = mode.getMenuInflater();
        inflator.inflate(R.menu.menu_explorer_actions, menu);
        return true;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_send:
                mListener.onSendUris(mDirectoryAdapter.getUris());
                mActionMode.finish();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mDirectoryAdapter.deactivateCheckboxes();
        mActionMode = null;
    }

    @Override
    public void onAllItemsDeselected() {
        mActionMode.finish();
    }

    @Override
    public void onError(String message) {
        setEmptyText(message);
    }
}
