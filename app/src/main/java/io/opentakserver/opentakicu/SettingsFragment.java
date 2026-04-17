package io.opentakserver.opentakicu;

import android.content.pm.PackageInfo;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import androidx.annotation.Nullable;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static String LOGTAG = "SettingsFragment";

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        Preference versionPref = findPreference("app_version");
        if (versionPref != null) {
            versionPref.setTitle("OpenTAK ICU");
            try {
                PackageInfo info = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0);
                versionPref.setSummary("v" + info.versionName);
            } catch (Exception e) {
                versionPref.setSummary("");
            }
        }
    }
}