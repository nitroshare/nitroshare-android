package net.nitroshare.android.explorer;

import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import net.nitroshare.android.R;

/**
 * Explorer for browsing directories
 */
public class ExplorerActivity extends AppCompatActivity implements ExplorerFragment.Listener {

    private void showDirectory(String directory) {
        ExplorerFragment explorerFragment = new ExplorerFragment();
        if (directory != null) {
            Bundle arguments = new Bundle();
            arguments.putString(ExplorerFragment.DIRECTORY, directory);
            explorerFragment.setArguments(arguments);
        }
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.directory_container, explorerFragment);
        if (directory != null) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explorer);

        if(savedInstanceState == null) {
            showDirectory(null);
        }
    }

    @Override
    public void onBrowseDirectory(String directory) {
        showDirectory(directory);
    }
}
