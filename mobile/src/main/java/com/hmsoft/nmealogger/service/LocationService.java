package com.hmsoft.nmealogger.service;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.location.LocationRequest;
import com.hmsoft.nmealogger.BuildConfig;
import com.hmsoft.nmealogger.R;
import com.hmsoft.nmealogger.common.Constants;
import com.hmsoft.nmealogger.common.Logger;
import com.hmsoft.nmealogger.common.PerfWatch;
import com.hmsoft.nmealogger.common.TaskExecutor;
import com.hmsoft.nmealogger.data.ExifGeotager;
import com.hmsoft.nmealogger.data.Geocoder;
import com.hmsoft.nmealogger.data.locatrack.LocatrackDb;
import com.hmsoft.nmealogger.data.nmea.NmeaStorer;
import com.hmsoft.nmealogger.ui.MainActivity;

import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LocationService extends Service implements GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener {

    //region Static fields
    private static final String TAG = "LocationService";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final int HALF_MINUTE = 1000 * 30;
    //endregion Static fields

    //region Settings fields
    private int mAutoLocationInterval = 300; // seconds
    private int mMinimumDistance = 20; //meters
    private int mGpsTimeout = 60; //seconds
    /*private*/ boolean mRequestPassiveLocationUpdates = true;
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
    private LocationListener mGmsLocationListener;
    private Location mCurrentBestLocation;
    private PowerManager mPowerManager;
    private ComponentName mMapIntentComponent = null;
    private boolean mAutoExifGeotagerEnabled;
    private boolean mUseGmsIgAvailable;
    //endregion Settings fields

    //region UI Data fields
    private Location mLastSavedLocation = null;
    private int mLocationCount = 0;
    String mLastSaveAddress = null;
    //endregion UI Data fields

    //region Core fields
    private BroadcastReceiver mUserPresentReceiver = null;
    private AlarmManager mAlarm = null;
    private PendingIntent mAlarmLocationCallback = null;
    private PendingIntent mAlarmSyncCallback = null;
    private PendingIntent mLocationActivityIntent = null;
    private Intent mMapIntent = null;
    private PendingIntent mUpdateLocationIntent = null;
    private PendingIntent mStopTrackingIntent;
    private LocationRequest mLocationRequest = null;
    private LocationClient mGpLocationClient = null;
    private boolean mGooglePlayServiceAvailable;

    private NmeaStorer mNmeaStorer;
    private LocatrackDb mDbStorer;

    boolean mTrackingMode;
    boolean mNeedsToUpdateUI;
    private WakeLock mWakeLock;

    private HandlerThread mExecutorThread = null;
    private Handler mHandler;
    //endregion Core fields

    //region Debug only fields
    int mUploadThreadCount;
    //endregion Debug only fields

    //region Helper Inner Classes

    public static class StartServiceReceiver extends BroadcastReceiver {

        private static final String TAG = "StartServiceReceiver";

        @Override
        public void onReceive(final Context context, Intent intent) {
            if(Logger.DEBUG) Logger.debug(TAG, "onReceive:%s", intent);
            LocationService.start(context);
            /*TaskExecutor.executeOnUIThread(new Runnable() {
                @Override
                public void run() {
                    LocationService.start(context);
                }
            }, 60);*/
        }
    }

    private static class UserPresentReceiver extends BroadcastReceiver {
        private static final String TAG = "UserPresentReceiver";

        public LocationService mService;

        public UserPresentReceiver(LocationService service) {
            mService = service;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if(Logger.DEBUG) Logger.debug(TAG, "onReceive");
            mService.mNeedsToUpdateUI = true;
            mService.updateNotification();
        }
    }

    private static class LocationListener implements android.location.LocationListener,
            com.google.android.gms.location.LocationListener {

        private static final String TAG = "LocationListener";

        private LocationService mService;
        String mProvider;

        public LocationListener(LocationService service, String provider) {
            mService = service;
            mProvider = provider;
        }

        @Override
        public void onLocationChanged(Location location) {
            if(Logger.DEBUG) Logger.debug(TAG, "onLocationChanged:%s", mProvider);
            mService.handleLocation(location, mProvider);
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
        }

        @Override
        protected void onPostExecute(String address) {
            sInstance = null;
            if (!TextUtils.isEmpty(address)) {
                if(Logger.DEBUG) Logger.debug(TAG, "onPostExecute");
                mService.mLastSaveAddress = address;
                mService.updateNotification();
            }
        }

        public static void run(LocationService service, Location location) {
            if (sInstance == null) {
                (new GetAddressTask(service)).run(location);
            }
        }
    }

    private static class PictureContentObserver extends ContentObserver {

        private static final String TAG = "PictureContentObserver";

        private static PictureContentObserver sExternalInstance = null;
        private static PictureContentObserver sInternalInstance = null;

        private Uri mUri;
        private Context mContext;
        private ExifGeotager.GeotagFinishListener mFinishListener = null;

        private PictureContentObserver(Context context, Uri uri) {
            super(null);
            mUri = uri;
            mContext = context;
        }

        public void onChange(boolean selfChange, Uri uri) {
            if(Logger.DEBUG) Logger.debug(TAG, "onChange:%s,%s", selfChange, uri);
            boolean geotag = false;
            if(uri != null) {
                if(uri.equals(mUri)) {
                    geotag = true;
                }
            }  else {
                geotag = true;
            }

            if(geotag) {
                if(mFinishListener == null) {
                    mFinishListener = new ExifGeotager.GeotagFinishListener() {
                        @Override
                        protected void onGeotagTaskFinished(int totalCount, int geotagedCount) {
                            if(Logger.DEBUG) Logger.debug(TAG, "onGeotagTaskFinished:%d/%d", totalCount, geotagedCount);
                            ExifGeotager.notify(mContext, totalCount, geotagedCount);
                        }
                    };
                }
                ExifGeotager.geoTagContent(mContext, mUri, false, false, mFinishListener);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        public static void register(Context context) {
            if(sExternalInstance == null) {
                sExternalInstance = new PictureContentObserver(context,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            }
            if(sInternalInstance == null) {
                sInternalInstance = new PictureContentObserver(context,
                        MediaStore.Images.Media.INTERNAL_CONTENT_URI);
            }

            ContentResolver resolver = context.getContentResolver();
            resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    false, sExternalInstance);
            resolver.registerContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                    false, sInternalInstance);

            if(Logger.DEBUG) Logger.debug(TAG, "ContentObserver registered");
        }

        public static void unregister(Context context) {
            ContentResolver resolver = context.getContentResolver();
            if(sExternalInstance != null) {
                resolver.unregisterContentObserver(sExternalInstance);
                sExternalInstance = null;
                if(Logger.DEBUG) Logger.debug(TAG, "ContentObserver UNregistered (external)");
            }
            if(sInternalInstance != null) {
                resolver.unregisterContentObserver(sInternalInstance);
                sInternalInstance = null;
                if(Logger.DEBUG) Logger.debug(TAG, "ContentObserver UNregistered (internal)");
            }
        }
    }

    //endregion Helper Inner Classes

    //region Google Play location service helper functions

    private void requestGooglePlayLocationUpdates() {
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
    }

    //endregion Google Play location service helper functions

    //region Core functions

    void handleLocation(Location location, String provider) {
        if (mCurrentBestLocation != null &&
                (mCurrentBestLocation.getTime() == location.getTime())) {
            if(Logger.DEBUG) Logger.debug(TAG, "Location is the same location that currentBestLocation");
            return;
        }

        long timeDelta = HALF_MINUTE;
        if (mTrackingMode) {
            timeDelta = 2500;
        }

        if(Logger.DEBUG) Logger.debug(TAG, "handleLocation %s", location);
        logLocation(location);

        if (isBetterLocation(location, mCurrentBestLocation, timeDelta, mMinimumAccuracy,
                mMaxReasonableSpeed)) {

            if(LocationManager.PASSIVE_PROVIDER.equals(provider)) {
                if(mLocationRequest == null || mLocationManager == null) {
                    mCurrentBestLocation = location;
                    saveLocation(mCurrentBestLocation);
                } else {
                    if(Logger.DEBUG) Logger.debug(TAG, "Ignored passive location while in location request.");
                }
            } else {
                mCurrentBestLocation = location;
                if ((!mGpsProviderEnabled && !mGooglePlayServiceAvailable) ||
                        (isFromGps(mCurrentBestLocation) && location.getAccuracy() <= mBestAccuracy)) {
                    saveLocation(mCurrentBestLocation);
                    if (!mTrackingMode) {
                        stopLocationListener();
                    }
                } else {
                    if(Logger.DEBUG) Logger.debug(TAG, "No good GPS location.");
                }
            }
        } else {
            if(Logger.DEBUG) Logger.debug(TAG, "Location is not better than last location.");
        }
    }

    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        return isBetterLocation(location, currentBestLocation, HALF_MINUTE, mMinimumAccuracy,
                mMaxReasonableSpeed);
    }

    private boolean isFromGps(Location location) {
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

        if (isFromSameProvider) {
            float meters = location.distanceTo(currentBestLocation);
            long seconds = timeDelta / 1000L;
            float speed = meters / seconds;
            if ( speed > maxReasonableSpeed) {
                if(Logger.DEBUG) Logger.debug(TAG, "Super speed detected. %f meters from last location", meters);
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

        if ((mCurrentBestLocation  != null && mLastSavedLocation != null) &&
                (mCurrentBestLocation == mLastSavedLocation ||
                mCurrentBestLocation.getTime() == mLastSavedLocation.getTime())) {
            if(Logger.DEBUG) Logger.debug(TAG, "currentBestLocation is the same lastSavedLocation. Saving nothing...");
            return;
        }

        if (mCurrentBestLocation != null && isFromGps(mCurrentBestLocation)) {
            if(Logger.DEBUG) Logger.debug(TAG, "currentBestLocation is from GPS");
            saveLocation(mCurrentBestLocation);
            return;
        }

        Location bestLastLocation = mCurrentBestLocation;

        if (mGooglePlayServiceAvailable) {
            Location lastKnownGmsLocation  = mGpLocationClient.getLastLocation();
            if (isBetterLocation(lastKnownGmsLocation, bestLastLocation)) {
                bestLastLocation = lastKnownGmsLocation;
            }
        } else {
            if (mGpsProviderEnabled) {
                if(Logger.DEBUG) Logger.debug(TAG, "currentBestLocation is not from GPS, but GPS is enabled");
                Location lastKnownGpsLocation = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (isBetterLocation(lastKnownGpsLocation, bestLastLocation)) {
                    if(Logger.DEBUG) Logger.debug(TAG, "Got good LastKnownLocation from GPS provider.");
                    bestLastLocation = lastKnownGpsLocation;
                } else {
                    if(Logger.DEBUG) Logger.debug(TAG, "LastKnownLocation from GPS provider is not better than currentBestLocation.");
                }
            }

            if (mNetProviderEnabled) {
                Location lastKnownNetLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (isBetterLocation(lastKnownNetLocation, bestLastLocation)) {
                    bestLastLocation = lastKnownNetLocation;
                }
            }
        }

        if (bestLastLocation != null) {
            saveLocation(bestLastLocation);
            if(mCurrentBestLocation == null) {
                mCurrentBestLocation = bestLastLocation;
            }
        } else if (DEBUG) {
            if(Logger.DEBUG) Logger.debug(TAG, "No last location. Turn on GPS!");
        }
    }

    private void saveLocation(final Location location) {
        if (location == null)
            return;

        if(Logger.DEBUG) Logger.debug(TAG, "saveLocation: %s", location);

        if(mExecutorThread == null) {
            mExecutorThread = new HandlerThread(BuildConfig.APPLICATION_ID + "." + TAG);
            mExecutorThread.start();
            Looper looper = mExecutorThread.getLooper();
            mHandler = new Handler(looper);
        }

        if (DEBUG) {
            int threadCount;
            synchronized (this) {
                threadCount = ++mUploadThreadCount;
            }
            if(Logger.DEBUG) Logger.debug(TAG, "START saveLocation: Thread count=%d", threadCount);
        }

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    PerfWatch pw = null;
                    if (Logger.DEBUG) {
                        pw = PerfWatch.start(TAG, "Start: Save location to NMEA log");
                    }
                    mNmeaStorer.storeLocation(location);
                    if (Logger.DEBUG) {
                        if (pw != null) {
                            pw.stop(TAG, "End: Save location to NMEA log");
                        }
                    }

                    if (mAutoExifGeotagerEnabled) {
                        if (Logger.DEBUG) {
                            pw = PerfWatch.start(TAG, "Start: Save location to db");
                        }
                        mDbStorer.prepareDmlStatements();
                        mDbStorer.storeLocation(location);

                        if (Logger.DEBUG) {
                            if (pw != null) {
                                pw.stop(TAG, "End: Save location to db");
                            }
                        }
                    }

                    if (DEBUG) {
                        int threadCount;
                        synchronized (this) {
                            threadCount = --mUploadThreadCount;
                        }
                        if(Logger.DEBUG) Logger.debug(TAG, "END saveLocation: Thread count=%d", threadCount);
                    }
                } finally {
                    TaskExecutor.executeOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            mLastSaveAddress = null;
                            mLastSavedLocation = location;
                            mLocationCount++;

                            updateUIIfNeeded();
                            releaseWackeLock();
                        }
                    });
                }
            }
        });
    }

    private void logLocation(Location location) {
        if (mLocationLogEnabled) {
            Logger.log2file("LOCATION", location.toString(), "locations-%s.log", null);
        }
    }

    public void acquireWackeLock() {
        if (mWakeLockEnabled) {
            if (mWakeLock == null) {
                mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Utils");
                mWakeLock.setReferenceCounted(false);
            }
            if(Logger.DEBUG) Logger.debug(TAG, "acquireLocationLock");
            mWakeLock.acquire();
        }
    }

    public void releaseWackeLock() {
        if (mWakeLock != null) {
            if(Logger.DEBUG) Logger.debug(TAG, "releaseLocationLock");
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
                        getString(R.string.service_title));
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
                notificationBuilder.setContentText(getString(R.string.service_content, mLocationCount, accuracy));
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                if (mTrackingMode) {
                    if (mStopTrackingIntent == null) {
                        Intent updateIntent = new Intent(context, LocationService.class);
                        updateIntent.putExtra(Constants.EXTRA_STOP_TRACKING_MODE, 1);
                        updateIntent.addCategory(Constants.CATEGORY_TRACKING);
                        mStopTrackingIntent = PendingIntent.getService(context, 0, updateIntent, 0);
                    }
                    notificationBuilder.addAction(R.drawable.ic_action_not_traking,
                            getString(R.string.action_stop_tracking), mStopTrackingIntent);
                } else {
                    if (mUpdateLocationIntent == null) {
                        Intent updateIntent = new Intent(context, LocationService.class);
                        updateIntent.putExtra(Constants.EXTRA_UPDATE_LOCATION, 1);
                        updateIntent.setAction(Constants.ACTION_NOTIFICATION_UPDATE_LOCATION);
                        mUpdateLocationIntent = PendingIntent.getService(context, 0, updateIntent, 0);
                    }
                    notificationBuilder.addAction(R.drawable.ic_action_place,
                            getString(R.string.action_update_location), mUpdateLocationIntent);
                }
            }

            Notification notif = notificationBuilder.build();
            startForeground(1, notif);
        }
    }

    private void startLocationListener() {
        if(mGooglePlayServiceAvailable) {
            if (mLocationRequest == null) {
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
            }
        } else if (mLocationManager == null) {

            if(Logger.DEBUG) Logger.debug(TAG, "startLocationListener: No Google Play Services available. Fallback to old location listeners.");

            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            mNetProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            mGpsProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

            long time = mGpsTimeout;
            if (!mTrackingMode) {
                time = mGpsTimeout / 4;
            }

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
            if(Logger.DEBUG) Logger.debug(TAG, "Executing gps timeout in %d seconds, TM:%s",
                    mGpsTimeout, mTrackingMode);
            mTimeoutRoutinePending = true;
            TaskExecutor.executeOnUIThread(new Runnable() {
                @Override
                public void run() {
                    mTimeoutRoutinePending = false;
                    if (mLocationManager != null || mLocationRequest != null) {
                        if(Logger.DEBUG) Logger.debug(TAG, "GPS Timeout");
                        saveLastLocation();
                        if (!LocationService.this.mTrackingMode) {
                            stopLocationListener();
                        }
                    }
                }
            }, mGpsTimeout);
        }
    }

    private void startPassiveLocationListener() {
        if (!mTrackingMode && mRequestPassiveLocationUpdates && mPassiveLocationListener == null) {
            if(Logger.DEBUG) Logger.debug(TAG, "startPassiveLocationListener");
            mPassiveLocationListener = new LocationListener(this, LocationManager.PASSIVE_PROVIDER);

            if(mGooglePlayServiceAvailable) {
                LocationRequest passiveRequest = new LocationRequest();
                passiveRequest.setInterval(2000);
                passiveRequest.setFastestInterval(1750);
                passiveRequest.setSmallestDisplacement(mMinimumDistance / 2);
                passiveRequest.setPriority(LocationRequest.PRIORITY_NO_POWER);
                mGpLocationClient.requestLocationUpdates(passiveRequest, mPassiveLocationListener);
            } else {
                LocationManager locationManager = this.mLocationManager;
                if (locationManager == null) {
                    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                }
                locationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 2000, mMinimumDistance / 2,
                        mPassiveLocationListener);

            }
        }
    }

    private void stopPassiveLocationListener() {
        if (mPassiveLocationListener != null) {
            if(mGooglePlayServiceAvailable) {
                mGpLocationClient.removeLocationUpdates(mPassiveLocationListener);
                if(Logger.DEBUG) Logger.debug(TAG, "stopPassiveLocationListener: Google Play Location");

            } else {
                LocationManager locationManager = this.mLocationManager;
                if (locationManager == null) {
                    locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                }

                locationManager.removeUpdates(mPassiveLocationListener);
                if(Logger.DEBUG) Logger.debug(TAG, "stopPassiveLocationListener:Android Location");

            }
            mPassiveLocationListener = null;
        }
    }

    private void stopLocationListener() {
        if(mGooglePlayServiceAvailable) {
            if(mLocationRequest != null) {
                if(Logger.DEBUG) Logger.debug(TAG, "stopLocationListener:%s", mGmsLocationListener.mProvider);
                mLocationRequest = null;
                if(mGpLocationClient != null) {
                    mGpLocationClient.removeLocationUpdates(mGmsLocationListener);
                    mGmsLocationListener = null;
                }
            }
            if (mPassiveLocationListener == null && mGpLocationClient != null) {
                if(Logger.DEBUG) Logger.debug(TAG, "Disconnecting Play Services...");
                if(mGpLocationClient.isConnected()) mGpLocationClient.disconnect();
                mGpLocationClient = null;
            }
        } else {
            if (mLocationManager != null) {
                if (mGpsLocationListener != null) {
                    mLocationManager.removeUpdates(mGpsLocationListener);
                    if(Logger.DEBUG) Logger.debug(TAG, "stopLocationListener:%s", mGpsLocationListener.mProvider);
                    mGpsLocationListener = null;
                }
                if (mNetLocationListener != null) {
                    mLocationManager.removeUpdates(mNetLocationListener);
                    if(Logger.DEBUG) Logger.debug(TAG, "stopLocationListener:%s", mNetLocationListener.mProvider);
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

    private void setLocationAlarm() {
        int interval = mAutoLocationInterval;
        if(mTrackingMode) {
            interval = 600;
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
        boolean tracking = intent.hasExtra(Constants.EXTRA_START_TRACKING_MODE);
        if(tracking) mTrackingMode = true;
        if(Logger.DEBUG && tracking) Logger.debug(TAG, "Starting tracking mode");

        if (startAlarm) {
            mNeedsToUpdateUI = true;
            updateNotification();
            setSyncAlarm();

            if(mAutoExifGeotagerEnabled) {
                PictureContentObserver.register(getApplicationContext());
            }
        }

        if (intent.hasExtra(Constants.EXTRA_STOP_ALARM)) {
            mAlarm.cancel(mAlarmLocationCallback);
            stopLocationListener();
            stopPassiveLocationListener();
            releaseWackeLock();
        }

        if(alarmCallBack || (startAlarm && !tracking)) {
            acquireWackeLock();
            startLocationListener();
        }

        if (alarmCallBack || startAlarm) {
            setLocationAlarm();
        }

        if (intent.hasExtra(Constants.EXTRA_UPDATE_LOCATION)) {
            acquireWackeLock();
            startLocationListener();
        }

        if (intent.hasExtra(Constants.EXTRA_CONFIGURE)) {
            configure(true);
        }

        if (intent.hasExtra(Constants.EXTRA_CONFIGURE_STORER)) {
            mNmeaStorer.configure();
            mDbStorer.configure();
        }

        if(intent.hasExtra(Constants.EXTRA_SYNC)) {
            SyncService.importNmeaToLocalDb(this);
            setSyncAlarm();
        }

        if(intent.hasExtra(Constants.EXTRA_STOP_TRACKING_MODE)) {
            stopLocationListener();
            mTrackingMode = false;
            setLocationAlarm();
            PreferenceManager.
                    getDefaultSharedPreferences(this).
                    edit().
                    putBoolean(Constants.PREF_TRAKING_MODE_KEY, false).
                    apply();

            mNeedsToUpdateUI = true;
            updateUIIfNeeded();

            Toast.makeText(this, R.string.toast_tracking_stop, Toast.LENGTH_LONG).show();
        }

        if(tracking) {
            stopLocationListener();
            mTrackingMode = true;
            startLocationListener();
            stopPassiveLocationListener();
            PreferenceManager.
                    getDefaultSharedPreferences(this).
                    edit().
                    putBoolean(Constants.PREF_TRAKING_MODE_KEY, true).
                    apply();

            mNeedsToUpdateUI = true;
            updateUIIfNeeded();

            Toast.makeText(this, R.string.toast_tracking_start, Toast.LENGTH_LONG).show();
        }

        if(intent.hasExtra(Constants.EXTRA_SET_AUTO_GEOTAG)) {
            boolean setAutoGeotag = intent.getBooleanExtra(Constants.EXTRA_SET_AUTO_GEOTAG, true);
            if(setAutoGeotag != mAutoExifGeotagerEnabled) {
                mAutoExifGeotagerEnabled = setAutoGeotag;

               PreferenceManager.getDefaultSharedPreferences(this)
                       .edit()
                       .putBoolean(getString(R.string.pref_auto_exif_geotager_enabled_key), setAutoGeotag)
                       .apply();

                PictureContentObserver.unregister(this);
                if(mAutoExifGeotagerEnabled) {
                    PictureContentObserver.register(this);
                }
            }
        }

        if(intent.hasExtra(Constants.EXTRA_NOTIFICATION_DELETED)) {
            int notificationId = intent.getIntExtra(Constants.EXTRA_NOTIFICATION_DELETED, 0);
            switch (notificationId) {
                case ExifGeotager.NOTIFICATION_ID:
                    ExifGeotager.clearNotifyCounts();
                    break;
            }
        }
    }

    private void configure(boolean setup) {
        if(Logger.DEBUG) Logger.debug(TAG, "configure(setup:%s)", setup);

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        if (!preferences.getBoolean(getString(R.string.pref_service_enabled_key), true)) {
            disable(getApplicationContext());
            return;
        }

        int oldAutoLocationInterval = mAutoLocationInterval;
        int oldSyncHour = mSyncHour;
        int oldSynMinute = mSyncMinute;
        boolean oldUseGmsIgAvailable = mUseGmsIgAvailable;
        mAutoLocationInterval = Integer.parseInt(preferences.getString(getString(R.string.pref_update_interval_key), String.valueOf(mAutoLocationInterval))); // seconds
        mTrackingMode = mAutoLocationInterval <= 20;
        mMinimumDistance = Integer.parseInt(preferences.getString(getString(R.string.pref_minimun_distance_key), String.valueOf(mMinimumDistance))); //meters
        mGpsTimeout = Integer.parseInt(preferences.getString(getString(R.string.pref_gps_timeout_key), String.valueOf(mGpsTimeout))); //seconds
        mRequestPassiveLocationUpdates = preferences.getBoolean(getString(R.string.pref_passive_enabled_key), Boolean.parseBoolean(getString(R.string.pref_passive_enabled_default)));
        mMaxReasonableSpeed = Float.parseFloat(preferences.getString(getString(R.string.pref_max_speed_key), String.valueOf(mMaxReasonableSpeed))); // meters/seconds
        mMinimumAccuracy = Integer.parseInt(preferences.getString(getString(R.string.pref_minimun_accuracy_key), String.valueOf(mMinimumAccuracy))); // meters
        mBestAccuracy = Integer.parseInt(preferences.getString(getString(R.string.pref_best_accuracy_key), String.valueOf(mBestAccuracy))); // meters
        mNotificationEnabled = preferences.getBoolean(getString(R.string.pref_notificationicon_enabled_key), Boolean.parseBoolean(getString(R.string.pref_passive_enabled_default)));
        mWakeLockEnabled = preferences.getBoolean(getString(R.string.pref_wackelock_enabled_key), Boolean.parseBoolean(getString(R.string.pref_wackelock_enabled_default)));
        mLocationLogEnabled = preferences.getBoolean(getString(R.string.pref_loglocations_key), Boolean.parseBoolean(getString(R.string.pref_loglocations_default)));
        String[] syncTime = preferences.getString(getString(R.string.pref_synctime_key), mSyncHour + ":" + mSyncMinute).split(":");
        mSyncHour = Integer.parseInt(syncTime[0]);
        mSyncMinute = Integer.parseInt(syncTime[1]);
        mAutoExifGeotagerEnabled = preferences.getBoolean(getString(R.string.pref_auto_exif_geotager_enabled_key), true);
        mUseGmsIgAvailable = preferences.getBoolean(getString(R.string.pref_use_gms_if_available_key), true);

        if (setup) {
            if (mRequestPassiveLocationUpdates) {
                startLocationListener();
            } else {
                stopPassiveLocationListener();
                if(mLocationRequest == null) {
                    stopLocationListener();
                }
            }

            if (mAutoLocationInterval != oldAutoLocationInterval) {
                setLocationAlarm();
                if(mTrackingMode) {
                    startLocationListener();
					stopPassiveLocationListener();
                } else {
                    stopLocationListener();
                }
            }

            if(oldSyncHour != mSyncHour || oldSynMinute != mSyncMinute) {
                setSyncAlarm();
            }

            if (mNotificationEnabled) {
                updateNotification();
            } else {
                stopForeground(true);
            }

            if(oldUseGmsIgAvailable != mUseGmsIgAvailable) {
                stopPassiveLocationListener();
                stopLocationListener();
                mGooglePlayServiceAvailable = mUseGmsIgAvailable &&
                        GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS;

            }

            PictureContentObserver.unregister(getApplicationContext());
            if(mAutoExifGeotagerEnabled) {
                PictureContentObserver.register(getApplicationContext());
            }
        }
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

        
        mNmeaStorer = NmeaStorer.instance;
        mNmeaStorer.configure();
        mDbStorer = new LocatrackDb(getApplicationContext());
        mDbStorer.configure();
        configure(false);

        
        mGooglePlayServiceAvailable = mUseGmsIgAvailable &&
                GooglePlayServicesUtil.isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS;

        
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

        mUserPresentReceiver = new UserPresentReceiver(this);
        registerReceiver(mUserPresentReceiver, new IntentFilter(Intent.ACTION_USER_PRESENT));
    }

    @Override
    public void onDestroy() {
        if(Logger.DEBUG) Logger.debug(TAG, "onDestroy");

        releaseWackeLock();
        PictureContentObserver.unregister(getApplicationContext());
        unregisterReceiver(mUserPresentReceiver);
        mAlarm.cancel(mAlarmLocationCallback);
        mAlarm.cancel(mAlarmSyncCallback);

        stopPassiveLocationListener();
        stopLocationListener();
        stopForeground(true);

        if(mExecutorThread != null) {
            mExecutorThread.quit();
        }

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
    }

    //endregionregion Google Play Service callbacks

    //region Helper functions

    public static void start(Context context, Intent intent) {
        intent.setClass(context, LocationService.class);
        context.startService(intent);
    }

    public static void start(Context context, String option) {
        Intent intent = new Intent();
        intent.putExtra(option, 1);
        start(context, intent);
    }

    public static void start(Context context) {
        boolean isTracking = PreferenceManager.
                getDefaultSharedPreferences(context).
                getBoolean(Constants.PREF_TRAKING_MODE_KEY, false);

        Intent i = new Intent();
        i.putExtra(Constants.EXTRA_START_ALARM, 1);
        if(isTracking) {
           i.putExtra(Constants.EXTRA_START_TRACKING_MODE, 1);
        }

        start(context, i);
    }

    public static void configure(Context context) {
        start(context, Constants.EXTRA_CONFIGURE);
    }

    public static void configureStorer(Context context) {
        start(context, Constants.EXTRA_CONFIGURE_STORER);
    }

    public static void startTrackingMode(Context context) {
        start(context, Constants.EXTRA_START_TRACKING_MODE);
    }

    public static void stopTrackingMode(Context context) {
        start(context, Constants.EXTRA_STOP_TRACKING_MODE);
    }

    public static void updateLocation(Context context) {
        start(context, Constants.EXTRA_UPDATE_LOCATION);
    }

    public static void setAutoExifGeotag(Context context, boolean enabled) {
        Intent intent = new Intent();
        intent.putExtra(Constants.EXTRA_SET_AUTO_GEOTAG, enabled);
        start(context, intent);
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

    public static Location getBestLastLocation(Context context)
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
        return bestResult;
    }

    //endregionregion Helper functions
}