package com.hmsoft.nmealogger.ui;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.hmsoft.nmealogger.R;
import com.hmsoft.nmealogger.common.Constants;
import com.hmsoft.nmealogger.common.Logger;
import com.hmsoft.nmealogger.data.ExifGeotager;
import com.hmsoft.nmealogger.data.Geocoder;
import com.hmsoft.nmealogger.data.LocationExporter;
import com.hmsoft.nmealogger.data.LocationStorer;
import com.hmsoft.nmealogger.data.locatrack.LocatrackDb;
import com.hmsoft.nmealogger.data.nmea.NmeaCommon;
import com.hmsoft.nmealogger.data.nmea.NmeaLogFile;
import com.hmsoft.nmealogger.service.LocationService;
import com.hmsoft.nmealogger.service.SyncService;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;

public class MainActivity extends ActionBarActivity {
    private static final String TAG = "MainActivity";

    static Bundle sLastBundle = null;
    static int sGeotagContentUriCount;

    TextView labelLastEntryValue;
    ToggleButton chkAutoGeotag;
    ToggleButton chkServiceEnabled;
    Button btnGeotagNow;
    Menu mMenu;

    private ExifGeotager.GeotagFinishListener mGeoTagContentFinishListener = null;
    int mGeotagContentTotalCount;
    int mGeotagContentTaggedCount;

