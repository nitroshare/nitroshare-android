package net.nitroshare.android.ui.explorer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

import net.nitroshare.android.R;
import net.nitroshare.android.ui.SettingsActivity;
import net.nitroshare.android.ui.ShareActivity;

import java.util.ArrayList;

/**
 * Explorer for browsing directories
 */
public class ExplorerActivity extends AppCompatActivity implements ExplorerFragment.Listener
{
    private static final int SHARE_REQUEST = 1;

    private void showDirectory(String directory)
    {
        ExplorerFragment explorerFragment = new ExplorerFragment();
        if (directory != null)
        {
            Bundle arguments = new Bundle();
            arguments.putString(ExplorerFragment.DIRECTORY, directory);
            explorerFragment.setArguments(arguments);
        }
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        if (directory != null)
        {
            transaction.setCustomAnimations(
                    R.anim.enter_from_right,
                    R.anim.exit_to_left,
                    R.anim.enter_from_left,
                    R.anim.exit_to_right
            );
        }
        transaction.replace(R.id.directory_container, explorerFragment);
        if (directory != null)
        {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        SharedPreferences mSharedPreferences;
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(ExplorerActivity.this);

        if(mSharedPreferences.getBoolean("dark-theme", false))
        {
            setTheme(R.style.DarkTheme);
        }
        else
        {
            setTheme(R.style.AppTheme);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_explorer);

        if(savedInstanceState == null)
        {
            showDirectory(null);
            Toast.makeText(this, getText(R.string.activity_explorer_hint),
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBrowseDirectory(String directory)
    {
        showDirectory(directory);
    }

    @Override
    public void onSendUris(ArrayList<Uri> uris)
    {
        Intent shareIntent = new Intent(this, ShareActivity.class);
        shareIntent.setAction("android.intent.action.SEND_MULTIPLE");
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        startActivityForResult(shareIntent, SHARE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SHARE_REQUEST)
        {
            if (resultCode == RESULT_OK)
            {
                finish();
            }
        }
    }
}
