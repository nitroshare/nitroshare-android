package net.nitroshare.android.ui.settings;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.support.annotation.StringRes;
import android.view.MenuItem;

import net.nitroshare.android.R;
import net.nitroshare.android.transfer.TransferService;
import net.nitroshare.android.util.Settings;

/**
 * Settings for the application
 */
public class SettingsActivity extends AppCompatSettingsActivity {

    public static class SettingsFragment extends PreferenceFragment {

        private Settings mSettings;

        /**
         * Create a category using the specified string resource for the title
         * @param titleResId resource ID to use for the title
         * @return newly created category
         */
        private PreferenceCategory createCategory(@StringRes int titleResId) {
            PreferenceCategory preferenceCategory = new PreferenceCategory(getActivity());
            preferenceCategory.setTitle(titleResId);
            getPreferenceScreen().addPreference(preferenceCategory);
            return preferenceCategory;
        }

        /**
         * Create an EditTextPreference for the specified preference
         * @param titleResId resource ID to use for the title
         * @param key preference key
         * @return newly created preference
         */
        private EditTextPreference createEditTextPreference(@StringRes int titleResId, Settings.Key key) {
            final EditTextPreference editTextPreference = new EditTextPreference(getActivity());
            editTextPreference.setDefaultValue(mSettings.getDefault(key));
            editTextPreference.setKey(key.name());
            editTextPreference.setSummary(mSettings.getString(key));
            editTextPreference.setTitle(titleResId);
            editTextPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    editTextPreference.setSummary((String) newValue);
                    return true;
                }
            });
            return editTextPreference;
        }

        /**
         * Create a SwitchPreference for the specified preference
         * @param titleResId resource ID to use for the title
         * @param summaryResId resource ID to use for the summary
         * @param key preference key
         * @return newly created preference
         */
        private CheckBoxPreference createCheckBoxPreference(@StringRes int titleResId, @StringRes int summaryResId, Settings.Key key) {
            final CheckBoxPreference checkBoxPreference = new CheckBoxPreference(getActivity());
            checkBoxPreference.setDefaultValue(mSettings.getDefault(key));
            checkBoxPreference.setKey(key.name());
            checkBoxPreference.setSummary(summaryResId);
            checkBoxPreference.setTitle(titleResId);
            checkBoxPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    checkBoxPreference.setChecked((boolean) newValue);
                    return true;
                }
            });
            return checkBoxPreference;
        }

        /**
         * Create a DirectoryPreference for the specified preference
         * @param titleResId resource ID to use for the title
         * @param key preference key
         * @return newly created preference
         */
        private DirectoryPreference createDirectoryPreference(@StringRes int titleResId, Settings.Key key) {
            final DirectoryPreference directoryPreference = new DirectoryPreference(getActivity(), null);
            directoryPreference.setDefaultValue(mSettings.getDefault(key));
            directoryPreference.setKey(key.name());
            directoryPreference.setSummary(mSettings.getString(key));
            directoryPreference.setTitle(titleResId);
            directoryPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    directoryPreference.setSummary((String) newValue);
                    return true;
                }
            });
            return directoryPreference;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mSettings = new Settings(getActivity());

            // Create the preference screen
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(getActivity());
            setPreferenceScreen(screen);

            // Create the categories
            PreferenceCategory general = createCategory(R.string.activity_settings_category_general);
            general.setLayoutResource(R.layout.preference_layout);
            PreferenceCategory appearance = createCategory(R.string.activity_settings_category_appearance);
            appearance.setLayoutResource(R.layout.preference_layout);
            PreferenceCategory notifications = createCategory(R.string.activity_settings_category_notifications);
            notifications.setLayoutResource(R.layout.preference_layout);

            // Create the preferences
            general.addPreference(createEditTextPreference(R.string.activity_settings_pref_device_name, Settings.Key.DEVICE_NAME));
            general.addPreference(createDirectoryPreference(R.string.activity_settings_pref_transfer_directory, Settings.Key.TRANSFER_DIRECTORY));
            general.addPreference(createCheckBoxPreference(R.string.activity_settings_pref_behavior_receive, R.string.activity_settings_pref_behavior_receive_summary, Settings.Key.BEHAVIOR_RECEIVE));
            general.addPreference(createCheckBoxPreference(R.string.activity_settings_pref_behavior_overwrite, R.string.activity_settings_pref_behavior_overwrite_summary, Settings.Key.BEHAVIOR_OVERWRITE));
            appearance.addPreference(createCheckBoxPreference(R.string.activity_settings_darkTheme, R.string.activity_settings_darkTheme_summary, Settings.Key.UI_DARK));
            notifications.addPreference(createCheckBoxPreference(R.string.activity_settings_pref_notification_sound, R.string.activity_settings_pref_notification_sound_summary, Settings.Key.TRANSFER_NOTIFICATION));

            // Instantly enable/disable the transfer service when the "receive"
            // setting has been changed
            Preference receivePreference = findPreference(Settings.Key.BEHAVIOR_RECEIVE.name());
            receivePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    TransferService.startStopService(getActivity(), (boolean) newValue);
                    return true;
                }
            });

            // Instantly apply theme changes
            Preference darkPreference = findPreference(Settings.Key.UI_DARK.name());
            darkPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    getActivity().recreate();
                    return true;
                }
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // The theme must be set before calling the parent method since it
        // initializes some of the controls in the method
        setTheme(new Settings(this).getTheme());
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment())
                    .commit();
        }

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
