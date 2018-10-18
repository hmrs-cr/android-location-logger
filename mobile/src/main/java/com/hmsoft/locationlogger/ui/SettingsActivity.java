package com.hmsoft.locationlogger.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.DialogPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.widget.TimePicker;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;
import com.hmsoft.locationlogger.service.LocationService;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import static com.hmsoft.locationlogger.R.string;
import static com.hmsoft.locationlogger.R.xml;

public class SettingsActivity extends PreferenceActivity 
	implements OnSharedPreferenceChangeListener, Preference.OnPreferenceChangeListener {

	private static final String TAG = "SettingsActivity";
	
	private PreferenceCategory mPrefCategoryService;
    private PreferenceCategory mPrefCategorySync;

    private boolean mServicePrefChanged;
    private boolean mSyncPrefChanged;


	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(Logger.DEBUG) Logger.debug(TAG, "onCreate");

        setPrefDefaults();
		addPreferencesFromResource(xml.settings);
        addCustomPreferecences();

     	mPrefCategoryService = (PreferenceCategory)findPreference(getString(string.pref_service_settings_key));
        mPrefCategorySync = (PreferenceCategory)findPreference(getString(string.pref_locatrack_settings_key));

        /*if(GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) != ConnectionResult.SUCCESS) {
            Preference gmspref = mPrefCategoryService.findPreference(getString(R.string.pref_use_gms_if_available_key));
            if(gmspref != null) {
                mPrefCategoryService.removePreference(gmspref);
            }
        }
        Preference gmspref = mPrefCategoryService.findPreference(getString(R.string.pref_use_gms_if_available_key));
        mPrefCategoryService.removePreference(gmspref);
        */

        if(getString(R.string.action_sync_settings).equals(getIntent().getAction())) {
            getPreferenceScreen().removePreference(mPrefCategoryService);
        } else {
            //hideAdvancedPreferences(getPreferenceScreen());
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) {
                mPrefCategoryService.removePreference(mPrefCategoryService.findPreference(getString(string.pref_set_airplanemode_key)));
            }
            if(PreferenceProfile.get(getApplicationContext()).activeProfile != PreferenceProfile.PROFILE_MANUAL) {
                mPrefCategoryService.removePreference(mPrefCategoryService.findPreference(getString(string.pref_update_interval_key)));
            }
        }

        PreferenceGroup screen = getPreferenceScreen();
        removePreferencesWithDefaults(screen);
        bindPreferencesSummaryToValue(screen);
	}

    private void removePreferencesWithDefaults(PreferenceGroup group) {
        int prefCount = group.getPreferenceCount();
        PreferenceProfile prefProf = PreferenceProfile.get(getApplicationContext());
        ArrayList<Preference> toRemove = new ArrayList<>();
        String mainKey = getString(string.pref_service_enabled_key);
        boolean removeDependency = false;
        for(int c = 0; c < prefCount; c++) {
            Preference preference = group.getPreference(c);
            if(preference instanceof PreferenceGroup) {
                removePreferencesWithDefaults((PreferenceGroup) preference);
            } else {
                String key = preference.getKey();
                if(mainKey.equals(key)) {
                    removeDependency = true;
                }
                if(prefProf.hasProfileDefault(key)) {
                    toRemove.add(preference);
                }
            }
        }
        for(Preference pref : toRemove) {
            if(removeDependency) pref.setDependency(null);
            group.removePreference(pref);
        }
    }

    /*private void hideAdvancedPreferences(PreferenceGroup group) {
        if(mAdvancedSettings.showAll()) {
            return;
        }

        int c = 0;
        while(c < group.getPreferenceCount()) {
            Preference pref = group.getPreference(c);
            if(!mAdvancedSettings.isVisible(group, pref)) {
                group.removePreference(pref);
            } else {
                if(pref instanceof PreferenceGroup) {
                    hideAdvancedPreferences((PreferenceGroup)pref);
                }
                c++;
            }
        }
    }*/

    private void addCustomPreferecences() {
        //noinspection deprecation
        final DialogPreference prefSyncTime = (DialogPreference)findPreference(getString(string.pref_synctime_key));
        final Context context = this;

        prefSyncTime.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                TimePicker timePicker = (TimePicker) prefSyncTime.getDialog().findViewById(R.id.timePicker);
                final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                String[] syncTime = preferences.getString(getString(R.string.pref_synctime_key),
                        getString(string.pref_synctime_default)).split(":");
                int syncHour = Integer.parseInt(syncTime[0]);
                int syncMinute = Integer.parseInt(syncTime[1]);
                timePicker.setCurrentHour(syncHour);
                timePicker.setCurrentMinute(syncMinute);
                timePicker.setOnTimeChangedListener(new TimePicker.OnTimeChangedListener() {
                    @Override
                    public void onTimeChanged(TimePicker view, int hourOfDay, int minute) {
                        Preference.OnPreferenceChangeListener listener = prefSyncTime.getOnPreferenceChangeListener();
                        String value = String.format("%d:%d", hourOfDay, minute);
                        preferences.edit().putString(context.getString(R.string.pref_synctime_key), value).apply();
                        if (listener != null) {
                            listener.onPreferenceChange(prefSyncTime, value);
                        }
                    }
                });
                return true;
            }
        });
    }

    private void setPrefDefaults() {
        setPrefDefaults(this);
    }

     public  static void setPrefDefaults(Context context) {
        PreferenceManager.setDefaultValues(context, xml.settings, false);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = null;
        String deviceIdKey = context.getString(R.string.pref_locatrack_deviceid_key);
        String devId = preferences.getString(deviceIdKey, "");
        if(TextUtils.isEmpty(devId)) {
            editor = preferences.edit();
            editor.putString(deviceIdKey, "");
        }
    }

	public static void start(Context context) {
		start(context, null);
	}


    public static void start(Context context, String action) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.setAction(action);
        context.startActivity(intent);
    }
	
	@Override
	public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {	
		
		if(mPrefCategoryService.findPreference(key) != null) {
			mServicePrefChanged = true;
			if(key.equals(getString(string.pref_service_enabled_key))) {
                boolean serviceChangedToEnabled = preferences.getBoolean(key, true);
				if(serviceChangedToEnabled) {
					LocationService.enable(this);
				} else {
					LocationService.configure(this);
					mServicePrefChanged = false;
				}
			} else if(key.equals(getString(string.pref_active_profile_key))) {
                PreferenceProfile.reset();
                Context ctx = getApplicationContext();
                if(PreferenceProfile.get(ctx).getBoolean(string.pref_service_enabled_key, true)) {
                    if(!LocationService.isRunning(ctx)) {
                        LocationService.enable(ctx);
                        mServicePrefChanged = false;
                    }
                }
            }
			if(Logger.DEBUG) Logger.debug(TAG, "Preference %s chaged. (Service)", key);
		} 
		else if(mPrefCategorySync.findPreference(key) != null) {
            mSyncPrefChanged = true;
            if(Logger.DEBUG) Logger.debug(TAG, "Preference %s chaged. (Sync)", key);
        } else {
			if(Logger.DEBUG) Logger.debug(TAG, "Preference %s chaged. (Unknow)", key);
		}
	}
	
	@SuppressWarnings("deprecation")
	@Override
	protected void onResume() {
	    super.onResume();
	    if(Logger.DEBUG) Logger.debug(TAG, "onResume");
		
		mServicePrefChanged = false;

		getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
	}

	@SuppressWarnings("deprecation")
	@Override
	protected void onPause() {	    
	    if(Logger.DEBUG) Logger.debug(TAG, "onPause");
		
		getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
		
		if(mServicePrefChanged || mSyncPrefChanged) {
			LocationService.configure(this);
		}

		super.onPause();
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object value) {	
		String stringValue = value.toString();
		if (preference instanceof ListPreference) {
			// For list preferences, look up the correct display value in
			// the preference's 'entries' list.
			ListPreference listPreference = (ListPreference) preference;
			int index = listPreference.findIndexOfValue(stringValue);

			// Set the summary to reflect the new value.
			stringValue = (index >= 0 ? listPreference.getEntries()[index] : "").toString();
		} else if(preference instanceof CheckBoxPreference) {
			boolean checked = Boolean.parseBoolean(stringValue);
            stringValue = (checked ? getString(string.enabled_string) : getString(string.disabled_string));
		} else if(getString(string.pref_synctime_key).equals(preference.getKey())) {
            String[] time = stringValue.split(":");
            int syncHour = Integer.parseInt(time[0]);
            int syncMinute = Integer.parseInt(time[1]);
            Date alarmeDate = new Date(Utils.getMillisOfTomorrowTime(syncHour, syncMinute));
            SimpleDateFormat alarmTimeFormatter = new SimpleDateFormat("MMM d h:ma ");

            Date deltaDate = new Date(alarmeDate.getTime() - System.currentTimeMillis());
            SimpleDateFormat deltaFormatter = new SimpleDateFormat("HH:mm");

            stringValue = String.format("%s (%s)", alarmTimeFormatter.format(alarmeDate),
                    deltaFormatter.format(deltaDate));
        }
		
		preference.setSummary(stringValue);
		return true;
	}
	
	private void bindPreferencesSummaryToValue(PreferenceGroup group) {
		int prefCount = group.getPreferenceCount();
        for(int c = 0; c < prefCount; c++) {
			Preference preference = group.getPreference(c);
            if(preference instanceof PreferenceGroup) {
                bindPreferencesSummaryToValue((PreferenceGroup)preference);
            } else {
                String dep = preference.getDependency();
                if(!TextUtils.isEmpty(dep) && group.findPreference(dep) == null) {
                    preference.setDependency(null);
                }
                bindPreferenceSummaryToValue(preference);
            }
		}
	}
	
	/**
	 * Binds a preference's summary to its value. More specifically, when the
	 * preference's value is changed, its summary (line of text below the
	 * preference title) is updated to reflect the value. The summary is also
	 * immediately updated upon calling this method. The exact display format is
	 * dependent on the type of preference.
	 */
	private void bindPreferenceSummaryToValue(Preference preference) {
		// Set the listener to watch for value changes.
		preference.setOnPreferenceChangeListener(this);
		
		Object value = null;		
		if(preference instanceof CheckBoxPreference) {
			value = ((CheckBoxPreference)preference).isChecked();
		} else if(preference instanceof ListPreference) {
			value = ((ListPreference)preference).getValue();
		} else if(preference instanceof EditTextPreference) {
			value = ((EditTextPreference)preference).getText();
		}
		// Trigger the listener immediately with the preference's
		// current value.
		if(value != null) {
			this.onPreferenceChange(preference,	value);
		}
	}
}
