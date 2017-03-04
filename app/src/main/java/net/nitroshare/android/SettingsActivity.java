package net.nitroshare.android;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.view.MenuItem;

/**
 * Settings for the application
 */
public class SettingsActivity extends PreferenceActivity {

    private static final String EXTRA_DEFAULT_ID = "default_id";

    public static class SettingsFragment extends PreferenceFragment {

        SharedPreferences mSharedPreferences;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);

            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

            bindPreferenceSummaryToValue(R.string.setting_device_name,
                    R.string.setting_device_name_default);
            bindPreferenceSummaryToValue(R.string.setting_transfer_directory,
                    R.string.setting_transfer_directory_default);
        }

        /**
         * Listener for preference change events
         */
        private Preference.OnPreferenceChangeListener mListener =
                new Preference.OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference preference, Object newValue) {
                        String stringValue = newValue.toString();
                        if (stringValue.isEmpty()) {
                            stringValue = getString(preference.getExtras().getInt(EXTRA_DEFAULT_ID));
                        }
                        preference.setSummary(stringValue);
                        return true;
                    }
                };

        /**
         * Update the summary for a preference based on its value
         */
        private void bindPreferenceSummaryToValue(int keyId, int defaultId) {
            Preference preference = findPreference(getString(keyId));
            preference.getExtras().putInt(EXTRA_DEFAULT_ID, defaultId);
            preference.setOnPreferenceChangeListener(mListener);
            mListener.onPreferenceChange(preference,
                    mSharedPreferences.getString(getString(keyId), ""));
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
