package net.nitroshare.android.ui.explorer;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Callback;
import com.squareup.picasso.Picasso;

import net.nitroshare.android.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Adapter for displaying directory contents
 */
class DirectoryAdapter extends ArrayAdapter<File> {

    interface Listener {
        void onAllItemsDeselected();
    }

    private Context mContext;
    private Listener mListener;
    private boolean mCheckboxes = false;
    private SparseArray<File> mChecked = new SparseArray<>();

    DirectoryAdapter(String directory, Context context, Listener listener) {
        super(context, R.layout.view_simple_list_item_with_checkbox, android.R.id.text1);
        mContext = context;
        mListener = listener;

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

    /**
     * Activate checkboxes for the list view items
     */
    void activateCheckboxes(int position) {
        mCheckboxes = true;
        mChecked.put(position, getItem(position));
        notifyDataSetChanged();
    }

    /**
     * Deactivate checkboxes for the list view items
     */
    void deactivateCheckboxes() {
        mCheckboxes = false;
        mChecked.clear();
        notifyDataSetChanged();
    }

    /**
     * Toggle the checkbox for the item at position
     */
    void toggleItem(int position) {
        if (mChecked.indexOfKey(position) < 0) {
            mChecked.put(position, getItem(position));
        } else {
            mChecked.remove(position);
        }
        notifyDataSetChanged();
    }

    /**
     * Retrieve a list of URIs containing all checked items
     */
    ArrayList<Uri> getUris() {
        ArrayList<Uri> uris = new ArrayList<>();
        for (int i = 0; i < mChecked.size(); ++i) {
            uris.add(Uri.fromFile(mChecked.valueAt(i)));
        }
        return uris;
    }

    private String getDirectorySummary(File directory) {
        File files[] = directory.listFiles();
        int numItems = 0;
        if (files != null) {
            numItems = files.length;
        }
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

    @NonNull
    @Override
    public View getView(final int position, View convertView, @NonNull ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        File file = getItem(position);
        //noinspection ConstantConditions
        ((TextView) view.findViewById(android.R.id.text1)).setText(file.getName());
        ((TextView) view.findViewById(android.R.id.text2)).setText(
                file.isDirectory() ? getDirectorySummary(file) : getFileSummary(file)
        );
        final ImageView imageView = (ImageView) view.findViewById(android.R.id.icon);
        Picasso.with(mContext)
                .load(file)
                .resize(96, 96)
                .placeholder(ContextCompat.getDrawable(
                        mContext, file.isDirectory() ? R.drawable.ic_folder : R.drawable.ic_file
                ))
                .into(imageView, new Callback() {
                    @Override
                    public void onSuccess() {
                        // Remove the tint once the image loads
                        imageView.setColorFilter(Color.argb(0, 0, 0, 0), PorterDuff.Mode.DST);
                    }

                    @Override
                    public void onError() {
                    }
                });
        CheckBox checkBox = (CheckBox) view.findViewById(android.R.id.checkbox);
        if (mCheckboxes) {
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setChecked(mChecked.indexOfKey(position) >= 0);
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        mChecked.put(position, getItem(position));
                    } else {
                        mChecked.remove(position);
                        if (mChecked.size() == 0) {
                            mListener.onAllItemsDeselected();
                        }
                    }
                }
            });
            checkBox.setVisibility(View.VISIBLE);
        } else {
            checkBox.setVisibility(View.GONE);
        }
        return view;
    }
}
