package net.nitroshare.android.explorer;

import android.app.ListFragment;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ListView;

import java.io.File;

/**
 * Display the contents of a directory
 */
public class ExplorerFragment extends ListFragment {

    static final String DIRECTORY = "directory";

    interface Listener {
        void onBrowseDirectory(String directory);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String directory = null;
        if (getArguments() != null) {
            directory = getArguments().getString(DIRECTORY);
        }
        if (directory == null) {
            directory = Environment.getExternalStorageDirectory().getPath();
        }
        setListAdapter(new DirectoryAdapter(directory, getActivity()));
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        File file = (File) getListAdapter().getItem(position);
        if (file.isDirectory()) {
            ((Listener) getActivity()).onBrowseDirectory(file.getPath());
        }
    }
}
