package net.nitroshare.android.explorer;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import net.nitroshare.android.R;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Adapter for displaying directory contents
 */
class DirectoryAdapter extends ArrayAdapter<File> {

    private Context mContext;

    DirectoryAdapter(String directory, Context context) {
        super(context, R.layout.view_simple_list_item, android.R.id.text1);
        mContext = context;

        File[] files = new File(directory).listFiles();
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                if (o1.isDirectory() == o2.isDirectory()) {
                    return o1.getName().compareToIgnoreCase(o2.getName());
                } else {
                    return o1.isDirectory() ? -1 : 1;
                }
            }
        });
        for (File file : files) {
            add(file);
        }
    }

    private String getDirectorySummary(File directory) {
        int numItems = directory.listFiles().length;
        return mContext.getResources().getQuantityString(R.plurals.activity_explorer_folder, numItems, numItems);
    }

    private String getFileSummary(File file) {
        long size = file.length();
        if (size < 1000) {
            return mContext.getResources().getQuantityString(
                    R.plurals.activity_explorer_bytes, (int) size, size);
        } else if (size < 1000000) {
            return mContext.getString(R.string.activity_explorer_kb, size / 1000);
        } else if (size < 1000000000) {
            return mContext.getString(R.string.activity_explorer_mb, size / 1000000);
        } else {
            return mContext.getString(R.string.activity_explorer_gb, size / 1000000000);
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = super.getView(position, convertView, parent);
        File file = getItem(position);
        ((TextView) convertView.findViewById(android.R.id.text1)).setText(file.getName());
        ((TextView) convertView.findViewById(android.R.id.text2)).setText(
                file.isDirectory() ? getDirectorySummary(file) : getFileSummary(file)
        );
        ((ImageView) convertView.findViewById(android.R.id.icon)).setImageResource(
                file.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file
        );
        return convertView;
    }
}