    private static class GetAddressNameTask extends AsyncTask<Location, Void, String> {
        private final MainActivity mActivity;
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
        }
    }

    private static class LoadUITask extends AsyncTask<Void, Void, Bundle> {

        private final MainActivity mActivity;
        private String mTimeText;
        private int mTimeValue;

        private LoadUITask(MainActivity activity) {
            mActivity = activity;
        }

        public static void run(MainActivity activity) {
            (new LoadUITask(activity)).execute();
        }

        @Override
        protected Bundle doInBackground(Void... params) {
            Bundle values = new Bundle();

            String lastEntryText = mActivity.getString(R.string.generic_none);

            Location lastLocation = LocatrackDb.last();
            if(lastLocation != null) {
                int distanceValue = 0;
                String distanceText = mActivity.getString(R.string.generic_meters);

                Location currentLoc = LocationService.getBestLastLocation(mActivity);
                if(currentLoc != null) {
                    distanceValue = (int)currentLoc.distanceTo(lastLocation);
                    if(distanceValue > 10000) {
                        distanceValue = distanceValue / 1000;
                        distanceText = mActivity.getString(R.string.generic_kilometers);
                    }
                }

                setTimeValueText(lastLocation.getTime(), false);
                String address = Geocoder.getFromCache(lastLocation);
                if(!TextUtils.isEmpty(address)) {
                    lastEntryText = mActivity.getString(R.string.nmea_last_entry_text,
                            mTimeValue, mTimeText, address, distanceValue, distanceText);
                } else {
                    lastEntryText = mActivity.getString(R.string.nmea_last_entry_noaddress_text,
                            mTimeValue, mTimeText, distanceValue, distanceText);
                    (new GetAddressNameTask(mActivity, distanceValue, distanceText,
                            mTimeValue, mTimeText)).execute(lastLocation);
                }
            }


            values.putString(String.valueOf(mActivity.labelLastEntryValue.getId()),
                    lastEntryText);


           return values;
        }

        @Override
        protected void onPostExecute(Bundle result) {
            sLastBundle = result;
            mActivity.setUiValuesFromBundle(result);
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
            } else if(days > 1) {
                mTimeText = mActivity.getString(R.string.generic_days);
                mTimeValue = days;
            } else if(hours == 1) {
                mTimeValue = 1;
                mTimeText = mActivity.getString(R.string.generic_hour);
            } else if(hours > 1) {
                mTimeValue = hours;
                mTimeText = mActivity.getString(R.string.generic_hours);
            } else if(minutes == 1) {
                mTimeValue = 1;
                mTimeText = mActivity.getString(R.string.generic_minute);
            } else if(minutes > 1) {
                mTimeValue = minutes;
                mTimeText = mActivity.getString(R.string.generic_minutes);
            } else {
                mTimeValue = seconds;
                mTimeText = mActivity.getString(R.string.generic_seconds);
            }
        }
    }

    // Define a DialogFragment that displays the error dialog
    public static class ErrorDialogFragment extends DialogFragment {
        // Global field to contain the error dialog
        private Dialog mDialog;
        // Default constructor. Sets the dialog field to null
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }
        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }
        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    private BroadcastReceiver mUpdateUiReceiver = new BroadcastReceiver() {
		@Override
        public void onReceive(Context context, Intent intent) {
            if(Logger.DEBUG) Logger.debug(TAG, "onReceive");
			MainActivity.this.updateUI();
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

    private void servicesConnected() {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if(prefs.getBoolean("gms_checked", false)) {
            return;
        }

        prefs.edit().putBoolean("gms_checked", true).apply();

        // Check that Google Play services is available
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        // If Google Play services is available
        if (ConnectionResult.SUCCESS == resultCode) {
            // In debug mode, log the status
            if(Logger.DEBUG) Logger.debug(TAG, "Google Play services is available.");
            // Continue
            return ;
            // Google Play services was not available for some reason
        } else {
            // Get the error code
            int errorCode = resultCode;
            // Get the error dialog from Google Play services
            Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
                    errorCode,
                    this,
                    1);

            // If Google Play services can provide an error dialog
            if (errorDialog != null) {
                // Create a new DialogFragment for the error dialog
                ErrorDialogFragment errorFragment =
                        new ErrorDialogFragment();
                // Set the dialog in the DialogFragment
                errorFragment.setDialog(errorDialog);
                // Show the error dialog in the DialogFragment
                errorFragment.show(getSupportFragmentManager(),
                        "Location Updates");
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if(Logger.DEBUG) Logger.debug(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SettingsActivity.setPrefDefaults(this);

        labelLastEntryValue = (TextView)findViewById(R.id.labelLastEntryValue);
        chkAutoGeotag = (ToggleButton)findViewById(R.id.chkAutoGeotag);
        chkServiceEnabled = (ToggleButton)findViewById(R.id.chkServiceEnabled);
        btnGeotagNow = (Button)findViewById(R.id.btnGeotagNow);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        chkAutoGeotag.setChecked(pref.getBoolean(getString(R.string.pref_auto_exif_geotager_enabled_key),
                true));

        chkServiceEnabled.setChecked(pref.getBoolean(getString(R.string.pref_service_enabled_key),
                true));

        btnGeotagNow.setEnabled(sGeotagContentUriCount <= 0);

        if(savedInstanceState == null) savedInstanceState = sLastBundle;
        setUiValuesFromBundle(savedInstanceState);

        ((TextView)findViewById(R.id.labelVersion)).setText(String.format("%s - %s",
                getString(R.string.app_name), Constants.VERSION_STRING));

       // Start service if not running.
        if(!LocationService.isRunning(this)) {
            LocationService.start(this);
        }
    }


    @Override
    protected void onSaveInstanceState(Bundle values) {
        if(Logger.DEBUG) Logger.debug(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(values);
        saveTextToBundle(labelLastEntryValue, values);
    }
	
	public void updateLocation(View view) {
		LocationService.updateLocation(this);
	}
	
	public void showFileOptions(View view) {
		// TODO: Implement
	}

    public void toggleTrackingMode(MenuItem item) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean trackingMode = !prefs.getBoolean(Constants.PREF_TRAKING_MODE_KEY, false);
        if(trackingMode) {
            LocationService.startTrackingMode(this);
        } else {
            LocationService.stopTrackingMode(this);
        }
        int iconId = trackingMode ? R.drawable.ic_action_traking : R.drawable.ic_action_not_traking;
        item.setIcon(iconId);
        item.setChecked(trackingMode);
    }

    public void setServiceEnabled(View view) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        String key = getString(R.string.pref_service_enabled_key);
        boolean serviceEnabled = preferences.getBoolean(key, true);
        serviceEnabled = !serviceEnabled;
        preferences.edit().putBoolean(key, serviceEnabled).apply();
        if(serviceEnabled) {
            LocationService.enable(this);
        } else {
            LocationService.configure(this);            
        }        
    }

    public void setAutoGeotag(View view) {
        boolean enable = chkAutoGeotag.isChecked();
        LocationService.setAutoExifGeotag(this, enable);
    }

    public void geotagPictures(View view) {
        final Context context = this;
        if(mGeoTagContentFinishListener ==null) {
            mGeoTagContentFinishListener = new ExifGeotager.GeotagFinishListener() {
                @Override
                protected void onGeotagTaskFinished(int totalCount, int geotagedCount) {
                    mGeotagContentTotalCount += totalCount;
                    mGeotagContentTaggedCount += geotagedCount;
                    if(--sGeotagContentUriCount <= 0) {
                        btnGeotagNow.setEnabled(true);
                        ExifGeotager.notify(context, mGeotagContentTotalCount, mGeotagContentTaggedCount);
                        String text;
                        if(mGeotagContentTotalCount == mGeotagContentTaggedCount) {
                            text = context.getString(R.string.geotagger_notify_text_ok, mGeotagContentTotalCount);
                        } else {
                            text = context.getString(R.string.geotagger_notify_text_failed,
                                    mGeotagContentTaggedCount, mGeotagContentTotalCount);
                        }
                        Toast.makeText(context, text, Toast.LENGTH_LONG).show();
                    }
                }
            };
        }

        btnGeotagNow.setEnabled(false);
        LocatrackDb storer = new LocatrackDb(this);
        storer.configure();
        storer.setOnCloseCallback(new LocationStorer.OnCloseCallback() {
            @Override
            public void onClose(Bundle extras, Exception error) {
                if (error == null) {
                    sGeotagContentUriCount = 2;
                    mGeotagContentTotalCount = 0;
                    mGeotagContentTaggedCount = 0;
                    ExifGeotager.geoTagContent(context,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 
                            true, true, mGeoTagContentFinishListener);
                    ExifGeotager.geoTagContent(context,
                            MediaStore.Images.Media.INTERNAL_CONTENT_URI, 
                            true, true, mGeoTagContentFinishListener);
                } else {
                    if(Logger.DEBUG) Logger.debug(TAG, "Error on LocatrackDatabase, %s", error.getMessage());
                    btnGeotagNow.setEnabled(true);
                }
            }
        });

        SyncService.exportNmeaToStorer(this, storer, LocatrackDb.getLastLocationTime(),  0);
    }

    private void updateUI() {
        if(mMenu != null) {
            updateTrackingMenuItem(mMenu.findItem(R.id.action_toggle_trackmode));
        }
        LoadUITask.run(this);
    }

    private void updateTrackingMenuItem(MenuItem item) {
        boolean isTracking = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean(Constants.PREF_TRAKING_MODE_KEY, false);

        item.setChecked(isTracking);
        int iconId = isTracking ? R.drawable.ic_action_traking : R.drawable.ic_action_not_traking;
        item.setIcon(iconId);
        item.setChecked(isTracking);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(Logger.DEBUG) Logger.debug(TAG, "onCreateOptionsMenu");
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        mMenu = menu;

        MenuItem menuItemToggleTRackingMode = menu.findItem(R.id.action_toggle_trackmode);
        updateTrackingMenuItem(menuItemToggleTRackingMode);

        menuItemToggleTRackingMode.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                toggleTrackingMode(item);
                return false;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(Logger.DEBUG) Logger.debug(TAG, "onOptionsItemSelected");
        LocationStorer storer = null;
        long starTimeFilter = 0;
        int successTextId = 0;
        int id = item.getItemId();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if(id == R.id.action_settings) {
            SettingsActivity.start(this);
        } if(id == R.id.action_webserver) {
            WebServerActivity.start(this);
        } else if (id == R.id.action_export_gpx) {
            storer = new LocationExporter.FileStorer(this);
            ((LocationExporter.FileStorer) storer).setFormat(LocationExporter.FileStorer.Format.GPX);
            starTimeFilter = cal.getTimeInMillis();
        } else if (id == R.id.action_export_kml) {
            storer = new LocationExporter.FileStorer(this);
            ((LocationExporter.FileStorer) storer).setFormat(LocationExporter.FileStorer.Format.KML);
            starTimeFilter = cal.getTimeInMillis();
        } else if (id == R.id.action_locatrac_sync) {
            storer = new LocatrackDb(this);
            storer.configure();
            final Context context = this;
            storer.setOnCloseCallback(new LocationStorer.OnCloseCallback() {
                @Override
                public void onClose(Bundle extras, Exception error) {
                    if (error == null) {
                        SyncService.syncNow(context);
                    } else {
                        if(Logger.DEBUG) Logger.debug(TAG, "Error on LocatrackDatabase, %s", error.getMessage());
                    }
                }
            });
            starTimeFilter = LocatrackDb.getLastLocationTime();
            successTextId = R.string.export_complete_sync;
        }

        if (storer != null) {
            SyncService.exportNmeaToStorer(this, storer, starTimeFilter, successTextId);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        if(Logger.DEBUG) Logger.debug(TAG, "onResume");
        super.onResume();
        updateUI();
		registerReceiver(mUpdateUiReceiver, new IntentFilter(Constants.ACTION_UPDATE_UI));
        servicesConnected();
    }
	
	@Override
    protected void onPause() {
        if(Logger.DEBUG) Logger.debug(TAG, "onPause");
		super.onPause();
        unregisterReceiver(mUpdateUiReceiver);
	}
}
