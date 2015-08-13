package com.hmsoft.locationlogger.service;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.text.TextUtils;
import android.widget.Toast;

import com.hmsoft.locationlogger.BuildConfig;
import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Constants;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.PerfWatch;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.data.Geocoder;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;
import com.hmsoft.locationlogger.data.locatrack.LocatrackDb;
import com.hmsoft.locationlogger.data.locatrack.LocatrackOnlineStorer;
import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;
import com.hmsoft.locationlogger.ui.MainActivity;

import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LocationService extends Service /*implements GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener*/ {

    //region Static fields
    private static final String TAG = "LocationService";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final boolean DIAGNOSTICS = DEBUG;
    private static final int HALF_MINUTE = 1000 * 30;

    //endregion Static fields

    //region Settings fields
    boolean mVehicleMode = false;
    private int mMinimumDistance = 20; //meters
    private int mGpsTimeout = 60; //seconds
    /*private*/ boolean mRequestPassiveLocationUpdates = true;
    boolean mNotifyEvents;
    boolean mRestrictedSettings;
    boolean mSetAirplaneMode = false;
    private float mMaxReasonableSpeed = 55; // meters/seconds
    private int mMinimumAccuracy = 750; // meters
    private int mBestAccuracy = 6;
    private boolean mNotificationEnabled = true;
    private boolean mWakeLockEnabled = false;
    private boolean mLocationLogEnabled = false;
    private int mSyncHour = 0;
    private int mSyncMinute = 30;
    LocationManager mLocationManager;
    private boolean mNetProviderEnabled;
    private boolean mGpsProviderEnabled;
    private boolean mTimeoutRoutinePending;
    private LocationListener mNetLocationListener;
    private LocationListener mGpsLocationListener;
    private LocationListener mPassiveLocationListener;
    //private LocationListener mGmsLocationListener;
    private LocatrackLocation mCurrentBestLocation;
    private PowerManager mPowerManager;
    private ComponentName mMapIntentComponent = null;
    //private boolean mUseGmsIgAvailable;
    private boolean mInstantUploadEnabled = false;
    private PreferenceProfile mPreferences;

    //endregion Settings fields

    //region UI Data fields
    private Location mLastSavedLocation = null;
    private int mLocationCount = 0;
    String mLastSaveAddress = null;
    //endregion UI Data fields

    //region Core fields
    private AlarmManager mAlarm = null;
    private PendingIntent mAlarmLocationCallback = null;
    private PendingIntent mAlarmSyncCallback = null;
    private PendingIntent mLocationActivityIntent = null;
    private Intent mMapIntent = null;
    private PendingIntent mUpdateLocationIntent = null;
    //private LocationRequest mLocationRequest = null;
    //private LocationClient mGpLocationClient = null;
    private final boolean mGooglePlayServiceAvailable = false;

    private LocatrackOnlineStorer mOnlineStorer = null;
    LocationStorer mLocationStorer;

    boolean mNeedsToUpdateUI;
    private WakeLock mWakeLock;

    private HandlerThread mExecutorThread = null;

    int mLastBatteryLevel = 99;
    boolean mChargingStart;
    boolean mChargingStop;
    //endregion Core fields

    //region Helper Inner Classes

    public static class StartServiceReceiver extends BroadcastReceiver {

        private static final String TAG = "StartServiceReceiver";

        @Override
        public void onReceive(final Context context, Intent intent) {
            if(Logger.DEBUG) {
                Logger.debug(TAG, "onReceive:%s", intent);
                Toast.makeText(context, "" + intent, Toast.LENGTH_LONG).show();
            }
            LocationService.start(context);
        }
    }

    private static class ActionReceiver extends BroadcastReceiver {
        private static final String TAG = "UserPresentReceiver";

        private static ActionReceiver sInstance;

        private LocationService mService;

        private ActionReceiver(LocationService service) {
            mService = service;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if(Logger.DEBUG) Logger.debug(TAG, "onReceive:" + intent.getAction());
            switch (intent.getAction()) {
                case Intent.ACTION_USER_PRESENT:
                    mService.handleUserPresent();
                    break;
                case Intent.ACTION_BATTERY_CHANGED:
                    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    boolean plugged = (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0);

                    boolean charging = (
                            (status == BatteryManager.BATTERY_STATUS_CHARGING) ||
                                    ((status == BatteryManager.BATTERY_STATUS_FULL) && plugged)
                    );

                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                    if (charging) level += 100;

                    mService.handleBatteryLevelChange(level);
                    break;
            }
        }

        public static void register(LocationService service){
            if(sInstance == null) {
                sInstance = new ActionReceiver(service);
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                filter.addAction(Intent.ACTION_USER_PRESENT);
                service.getApplicationContext().registerReceiver(sInstance, filter);
            }
        }

        public static void unregister(Context context) {
            if(sInstance != null) {
                context.getApplicationContext().unregisterReceiver(sInstance);
                sInstance.mService = null;
                sInstance = null;
            }
        }
    }

    private static class LocationListener implements android.location.LocationListener
            /*, com.google.android.gms.location.LocationListener*/ {

        private static final String TAG = "LocationListener";

        LocationService mService;
        String mProvider;

        public LocationListener(LocationService service, String provider) {
            mService = service;
            mProvider = provider;
        }

        @Override
        public void onLocationChanged(Location location) {
            if(Logger.DEBUG) Logger.debug(TAG, "onLocationChanged:%s", mProvider);
            if(mService != null) mService.handleLocation(location, mProvider);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }
    }

    private static class GetAddressTask extends AsyncTask<Location, Void, String> {
        private static final String TAG = "GetAddressTask";

        private static GetAddressTask sInstance = null;

        private LocationService mService;

        private GetAddressTask(LocationService service) {
            super();
            mService = service;
            sInstance = this;
        }

        public void run(Location location) {
            execute(location);
        }

        @Override
        protected String doInBackground(Location... params) {
            if(Logger.DEBUG) Logger.debug(TAG, "doInBackground");
			Location location = params[0];
			String address = Geocoder.getFromRemote(mService, location);
			if (!TextUtils.isEmpty(address)) {
				Geocoder.addToCache(location, address);
			}
            return address;
        }

        @Override
        protected void onCancelled() {
            sInstance = null;
            mService = null;
        }

        @Override
        protected void onPostExecute(String address) {
            if (!TextUtils.isEmpty(address)) {
                if(Logger.DEBUG) Logger.debug(TAG, "onPostExecute");
                mService.mLastSaveAddress = address;
                mService.updateNotification();
            }
            mService = null;
            sInstance = null;
        }

        public static void run(LocationService service, Location location) {
            if (sInstance == null) {
                (new GetAddressTask(service)).run(location);
            }
        }
    }

    private static class UploadHandler extends Handler {

        private static final int UPLOAD_LOCATION_MSG = 19830310;

        private LocationService mService;
        private static UploadHandler sInstance = null;

        private UploadHandler(LocationService service, Looper looper) {
            super(looper);
            mService = service;
        }

        private void _sendUploadLocationMsg(LocatrackLocation location) {
            Message msg = obtainMessage(UPLOAD_LOCATION_MSG, location);
            sendMessage(msg);
        }

        public static void init(LocationService service, Looper looper) {
            if(sInstance == null) {
                sInstance = new UploadHandler(service, looper);
            }
        }

        public static void sendUploadLocationMsg(LocatrackLocation location) {
            if(sInstance != null) sInstance._sendUploadLocationMsg(location);
        }

        public static void destroy() {
            if(sInstance != null) {
                sInstance.mService = null;
                sInstance = null;
            }
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPLOAD_LOCATION_MSG:
                    LocatrackLocation location = (LocatrackLocation)msg.obj;
                    mService.uploadLocation(location);
            }
        }
    }

    //endregion Helper Inner Classes

    //region Google Play location service helper functions

    /*private void requestGooglePlayLocationUpdates() {
        if(Logger.DEBUG) Logger.debug(TAG, "requestGooglePlayLocationUpdates");
        long time = mGpsTimeout;
        if (!mTrackingMode) {
            time = mGpsTimeout / 4;
        }

        float minDistance = mMinimumDistance / 2;

        if(mGmsLocationListener == null) {
            mGmsLocationListener = new LocationListener(this, "gms");
        }

        mLocationRequest = new LocationRequest();
        mLocationRequest.setFastestInterval(time);
        mLocationRequest.setInterval(time);
        mLocationRequest.setSmallestDisplacement(minDistance);
        //TODO: Evaluate this
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        mGpLocationClient.requestLocationUpdates(mLocationRequest, mGmsLocationListener);

        startPassiveLocationListener();
    }*/

    //endregion Google Play location service helper functions

    //region Core functions

    void handleUserPresent() {
        mNeedsToUpdateUI = true;
        updateNotification();
    }

    void handleBatteryLevelChange(int newLevel) {
        if (mLastBatteryLevel <= 100 && newLevel > 100) {
            SyncService.setAutoSync(getApplicationContext(), true);
            Logger.info(TAG, "Charging start");
            mChargingStart = true;
            mChargingStop = false;
        } else if (mLastBatteryLevel > 100 && newLevel <= 100) {
            SyncService.setAutoSync(getApplicationContext(), false);
            Logger.info(TAG, "Charging stop");
            mChargingStop = true;
            mChargingStart = false;
        }
        mLastBatteryLevel = newLevel;
        if (mChargingStart || mChargingStop) {
            destroyExecutorThread(); // Start with a new created thread
            acquireWakeLock();
            startLocationListener();
            int intv = -1;
            if(mChargingStop && mPreferences.getInterval(mLastBatteryLevel)  > 900) {
                intv = 900;
            }
            setLocationAlarm(intv);
        }
    }

    void handleLocation(Location location, String provider) {

        if (mCurrentBestLocation != null &&
                (mCurrentBestLocation.getTime() == location.getTime())) {
            logLocation(location, "Location is the same location that currentBestLocation");
            return;
        }

        String message;

        long timeDelta = HALF_MINUTE;

        if(Logger.DEBUG) Logger.debug(TAG, "handleLocation %s", location);

        if (isBetterLocation(location, mCurrentBestLocation, timeDelta, mMinimumAccuracy,
                mMaxReasonableSpeed)) {

            if(!mVehicleMode && LocationManager.PASSIVE_PROVIDER.equals(provider)) {
                if(/*mLocationRequest == null ||*/ mLocationManager == null) {
                    mCurrentBestLocation = new LocatrackLocation(location);
                    saveLocation(mCurrentBestLocation);
                    message = "*** Location saved (passive)";
                } else {
                    message = "Ignored passive location while in location request.";
                }
            } else {
                mCurrentBestLocation = new LocatrackLocation(location);
                if ((!mGpsProviderEnabled && !mGooglePlayServiceAvailable) ||
                        (isFromGps(mCurrentBestLocation) && location.getAccuracy() <= mBestAccuracy)) {
                    saveLocation(mCurrentBestLocation, true);
                    message = "*** Location saved";
                    stopLocationListener();
                } else {
                    message = "No good GPS location.";
                }
            }
        } else {
            message = "Location is not better than last location.";
        }

        logLocation(location, message);
    }

    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        return isBetterLocation(location, currentBestLocation, HALF_MINUTE, mMinimumAccuracy,
                mMaxReasonableSpeed);
    }

    private static boolean isFromGps(Location location) {
        return LocationManager.GPS_PROVIDER.equals(location.getProvider()) ||
                location.hasAltitude() || location.hasBearing() || location.hasSpeed();
    }

     /**
     * Determines whether one Location reading is better than the current Location fix
     *
     * @param location            The new Location that you want to evaluate
     * @param currentBestLocation The current Location fix, to which you want to compare the new one
     */
    private static boolean isBetterLocation(Location location, Location currentBestLocation,
                        long minTimeDelta, int minimumAccuracy, float maxReasonableSpeed) {

        if (location == null) {
            // A new location is always better than no location
            return false;
        }

        if (location.getAccuracy() > minimumAccuracy) {
            if(Logger.DEBUG) Logger.debug(TAG, "Location below min accuracy of %d meters", minimumAccuracy);
            return false;
        }

        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }


        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        long timeDelta = location.getTime() - currentBestLocation.getTime();

        if(isFromSameProvider || !isFromGps(location)) {
            float meters = location.distanceTo(currentBestLocation);
            long seconds = timeDelta / 1000L;
            float speed = meters / seconds;
            if (speed > maxReasonableSpeed) {
                if (Logger.DEBUG)
                    Logger.debug(TAG, "Super speed detected. %f meters from last location", meters);
                return false;
            }
        }

        // Check whether the new location fix is newer or older
        boolean isSignificantlyNewer = timeDelta > minTimeDelta;
        boolean isSignificantlyOlder = timeDelta < -minTimeDelta;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /**
     * Checks whether two providers are the same
     */
    private static boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    private void saveLastLocation() {
        if(Logger.DEBUG) Logger.debug(TAG, "saveLastLocation");

        if ((!mChargingStart && !mChargingStop) && (mCurrentBestLocation  != null && mLastSavedLocation != null) &&
                (mCurrentBestLocation == mLastSavedLocation ||
                mCurrentBestLocation.getTime() == mLastSavedLocation.getTime())) {
            logLocation(null, "currentBestLocation is the same lastSavedLocation. Saving nothing...");
            releaseWakeLock();
            mChargingStart = false;
            mChargingStop = false;
            return;
        }

        if (mCurrentBestLocation != null && isFromGps(mCurrentBestLocation)) {
            if(Logger.DEBUG) Logger.debug(TAG, "currentBestLocation is from GPS");
            saveLocation(mCurrentBestLocation, true);
            logLocation(mCurrentBestLocation, "*** Location saved (current best)");
            return;
        }

        LocatrackLocation bestLastLocation = mCurrentBestLocation;

        if (mGooglePlayServiceAvailable) {
            /*Location lastKnownGmsLocation  = mGpLocationClient.getLastLocation();
            if (isBetterLocation(lastKnownGmsLocation, bestLastLocation)) {
                bestLastLocation = new LocatrackLocation(lastKnownGmsLocation);
            }*/
        } else {
            if (mGpsProviderEnabled) {
                if(Logger.DEBUG) Logger.debug(TAG, "currentBestLocation is not from GPS, but GPS is enabled");
                Location lastKnownGpsLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (isBetterLocation(lastKnownGpsLocation, bestLastLocation)) {
                    if(Logger.DEBUG) Logger.debug(TAG, "Got good LastKnownLocation from GPS provider.");
                    bestLastLocation = new LocatrackLocation(lastKnownGpsLocation);
                } else {
                    if(Logger.DEBUG) Logger.debug(TAG, "LastKnownLocation from GPS provider is not better than currentBestLocation.");
                }
            }

            if (mNetProviderEnabled) {
                Location lastKnownNetLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (isBetterLocation(lastKnownNetLocation, bestLastLocation)) {
                    bestLastLocation = new LocatrackLocation(lastKnownNetLocation);
                }
            }
        }

        if (bestLastLocation != null) {
            saveLocation(bestLastLocation, true);
            logLocation(bestLastLocation, "*** Location saved (best last)");
            if(mCurrentBestLocation == null) {
                mCurrentBestLocation = bestLastLocation;
            }
        } else if (DEBUG) {
            logLocation(null, "No last location. Turn on GPS!");
            releaseWakeLock();
            mChargingStart = false;
            mChargingStop = false;
        }
    }


    /* Must not be called in the UI thread */
    void uploadLocation(final LocatrackLocation location) {

        final Context context = getApplicationContext();
        if (mOnlineStorer == null) {
            mOnlineStorer = new LocatrackOnlineStorer(context);
            mOnlineStorer.configure();
        }
        boolean locationUploaded = false;
        try {
            if (DIAGNOSTICS && mLocationLogEnabled) {
                Logger.info(TAG, "Upload: %s", location);
            }

            PerfWatch pw = null;

            if (DIAGNOSTICS && mLocationLogEnabled) {
                pw = PerfWatch.start(TAG, "Start: Upload location");
            }

            if (mSetAirplaneMode && mLastBatteryLevel <= 100) {
                mOnlineStorer.retryDelaySeconds = 10;
                mOnlineStorer.retryCount = 3;
            } else {
                mOnlineStorer.retryDelaySeconds = 3;
                mOnlineStorer.retryCount = 1;
            }
            locationUploaded = mOnlineStorer.storeLocation(location);
            if (DIAGNOSTICS && mLocationLogEnabled) {
                if (pw != null) {
                    pw.stop(TAG, "End: Upload location Success: " + locationUploaded);
                }
            }
            if (mSetAirplaneMode && mLastBatteryLevel <= 100) {
                setAirplaneMode(context, true);
            }
        } finally {
            final boolean uploaded = locationUploaded;
            TaskExecutor.executeOnUIThread(new Runnable() {
                @Override
                public void run() {
                    releaseWakeLock();
                    if (uploaded) {
                        mLocationStorer.setUploadDateToday(location);
                    }
                }
            });
        }
    }

    private void saveLocation(final LocatrackLocation location) {
        saveLocation(location, false);
    }
    
    private void saveLocation(final LocatrackLocation location, final boolean upload) {

        location.batteryLevel = mLastBatteryLevel;
        if(mNotifyEvents) {
            if(mChargingStart) {
                location.event = LocatrackLocation.EVENT_START;
            } else if(mChargingStop) {
                location.event = LocatrackLocation.EVENT_STOP;
            }
            mChargingStart = false;
            mChargingStop = false;
        }

        mLocationStorer.storeLocation(location);

        mLastSaveAddress = null;
        mLastSavedLocation = location;
        mLocationCount++;
        updateUIIfNeeded();

        if(upload) {
            if (mInstantUploadEnabled) {
                sendUploadLocationMsg(location);
            } else {
                releaseWakeLock();
            }
        }
    }

    private void sendUploadLocationMsg(LocatrackLocation location) {
        if(mLocationCount % 100 == 0) destroyExecutorThread();
        if(mExecutorThread == null) {
            mExecutorThread = new HandlerThread(BuildConfig.APPLICATION_ID + "." + TAG);
            mExecutorThread.start();
            Looper looper = mExecutorThread.getLooper();
            UploadHandler.init(this, looper);
            Logger.info(TAG, "ExecutorThread created");
            if(DEBUG) Toast.makeText(this, "ExecutorThread created", Toast.LENGTH_SHORT).show();
        }
        UploadHandler.sendUploadLocationMsg(location);
    }

    void destroyExecutorThread() {
        if(mExecutorThread != null) {
            mExecutorThread.quit();
            mExecutorThread = null;
            Logger.info(TAG, "ExecutorThread destroyed");
            if(DEBUG) Toast.makeText(this, "ExecutorThread destroyed", Toast.LENGTH_SHORT).show();
        }
        UploadHandler.destroy();
    }

    private void logLocation(Location location, String message) {
        if(DEBUG) Logger.debug(TAG, message);
        if (mLocationLogEnabled) {
            String locationStr = "NOLOC";
            if(location != null) locationStr = location.toString();
            Logger.log2file(message, locationStr, "locations-%s.log", null);
        }
    }

    public void acquireWakeLock() {
        if (mWakeLockEnabled) {
            if (mWakeLock == null) {
                mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Utils");
                mWakeLock.setReferenceCounted(false);
            }
            if(!mWakeLock.isHeld()) {
                if (DIAGNOSTICS && mLocationLogEnabled) Logger.info(TAG, "acquireLocationLock");
                mWakeLock.acquire();
            }
        }
    }

    public void releaseWakeLock() {
        if (mWakeLock != null) {
            if(DIAGNOSTICS && mLocationLogEnabled) Logger.info(TAG, "releaseLocationLock");
            mWakeLock.release();
        }
    }

    void updateUIIfNeeded() {
        if (mNeedsToUpdateUI) {
            if (mPowerManager.isScreenOn()) {                
                updateNotification();
				sendBroadcast(new Intent(Constants.ACTION_UPDATE_UI));
            } else {
                mNeedsToUpdateUI = false;
            }
        }
    }

    void updateNotification() {
        if (mNotificationEnabled) {
            if(Logger.DEBUG) Logger.debug(TAG, "updateNotification");

            Context context = getApplicationContext();

            if (mLocationActivityIntent == null) {
                Intent activityIntent = new Intent(context, MainActivity.class);
                activityIntent.setAction(Intent.ACTION_MAIN);
                activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NEW_TASK);
                mLocationActivityIntent = PendingIntent.getActivity(this, 0, activityIntent, 0);
            }

            long when = 0;
            int accuracy = 0;
            String contentTitle;
            PendingIntent mapPendingIntent = null;
            if (mLastSavedLocation != null) {
                if (mLastSaveAddress == null) {
                    mLastSaveAddress = Geocoder.getFromCache(mLastSavedLocation);
                    if (mLastSaveAddress == null) {
                        GetAddressTask.run(this, mLastSavedLocation);
                    }
                }

                Uri mapUri = Uri.parse(String.format("geo:%f,%f?z=%d", mLastSavedLocation.getLatitude(),
                        mLastSavedLocation.getLongitude(), 18));

                if (mMapIntent == null) {
                    mMapIntent = new Intent(Intent.ACTION_VIEW, mapUri);
                    mMapIntentComponent = mMapIntent.resolveActivity(getPackageManager());
                }

                if (mMapIntentComponent != null) {
                    mMapIntent.setData(mapUri);
                    mapPendingIntent = PendingIntent.getActivity(this, 0, mMapIntent, 0);
                }

                when = mLastSavedLocation.getTime();
                accuracy = Math.round(mLastSavedLocation.getAccuracy());
            }

            if (!TextUtils.isEmpty(mLastSaveAddress)) {
                contentTitle = mLastSaveAddress;
            } else {
                contentTitle = (mLastSavedLocation != null ?
                        String.format(Locale.ENGLISH, "%f,%f", mLastSavedLocation.getLatitude(), mLastSavedLocation.getLongitude()) :
                        getString(R.string.app_name));
            }

            Builder notificationBuilder = (new NotificationCompat.Builder(this)).
                    setSmallIcon(R.drawable.ic_stat_service).
                    setContentIntent(mLocationActivityIntent).
                    setWhen(when).
                    setAutoCancel(false).
                    setOngoing(true).
                    setContentTitle(contentTitle).
                    setPriority(NotificationCompat.PRIORITY_MAX);


            if (mapPendingIntent != null) {
                notificationBuilder.setContentIntent(mapPendingIntent);
            }

            if (mLocationCount > -1) {
                notificationBuilder.setContentText(getString(R.string.service_content,
                        mLocationCount, accuracy, mPreferences.activeProfileName));
            } else {
                notificationBuilder.setContentText(mPreferences.activeProfileName);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && !mRestrictedSettings) {

                if (mUpdateLocationIntent == null) {
                    Intent updateIntent = new Intent(context, LocationService.class);
                    updateIntent.putExtra(Constants.EXTRA_UPDATE_LOCATION, 1);
                    updateIntent.setAction(Constants.ACTION_NOTIFICATION_UPDATE_LOCATION);
                    mUpdateLocationIntent = PendingIntent.getService(context, 0, updateIntent, 0);
                }
                notificationBuilder.addAction(R.drawable.ic_action_place,
                        getString(R.string.action_update_location), mUpdateLocationIntent);

            }

            Notification notif = notificationBuilder.build();
            startForeground(1, notif);
        }
    }

    void startLocationListener() {
        if(mSetAirplaneMode) setAirplaneMode(this, false);
        if(mGooglePlayServiceAvailable) {
            /*if (mLocationRequest == null) {
                if(Logger.DEBUG) Logger.debug(TAG, "startLocationListener: Google Play Services available.");
                if(mGpLocationClient == null) {
                    mGpLocationClient = new LocationClient(this, this, this);
                }
                if (mGpLocationClient.isConnected()) {
                    requestGooglePlayLocationUpdates();
                } else if (!mGpLocationClient.isConnecting()) {
                    mGpLocationClient.connect();
                    if(Logger.DEBUG) Logger.debug(TAG, "Connecting Google Pay Location Service Client");

                }
            }*/
        } else if (mLocationManager == null) {

            if(Logger.DEBUG) Logger.debug(TAG, "startLocationListener: No Google Play Services available. Fallback to old location listeners.");

            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            mNetProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            mGpsProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

            long time = mGpsTimeout;
            time = mGpsTimeout / 4;

            float minDistance = mMinimumDistance / 2;

            if (mNetProviderEnabled) {
                if (mNetLocationListener == null) {
                    mNetLocationListener = new LocationListener(this, LocationManager.NETWORK_PROVIDER);
                }
                mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, time, minDistance, mNetLocationListener);
                if(Logger.DEBUG) Logger.debug(TAG, "requestLocationUpdates for %s", mNetLocationListener.mProvider);
            }

            if (mGpsProviderEnabled) {
                if (mGpsLocationListener == null) {
                    mGpsLocationListener = new LocationListener(this, LocationManager.GPS_PROVIDER);
                }
                mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, time, minDistance, mGpsLocationListener);
                if(Logger.DEBUG) Logger.debug(TAG, "requestLocationUpdates for %s", mGpsLocationListener.mProvider);
            }

            startPassiveLocationListener();
        }

        if (!mTimeoutRoutinePending) {
            if(Logger.DEBUG) Logger.debug(TAG, "Executing gps timeout in %d seconds", mGpsTimeout);
            mTimeoutRoutinePending = true;
            TaskExecutor.executeOnUIThread(new Runnable() {
                @Override
                public void run() {
                    mTimeoutRoutinePending = false;
                    if (mLocationManager != null /*|| mLocationRequest != null*/) {
                        if(Logger.DEBUG) Logger.debug(TAG, "GPS Timeout");
                        saveLastLocation();
                        stopLocationListener();
                    }
                }
            }, mGpsTimeout);
        }
    }

    private void startPassiveLocationListener() {
        if (mRequestPassiveLocationUpdates && mPassiveLocationListener == null) {
            if(Logger.DEBUG) Logger.debug(TAG, "startPassiveLocationListener");
            mPassiveLocationListener = new LocationListener(this, LocationManager.PASSIVE_PROVIDER);

            if(mGooglePlayServiceAvailable) {
               /* LocationRequest passiveRequest = new LocationRequest();
                passiveRequest.setInterval(2000);
                passiveRequest.setFastestInterval(1750);
                passiveRequest.setSmallestDisplacement(mMinimumDistance / 2);
                passiveRequest.setPriority(LocationRequest.PRIORITY_NO_POWER);
                mGpLocationClient.requestLocationUpdates(passiveRequest, mPassiveLocationListener);*/
            } else {
                LocationManager locationManager = this.mLocationManager;
                if (locationManager == null) {
                    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                }
                locationManager.requestLocationUpdates (LocationManager.PASSIVE_PROVIDER, 2000, mMinimumDistance / 2,
                        mPassiveLocationListener);

            }
        }
    }

    private void stopPassiveLocationListener() {
        if (mPassiveLocationListener != null) {
            if(mGooglePlayServiceAvailable) {
                //mGpLocationClient.removeLocationUpdates(mPassiveLocationListener);
                if(Logger.DEBUG) Logger.debug(TAG, "stopPassiveLocationListener: Google Play Location");

            } else {
                LocationManager locationManager = this.mLocationManager;
                if (locationManager == null) {
                    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                }

                locationManager.removeUpdates(mPassiveLocationListener);
                if(Logger.DEBUG) Logger.debug(TAG, "stopPassiveLocationListener:Android Location");

            }
            mPassiveLocationListener.mService = null;
            mPassiveLocationListener = null;
        }
    }

    private void stopLocationListener() {
        if(mGooglePlayServiceAvailable) {
            /*if(mLocationRequest != null) {
                if(Logger.DEBUG) Logger.debug(TAG, "stopLocationListener:%s", mGmsLocationListener.mProvider);
                mLocationRequest = null;
                if(mGpLocationClient != null) {
                    mGpLocationClient.removeLocationUpdates(mGmsLocationListener);
                    mGmsLocationListener.mService = null;
                    mGmsLocationListener = null;
                }
            }
            if (mPassiveLocationListener == null && mGpLocationClient != null) {
                if(Logger.DEBUG) Logger.debug(TAG, "Disconnecting Play Services...");
                if(mGpLocationClient.isConnected()) mGpLocationClient.disconnect();
                mGpLocationClient = null;
            }*/
        } else {
            if (mLocationManager != null) {
                if (mGpsLocationListener != null) {
                    mLocationManager.removeUpdates(mGpsLocationListener);
                    if(Logger.DEBUG) Logger.debug(TAG, "stopLocationListener:%s", mGpsLocationListener.mProvider);
                    mGpsLocationListener.mService = null;
                    mGpsLocationListener = null;
                }
                if (mNetLocationListener != null) {
                    mLocationManager.removeUpdates(mNetLocationListener);
                    if(Logger.DEBUG) Logger.debug(TAG, "stopLocationListener:%s", mNetLocationListener.mProvider);
                    mNetLocationListener.mService = null;
                    mNetLocationListener = null;
                }
                mLocationManager = null;
            }
        }
    }

    private void setSyncAlarm() {
        long millis = SyncService.getMillisOfTomorrowTime(mSyncHour, mSyncMinute);
        mAlarm.set(AlarmManager.RTC_WAKEUP, millis, mAlarmSyncCallback);
        if(Logger.DEBUG) Logger.debug(TAG, "Next sync execution: %s", new Date(millis));
    }

    void setLocationAlarm() {
        setLocationAlarm(-1);
    }

    void setLocationAlarm(int interval) {
        if (interval == -1) {
            interval = mPreferences.getInterval(mLastBatteryLevel);
        }

        if(DEBUG) {
            Toast.makeText(this, "Location alarm set to " + interval + "s", Toast.LENGTH_LONG).show();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mAlarm.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() +
                    interval * 1000, mAlarmLocationCallback);
        } else {
            mAlarm.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() +
                    interval * 1000, mAlarmLocationCallback);
        }
        if(Logger.DEBUG) Logger.debug(TAG, "Set alarm to %d seconds", interval);
    }

    private void processIntent(Intent intent) {
        if (intent == null) return;

        if(Logger.DEBUG) Logger.debug(TAG, "processIntent");

        boolean alarmCallBack = intent.hasExtra(Constants.EXTRA_ALARM_CALLBACK);
        boolean startAlarm = intent.hasExtra(Constants.EXTRA_START_ALARM);

        if (startAlarm) {
            mNeedsToUpdateUI = true;
            updateNotification();
            setSyncAlarm();
        }

        if (intent.hasExtra(Constants.EXTRA_STOP_ALARM)) {
            mAlarm.cancel(mAlarmLocationCallback);
            stopLocationListener();
            stopPassiveLocationListener();
            releaseWakeLock();
        }

        if(alarmCallBack || (startAlarm)) {
            acquireWakeLock();
            startLocationListener();
        }

        if (alarmCallBack || startAlarm) {
            setLocationAlarm();
        }

        if (intent.hasExtra(Constants.EXTRA_UPDATE_LOCATION)) {
            acquireWakeLock();
            startLocationListener();
        }

        if (intent.hasExtra(Constants.EXTRA_CONFIGURE)) {
            mPreferences = PreferenceProfile.get(getApplicationContext());
            configure(true);
        }

        if (intent.hasExtra(Constants.EXTRA_CONFIGURE_STORER)) {
            mLocationStorer.configure();
        }

        if(intent.hasExtra(Constants.EXTRA_SYNC)) {
            if(mLastBatteryLevel > 55) {
                SyncService.setAutoSync(getApplicationContext(), true);
            }
            setSyncAlarm();
        }
    }

    private void configure(boolean setup) {
        if(Logger.DEBUG) Logger.debug(TAG, "configure(setup:%s)", setup);


        if (!mPreferences.getBoolean(R.string.pref_service_enabled_key, true)) {
            disable(getApplicationContext());
            return;
        }

        int oldSyncHour = mSyncHour;
        int oldSynMinute = mSyncMinute;
        //boolean oldUseGmsIgAvailable = mUseGmsIgAvailable;
        mMinimumDistance = mPreferences.getInt(R.string.pref_minimun_distance_key, String.valueOf(mMinimumDistance)); //meters
        mGpsTimeout = mPreferences.getInt(R.string.pref_gps_timeout_key, String.valueOf(mGpsTimeout)); //seconds
        mRequestPassiveLocationUpdates = mPreferences.getBoolean(R.string.pref_passive_enabled_key, Boolean.parseBoolean(getString(R.string.pref_passive_enabled_default)));
        mMaxReasonableSpeed = mPreferences.getFloat(R.string.pref_max_speed_key, String.valueOf(mMaxReasonableSpeed)); // meters/seconds
        mMinimumAccuracy = mPreferences.getInt(R.string.pref_minimun_accuracy_key, String.valueOf(mMinimumAccuracy)); // meters
        mBestAccuracy = mPreferences.getInt(R.string.pref_best_accuracy_key, String.valueOf(mBestAccuracy)); // meters
        mNotificationEnabled = mPreferences.getBoolean(R.string.pref_notificationicon_enabled_key, Boolean.parseBoolean(getString(R.string.pref_passive_enabled_default)));
        mWakeLockEnabled = mPreferences.getBoolean(R.string.pref_wackelock_enabled_key, Boolean.parseBoolean(getString(R.string.pref_wackelock_enabled_default)));
        mLocationLogEnabled = mPreferences.getBoolean(R.string.pref_loglocations_key, Boolean.parseBoolean(getString(R.string.pref_loglocations_default)));
        String[] syncTime = mPreferences.getString(R.string.pref_synctime_key, mSyncHour + ":" + mSyncMinute).split(":");
        mSyncHour = Integer.parseInt(syncTime[0]);
        mSyncMinute = Integer.parseInt(syncTime[1]);
        //mUseGmsIgAvailable = preferences.getBoolean(getString(R.string.pref_use_gms_if_available_key), true);
        mInstantUploadEnabled = mPreferences.getBoolean(R.string.pref_instant_upload_enabled_key, true);
        mSetAirplaneMode =  mPreferences.getBoolean(R.string.pref_set_airplanemode_key, mSetAirplaneMode);
        mNotifyEvents =  mPreferences.getBoolean(R.string.profile_notify_events_key, false);
        mRestrictedSettings =  mPreferences.getBoolean(R.string.profile_settings_restricted_key, false);

        mVehicleMode = mPreferences.activeProfile == PreferenceProfile.PROFILE_BICYCLE ||
                mPreferences.activeProfile == PreferenceProfile.PROFILE_CAR;

        if (setup) {
            if (mRequestPassiveLocationUpdates) {
                startLocationListener();
            } else {
                stopPassiveLocationListener();
                /*if(mLocationRequest == null) {
                    stopLocationListener();
                }*/
            }

            if(oldSyncHour != mSyncHour || oldSynMinute != mSyncMinute) {
                setSyncAlarm();
            }

            if (mNotificationEnabled) {
                updateNotification();
            } else {
                stopForeground(true);
            }

            /*
            if(oldUseGmsIgAvailable != mUseGmsIgAvailable) {
                stopPassiveLocationListener();
                stopLocationListener();
                mGooglePlayServiceAvailable = mUseGmsIgAvailable &&
                        GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS;

            }
            */
        }
    }

    protected LocationStorer createStorer() {
        LocatrackDb storer = new LocatrackDb(getApplicationContext());
        storer.prepareDmlStatements();
        return storer;
    }

    //endregion Core functions

    //region Method overrides

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if(Logger.DEBUG) Logger.debug(TAG, "onCreate");

        mLocationStorer = createStorer();
        mLocationStorer.configure();
        mPreferences = PreferenceProfile.get(getApplicationContext());
        configure(false);
        
        /*mGooglePlayServiceAvailable = mUseGmsIgAvailable &&
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS;*/

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mAlarm = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent i = new Intent(getApplicationContext(), LocationService.class);
        i.setAction(Constants.ACTION_ALARM);
        i.putExtra(Constants.EXTRA_ALARM_CALLBACK, 1);
        mAlarmLocationCallback = PendingIntent.getService(getApplicationContext(), 0, i, 0);

        i = new Intent(getApplicationContext(), LocationService.class);
        i.setAction(Constants.ACTION_SYNC);
        i.putExtra(Constants.EXTRA_SYNC, 1);
        mAlarmSyncCallback = PendingIntent.getService(getApplicationContext(), 0, i, 0);

        ActionReceiver.register(this);
    }

    @Override
    public void onDestroy() {
        if(Logger.DEBUG) Logger.debug(TAG, "onDestroy");

        releaseWakeLock();
        ActionReceiver.unregister(this);
        mAlarm.cancel(mAlarmLocationCallback);
        mAlarm.cancel(mAlarmSyncCallback);

        stopPassiveLocationListener();
        stopLocationListener();
        stopForeground(true);

        destroyExecutorThread();
        PreferenceProfile.reset();

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(Logger.DEBUG) Logger.debug(TAG, "onStartCommand");
        processIntent(intent);
        return START_STICKY;
    }

    //endregion Method overrides

    //region Google Play Service callbacks

    /*
    @Override
    public void onConnected(Bundle bundle) {
        if(Logger.DEBUG) Logger.debug(TAG, "onConnected");
        requestGooglePlayLocationUpdates();
    }

    @Override
    public void onDisconnected() {
        if(Logger.DEBUG) Logger.debug(TAG, "onDisconnected");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if(Logger.DEBUG) Logger.debug(TAG, "onConnectionFailed:%s", connectionResult);
    }*/

    //endregionregion Google Play Service callbacks

    //region Helper functions

    public static void start(Context context, Intent intent) {
        context = context.getApplicationContext();
        intent.setClass(context, LocationService.class);
        context.startService(intent);
    }

    public static void start(Context context, String option) {
        Intent intent = new Intent();
        intent.putExtra(option, 1);
        start(context, intent);
    }

    public static void start(Context context) {
        Intent i = new Intent();
        i.putExtra(Constants.EXTRA_START_ALARM, 1);
        start(context, i);
    }

    public static void configure(Context context) {
        start(context, Constants.EXTRA_CONFIGURE);
    }

    public static void updateLocation(Context context) {
        start(context, Constants.EXTRA_UPDATE_LOCATION);
    }

    public static void enable(Context context) {
        PackageManager pm = context.getPackageManager();

        // Service
        ComponentName cn = new ComponentName(context, LocationService.class);
        pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        // Boot receiver
        cn = new ComponentName(context, LocationService.StartServiceReceiver.class);
        pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        start(context);
    }

    public static void disable(Context context) {
        stop(context);

        PackageManager pm = context.getPackageManager();

        // Service
        ComponentName cn = new ComponentName(context, LocationService.class);
        pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        // Boot receiver
        cn = new ComponentName(context, LocationService.StartServiceReceiver.class);
        pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, LocationService.class));
    }

    public static boolean isRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        String className = LocationService.class.getName();
        List<RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);

        for (RunningServiceInfo service : runningServices) {
            if (className.equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static LocatrackLocation getBestLastLocation(Context context)
    {
        LocationManager locationManager =
                (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);

        Location bestResult = null;
        List<String> matchingProviders = locationManager.getAllProviders();
        for (String provider: matchingProviders)
        {
            Location location = locationManager.getLastKnownLocation(provider);
            if(isBetterLocation(location, bestResult, 2000, 5500, 100)) {
                bestResult = location;
            }
        }
        if(bestResult != null) {
            return new LocatrackLocation(bestResult);
        }
        return null;
    }

    static void setAirplaneMode(Context context, boolean  isEnabled) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN) return;
        try {
            boolean enabled = Settings.System.getInt(context.getContentResolver(),
                    Settings.System.AIRPLANE_MODE_ON, 0) == 1;

            if(enabled == isEnabled) return;

            // Toggle airplane mode.
            Settings.System.putInt(context.getContentResolver(), Settings.System.AIRPLANE_MODE_ON,
                    isEnabled ? 1 : 0);

            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", isEnabled);
            context.sendBroadcast(intent);
        } catch (Exception ignored) {
            Logger.error(TAG, ignored.getMessage());
        }
    }


    //endregionregion Helper functions
}