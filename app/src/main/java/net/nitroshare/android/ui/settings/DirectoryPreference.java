package net.nitroshare.android.ui.settings;

import android.content.Context;
import android.graphics.Color;
import android.os.Environment;
import android.preference.DialogPreference;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import net.nitroshare.android.R;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Preference for selecting a directory
 */
class DirectoryPreference extends DialogPreference {

    /**
     * Adapter for showing a simple list of directories
     */
    private class DirectoryAdapter extends ArrayAdapter<File> {

        private int mPosition = -1;

        DirectoryAdapter(@NonNull Context context, String directory) throws IOException {
            super(context, R.layout.view_simple_list_item_directory, android.R.id.text1);

            File[] files = new File(directory).listFiles();
            if (files == null) {
                throw new IOException(String.format("unable to read %s", directory));
            }

            // Sort the list of directories by name
            Arrays.sort(files, new Comparator<File>() {
                @Override
                public int compare(File o1, File o2) {
                    return o1.getName().compareToIgnoreCase(o2.getName());
                }
            });

            // Append all of the directories
            add(new File(".."));
            for (File file : files) {
                if (file.isDirectory()) {
                    add(file);
                }
            }
        }

        @NonNull
        @Override
        public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            File file = getItem(position);
            ((ImageView) view.findViewById(android.R.id.icon)).setImageResource(R.drawable.ic_folder);
            //noinspection ConstantConditions
            ((TextView) view.findViewById(android.R.id.text1)).setText(file.getName());
            view.setBackgroundColor(
                    position == mPosition ?
                            ContextCompat.getColor(getContext(), R.color.colorAccent) :
                            Color.TRANSPARENT
            );
            return view;
        }

        void setSelection(int position) {
            mPosition = position;
        }

        /**
         * Obtain the currently selected path
         */
        String getSelection() {
            //noinspection ConstantConditions
            return 0 <= mPosition && mPosition < getCount() ?
                    getItem(mPosition).getAbsolutePath() : null;
        }
    }

    DirectoryPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.pref_directory_browser);
    }

    private String mDirectory;
    private ListView mListView;
    private DirectoryAdapter mAdapter;

    /**
     * Load the specified directory in the adapter
     */
    private void load(String directory) {
        String newDirectory;
        if (directory == null) {
            newDirectory = Environment.getExternalStorageDirectory().getPath();
        } else {
            newDirectory = new File(mDirectory + File.separatorChar + directory).getAbsolutePath();
        }
        try {
            mAdapter = new DirectoryAdapter(getContext(), newDirectory);
        } catch (IOException e) {
            Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        mDirectory = newDirectory;
        mListView.setAdapter(mAdapter);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        mListView = (ListView) view.findViewById(R.id.listView);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //noinspection ConstantConditions
                load(mAdapter.getItem(position).getName());
            }
        });
        mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    mAdapter.setSelection(position);
                    mAdapter.notifyDataSetChanged();
                    return true;
                }
                return false;
            }
        });
        load(null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (mAdapter != null && positiveResult) {
            String path = mAdapter.getSelection();

            if (path != null) {
                callChangeListener(path);
                persistString(path);
            }
        }
    }
}
