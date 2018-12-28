package com.hmsoft.locationlogger.ui;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.hmsoft.locationlogger.BuildConfig;
import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Constants;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.data.Geocoder;
import com.hmsoft.locationlogger.data.locatrack.LocatrackDb;
import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;
import com.hmsoft.locationlogger.receivers.StartServiceReceiver;
import com.hmsoft.locationlogger.service.CoreService;

public class MainActivity extends ActionBarActivity {
    private static final String TAG = "MainActivity";

    static Bundle sLastBundle = null;

    TextView labelLastEntryValue;
    ToggleButton chkServiceEnabled;
    Menu mMenu;
    View layoutServiceEnabled;
    TextView labelDeviceId;
    int updateUIFreq = 1000;

    private boolean mRestrictedSettings;
    private boolean mConfigured;
    private String mDeviceId;

    private int mRestrictedSettingsCount = 6;

    Handler mUpdateHandler;
    UpdateUIRunnable mUpdateRunnable;


    public static void start(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        if(!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    private static class UpdateUIRunnable implements Runnable {

        MainActivity activity;

        private UpdateUIRunnable(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            if(activity != null && activity.mUpdateHandler != null) {
                if(!LoadUITask.running) {
                    activity.updateUI();
                    activity.mUpdateHandler.postDelayed(this, activity.updateUIFreq);
                    Logger.debug(TAG, "Upating UI");
                }
            }
        }
    }

    private static class GetAddressNameTask extends AsyncTask<Location, Void, String> {
        private MainActivity mActivity;
        private final int mDistanceValue;
        private final String mDistanceText;
        private final int mTimeValue;
        private final String mTimeText;

        private GetAddressNameTask(MainActivity activity, int distanceValue, String distanceText,
                                   int timeValue, String timeText) {
            mActivity = activity;
            mDistanceValue = distanceValue;
            mDistanceText = distanceText;
            mTimeValue = timeValue;
            mTimeText = timeText;
        }

        @Override
        protected String doInBackground(Location... params) {
            Location loc = params[0];
            String address = Geocoder.getFromRemote(mActivity, loc);
            if(!TextUtils.isEmpty(address)) {
                Geocoder.addToCache(loc, address);
            }
            return address;
        }

        @Override
        protected void onPostExecute(String address) {
            if(!TextUtils.isEmpty(address)) {
                mActivity.labelLastEntryValue.setText(mActivity.getString(R.string.nmea_last_entry_text,
                        mTimeValue, mTimeText, address, mDistanceValue, mDistanceText));
            }
            mActivity = null;
        }
    }

    private static class LoadUITask extends AsyncTask<Void, Void, Bundle> {

        private static LoadUITask sInstance;

        private MainActivity mActivity;
        private String mTimeText;
        private int mTimeValue;
        private final int mLabelLastEntryValueId;
        private boolean mNeedGetAddress;
        private int mDistanceValue = 0;
        private String mDistanceText;
        private Location mLastLocation;

        public static boolean running;

        private LoadUITask(MainActivity activity) {
            mActivity = activity;
            mDistanceText = mActivity.getString(R.string.generic_meters);
            mLabelLastEntryValueId = mActivity.labelLastEntryValue.getId();
        }

        public static void run(MainActivity activity) {
            if(sInstance == null) {
                sInstance = new LoadUITask(activity);
                sInstance.execute();
                running = true;
            }
        }

        @Override
        protected Bundle doInBackground(Void... params) {

            String lastEntryText = mActivity.getString(R.string.generic_none);

            mLastLocation = LocatrackDb.last();
            if(mLastLocation != null) {
                Location currentLoc = Utils.getBestLastLocation(mActivity);
                if(currentLoc != null) {
                    mDistanceValue = (int)currentLoc.distanceTo(mLastLocation);
                    if(mDistanceValue > 10000) {
                        mDistanceValue = mDistanceValue / 1000;
                        mDistanceText = mActivity.getString(R.string.generic_kilometers);
                    }
                }

                setTimeValueText(mLastLocation.getTime(), false);
                String address = Geocoder.getFromCache(mLastLocation);
                if(!TextUtils.isEmpty(address)) {
                    lastEntryText = mActivity.getString(R.string.nmea_last_entry_text,
                            mTimeValue, mTimeText, address, mDistanceValue, mDistanceText);
                } else {
                    lastEntryText = mActivity.getString(R.string.nmea_last_entry_noaddress_text,
                            mTimeValue, mTimeText, mDistanceValue, mDistanceText);
                    mNeedGetAddress = true;
                }
            }

            Bundle values = new Bundle();
            values.putString(String.valueOf(mLabelLastEntryValueId), lastEntryText);
            return values;
        }

        @Override
        protected void onPostExecute(Bundle result) {
            if(mNeedGetAddress) {
                (new GetAddressNameTask(mActivity, mDistanceValue, mDistanceText,
                        mTimeValue, mTimeText)).execute(mLastLocation);
            }

            sLastBundle = result;
            mActivity.setUiValuesFromBundle(result);
            mActivity = null;
            sInstance = null;
            running = false;
        }

        private void setTimeValueText(long millis, boolean negative) {
            int seconds = (int)(System.currentTimeMillis() - millis) / 1000;

            if(negative) seconds *= -1;
            if(seconds < 0) seconds = 0;

            int minutes = seconds / 60;
            int hours = minutes / 60;
            int days = hours / 24;

            if(days == 1) {
                mTimeText = mActivity.getString(R.string.generic_day);
                mTimeValue = 1;
                mActivity.updateUIFreq  = 60000 * 60;
            } else if(days > 1) {
                mTimeText = mActivity.getString(R.string.generic_days);
                mTimeValue = days;
                mActivity.updateUIFreq  = 60000 * 60;
            } else if(hours == 1) {
                mTimeValue = 1;
                mActivity.updateUIFreq  = 60000 * 60;
                mTimeText = mActivity.getString(R.string.generic_hour);
            } else if(hours > 1) {
                mTimeValue = hours;
                mTimeText = mActivity.getString(R.string.generic_hours);
                mActivity.updateUIFreq  = 60000 * 60;
            } else if(minutes == 1) {
                mTimeValue = 1;
                mTimeText = mActivity.getString(R.string.generic_minute);
                mActivity.updateUIFreq  = 60000;
            } else if(minutes > 1) {
                mTimeValue = minutes;
                mActivity.updateUIFreq  = 60000;
                mTimeText = mActivity.getString(R.string.generic_minutes);
            } else {
                mActivity.updateUIFreq  = 1000;
                mTimeValue = seconds;
                mTimeText = mActivity.getString(R.string.generic_seconds);
            }
        }
    }

    private BroadcastReceiver mUpdateUiReceiver = new BroadcastReceiver() {
		@Override
        public void onReceive(Context context, Intent intent) {
            if(Logger.DEBUG) Logger.debug(TAG, "onReceive");
			MainActivity.this.updateUI();
            mUpdateHandler.postDelayed(mUpdateRunnable, 6010);
        }
	};

    void saveTextToBundle(TextView view, Bundle values) {
        values.putString(String.valueOf(view.getId()), view.getText().toString());
    }

    private void setTextFromBundle(TextView view, Bundle values) {
        String text = values.getString(String.valueOf(view.getId()));
        if(TextUtils.isEmpty(text)) {
            text = view.getText().toString();
        }
        view.setText(text);
    }

    void setUiValuesFromBundle(Bundle values) {
        if(values != null) {
            setTextFromBundle(labelLastEntryValue, values);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(Logger.DEBUG) Logger.debug(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        SettingsActivity.setPrefDefaults(this);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mDeviceId = preferences.getString(getString(R.string.pref_locatrack_deviceid_key), "");
        if(BuildConfig.DEFAULT_CONFIGURED) {
            mConfigured = true;
        } else {
            mConfigured = preferences.getBoolean(getString(R.string.pref_locatrack_activated_key), false);            
            if(!mConfigured) {
                LoginActivity.start(getApplicationContext());
                StartServiceReceiver.disable(getApplicationContext());
                finish();
                return;
            }
        }

        setContentView(R.layout.activity_main);

        labelLastEntryValue = (TextView)findViewById(R.id.labelLastEntryValue);
        chkServiceEnabled = (ToggleButton)findViewById(R.id.chkServiceEnabled);
        layoutServiceEnabled = findViewById(R.id.layoutServiceEnabled);
        labelDeviceId = (TextView)findViewById(R.id.labelDeviceId);

        if(savedInstanceState == null) savedInstanceState = sLastBundle;
        setUiValuesFromBundle(savedInstanceState);

        ((TextView)findViewById(R.id.labelVersion)).setText(String.format("%s - %s",
                getString(R.string.app_name), Constants.VERSION_STRING));
    }


    @Override
    protected void onSaveInstanceState(Bundle values) {
        if(Logger.DEBUG) Logger.debug(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(values);
        saveTextToBundle(labelLastEntryValue, values);
    }
	
	public void updateLocation(View view) {
		CoreService.updateLocation(this);
	}

    public void setServiceEnabled(View view) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String key = getString(R.string.pref_service_enabled_key);
        boolean serviceEnabled = preferences.getBoolean(key, true);
        serviceEnabled = !serviceEnabled;
        preferences.edit().putBoolean(key, serviceEnabled).apply();
        if(serviceEnabled) {
            StartServiceReceiver.enable(this);
        } else {
            CoreService.configure(this);
        }        
    }

    void updateUI() {
        if(Utils.hasAllPermissions()) {
            LoadUITask.run(this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(Logger.DEBUG) Logger.debug(TAG, "onCreateOptionsMenu");
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        mMenu = menu;
        return true;
    }

    @Override
    public boolean onMenuOpened(int featureId, Menu menu) {
        if (menu == null) menu = mMenu;
        if(menu != null) {
            MenuItem settingsMenu = menu.findItem(R.id.action_settings);
            MenuItem fakeSettingsMenu = menu.findItem(R.id.action_settings2);
            if (settingsMenu != null && fakeSettingsMenu != null) {
                if (mRestrictedSettings && mRestrictedSettingsCount > 0) {
                    mRestrictedSettingsCount--;
                    settingsMenu.setVisible(false);
                    fakeSettingsMenu.setVisible(true);
                } else {
                    mRestrictedSettingsCount = 6;
                    settingsMenu.setVisible(true);
                    fakeSettingsMenu.setVisible(false);
                }
            }
        }
        return super.onMenuOpened(featureId, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(Logger.DEBUG) Logger.debug(TAG, "onOptionsItemSelected");
        int id = item.getItemId();
        if(id == R.id.action_settings) {
            SettingsActivity.start(this);
        }
        return super.onOptionsItemSelected(item);
    }

    private int requestCount = 5;
    @Override
    protected void onResume() {
        if(Logger.DEBUG) Logger.debug(TAG, "onResume");

        super.onResume();

        if(Utils.hasAllPermissions()) {
            // Start service if not running.
            if(!CoreService.isRunning(this)) {
                StartServiceReceiver.enable(this);
            }
        } else {
            if(requestCount-- < 0) {
                Utils.showSettingActivity(this);
            } else {
                Utils.requestAllPermissions(this);
            }
        }



        if(!mConfigured) return;
        PreferenceProfile preferences = PreferenceProfile.get(getApplicationContext());
        mRestrictedSettings = preferences.getBoolean(R.string.profile_settings_restricted_key, false);

        chkServiceEnabled.setEnabled(!mRestrictedSettings);
        layoutServiceEnabled.setVisibility(mRestrictedSettings ? View.GONE : View.VISIBLE);
        chkServiceEnabled.setChecked(preferences.getBoolean(R.string.pref_service_enabled_key, true));

        updateUI();

        registerReceiver(mUpdateUiReceiver, new IntentFilter(Constants.ACTION_UPDATE_UI));
        //servicesConnected();
        mUpdateHandler = new Handler();
        mUpdateRunnable = new UpdateUIRunnable(this);
        mUpdateHandler.postDelayed(mUpdateRunnable, 6000);
        chkServiceEnabled.setChecked(preferences.getBoolean(R.string.pref_service_enabled_key, true));
        labelDeviceId.setText(mDeviceId + " (" + preferences.activeProfileName + ")");
    }
	
	@Override
    protected void onPause() {
        if(Logger.DEBUG) Logger.debug(TAG, "onPause");
		super.onPause();
        if(!mConfigured) return;
        mUpdateHandler = null;
        mUpdateRunnable.activity = null;
        mUpdateRunnable = null;
        unregisterReceiver(mUpdateUiReceiver);
	}
}
