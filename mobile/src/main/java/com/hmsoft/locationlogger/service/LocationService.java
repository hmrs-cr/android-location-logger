package com.hmsoft.locationlogger.service;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlarmManager;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.widget.Toast;

import com.hmsoft.locationlogger.BuildConfig;
import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Constants;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.PerfWatch;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.TelegramHelper;
import com.hmsoft.locationlogger.data.ExifGeotager;
import com.hmsoft.locationlogger.data.Geocoder;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;
import com.hmsoft.locationlogger.data.locatrack.LocatrackDb;
import com.hmsoft.locationlogger.data.locatrack.LocatrackOnlineStorer;
import com.hmsoft.locationlogger.data.locatrack.LocatrackSimNotifierStorer;
import com.hmsoft.locationlogger.data.locatrack.LocatrackTelegramStorer;
import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;
import com.hmsoft.locationlogger.ui.MainActivity;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LocationService extends Service /*implements GooglePlayServicesClient.ConnectionCallbacks,
        GooglePlayServicesClient.OnConnectionFailedListener*/
    implements TelegramHelper.UpdateCallback {

    //region Static fields
    private static final String TAG = "LocationService";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final boolean DIAGNOSTICS = DEBUG;
    private static final int HALF_MINUTE = 1000 * 30;
    private static final int CRITICAL_BATTERY_LEV = 50;

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
    private LocatrackLocation mCurrentBestLocation;
    private PowerManager mPowerManager;
    private ComponentName mMapIntentComponent = null;
    private ConnectivityManager mConnectivityManager;
    private boolean mInstantUploadEnabled = false;
    private boolean mTelegramNotifyEnabled = false;
    private PreferenceProfile mPreferences;
    private boolean mAutoExifGeotagerEnabled;

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
    private PendingIntent mAvailBalanceSmsCallback = null;
    private PendingIntent mLocationActivityIntent = null;
    private Intent mMapIntent = null;
    private PendingIntent mUpdateLocationIntent = null;
    private boolean mUploadHandlerRunning;
    private int mRetrySmsCount;

    LocatrackOnlineStorer mOnlineStorer = null;
    LocatrackTelegramStorer mTelegramStorer = null;
    LocationStorer mLocationStorer;

    boolean mNeedsToUpdateUI;
    private WakeLock mWakeLock;

    private HandlerThread mUploadThread = null;
    private Handler mUploadHandler;

    static int sLastBatteryLevel = 99;
    boolean mChargingStart;
    boolean mChargingStop;
    boolean mAirplaneModeOn;


    StringBuilder mPendingNotifyInfo;

    private String[] mTelegramAllowedFrom = null;
    private long mLastTelegamUpdate;

    @Override
    public void onTelegramUpdateReceived(String chatId, final String messageId, final String text) {
        if (DEBUG)
            Logger.debug(TAG, "Telegram:onTelegramUpdateReceived: ChatId: %s, Message: %s", chatId, text);

        final String channelId = getString(R.string.pref_telegram_chatid);
        boolean allowed;
        if (!(allowed = channelId.equals(chatId))) {
            if (mTelegramAllowedFrom == null) {
                mTelegramAllowedFrom = getString(R.string.pref_telegram_masterid).split("\\|");
            }
            for (int i = 0; i < mTelegramAllowedFrom.length; i++) {
                if (mTelegramAllowedFrom[i].equals(chatId)) {
                    allowed = true;
                    break;
                }
            }
        }

        if (!allowed) {
            Logger.warning(TAG, "You are not my master! %s", chatId);
            return;
        }

        TaskExecutor.executeOnUIThread(new Runnable() {
            @Override
            public void run() {
                requestTelegramUpdates(2);
            }
        }, 1);

        final String botKey = getString(R.string.pref_telegram_botkey);
        if (text.startsWith("document|")) {
            String[] values = text.split("\\|");

            String fileName = values[1];
            String fileId = values[2];

            String downloadUrl = TelegramHelper.getFileDownloadUrl(botKey, fileId);
            if (DEBUG) Logger.debug(TAG, "DownloadUrl: %s", downloadUrl);

            if (!TextUtils.isEmpty(downloadUrl)) {
                long id = downloadFile(fileName, downloadUrl);
                DownloadFinishedReceiver.addDownload(id, messageId);
            }

        } else {
            final String textl = text.toLowerCase().trim();
            TaskExecutor.executeOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (textl.contains("location")) {
                        if (mPendingNotifyInfo == null) {
                            mPendingNotifyInfo = new StringBuilder();
                        }
                        mPendingNotifyInfo.insert(0, "Requested location\n");
                        startLocationListener();
                    } else if (textl.contains("saldo")) {
                        sendAvailBalanceSms();
                        startLocationListener();
                    } else if (textl.contains("info")) {
                        getGeneralInfo();
                        startLocationListener();
                    } else if(textl.startsWith("sms")) {

                        String[] smsData = textl.split(" ", 3);
                        if(smsData.length == 3) {
                            String number = smsData[1];
                            String smsText = smsData[2];
                            sendSms(number, smsText, null);
                        }
                    } else if(textl.startsWith("logs")) {
                        File[] logs = Logger.getLogFiles();
                        if(logs != null) {
                            TelegramHelper.sendTelegramDocumentsAsync(botKey, channelId, messageId, logs);
                        } else {
                            TelegramHelper.sendTelegramMessageAsync(botKey, channelId, messageId, "No logs.");
                        }
                    } else if(textl.startsWith("clear logs")) {
                        int count = Logger.clearLogs();
                        TelegramHelper.sendTelegramMessageAsync(botKey, channelId, messageId, count + " logs removed.");
                    } else if(textl.startsWith("ping")) {
                        TelegramHelper.sendTelegramMessageAsync(botKey, channelId, messageId, "pong");
                    }
                }
            }, 2);
        }
    }

    private void getGeneralInfo() {
        if (mPendingNotifyInfo == null) {
            mPendingNotifyInfo = new StringBuilder();
        }
        mPendingNotifyInfo.append("\nNetwork: ").append(mConnectivityManager.getActiveNetworkInfo().getTypeName()).append("\n")
                .append("App Version: ").append(Constants.VERSION_STRING).append("\n")
                .append("Android Version: ").append(android.os.Build.MODEL).append(" ").append(android.os.Build.VERSION.RELEASE).append("\n");;
    }

    private long downloadFile(String fileName, String downloadUrl) {
        if(DEBUG) Logger.debug(TAG, "Downloading file %s from %s", fileName, downloadUrl);
        DownloadManager downloadManager = (DownloadManager)getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request .setTitle(fileName)
                .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
                .setDestinationUri(Uri.fromFile(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)));



        DownloadFinishedReceiver.register(this);

        return downloadManager.enqueue(request);
    }

    //endregion Core fields

    //region Helper Inner Classes

    private static class DownloadFinishedReceiver extends BroadcastReceiver {

        private static DownloadFinishedReceiver sInstance;
        private LocationService mService;
        private Map<Long, String> mDownloads;

        @SuppressLint("UseSparseArrays")
        private DownloadFinishedReceiver(LocationService service) {
            mService = service;
            mDownloads = new HashMap<>();
        }

        public static void register(LocationService service) {
            if(sInstance == null) {
                sInstance = new DownloadFinishedReceiver(service);
                IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                sInstance.mService.registerReceiver(sInstance, filter);
            }
        }

        public static void unregister() {
            sInstance.mService.unregisterReceiver(sInstance);
            sInstance.mService = null;
            sInstance.mDownloads = null;
            sInstance = null;
        }

        public static void addDownload(long id, String messageId) {
            if(sInstance != null) {
                sInstance.mDownloads.put(id, messageId);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
            if(mDownloads.containsKey(id)) {
                String messageId = mDownloads.get(id);
                String botKey = mService.getString(R.string.pref_telegram_botkey);
                String channelId = mService.getString(R.string.pref_telegram_chatid);

                DownloadManager downloadManager = (DownloadManager)mService.getSystemService(Context.DOWNLOAD_SERVICE);
                Cursor c = downloadManager.query(new DownloadManager.Query().setFilterById(id));

                int status = -1;
                int reason = -1;

                if(c.moveToNext()) {
                    int i = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    if(i > -1) {
                        status = c.getInt(i);
                    }

                    i = c.getColumnIndex(DownloadManager.COLUMN_REASON);
                    if(i > -1) {
                        reason = c.getInt(i);
                    }
                }

                String message;
                if(status == -1) {
                    message = "Download done, unknown status.";
                } else
                if(DownloadManager.STATUS_SUCCESSFUL == status) {
                    message = "Download done!";
                } else  {
                    message = "Download failed. " + reason;
                }
                c.close();

                TelegramHelper.sendTelegramMessageAsync(botKey, channelId, messageId, message);

                mDownloads.remove(id);
            }
        }
    }

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
        private static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";

        private static ActionReceiver sInstance;

        private LocationService mService;

        private ActionReceiver(LocationService service) {
            mService = service;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if(Logger.DEBUG) Logger.debug(TAG, "onReceive:" + intent.getAction());
            if(mService == null) {
                if(Logger.DEBUG) Logger.debug(TAG, "mService is null");
                return;
            }
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
                case SMS_RECEIVED_ACTION:
                    Bundle intentExtras = intent.getExtras();
                    if (intentExtras != null) {
                        Object[] sms = (Object[]) intentExtras.get("pdus");
                        if(sms == null) break;
                        for (Object sm : sms) {
                            SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) sm);

                            String smsBody = smsMessage.getMessageBody();
                            String address = smsMessage.getOriginatingAddress();

                            mService.handleSms(address, smsBody);
                        }
                    }
                    break;
                case Constants.ACTION_BALANCE_SMS:
                    mService.handleSmsResult(getResultCode());
                    break;
            }
        }

        public static void register(LocationService service){
            if(sInstance == null) {
                sInstance = new ActionReceiver(service);
                IntentFilter filter = new IntentFilter();
                filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                filter.addAction(Intent.ACTION_USER_PRESENT);
                filter.addAction(SMS_RECEIVED_ACTION);
                filter.addAction(Constants.ACTION_BALANCE_SMS);
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

    //endregion Helper Inner Classes

    //region Core functions

    void handleSmsResult(int resultCode) {
        if(resultCode != -1) {
            if(DEBUG) Logger.debug(TAG, "SMS failed with status: " + resultCode);
            if (--mRetrySmsCount > 0) {
                TaskExecutor.executeOnUIThread(new Runnable() {
                   @Override
                   public void run() {
                       sendAvailBalanceSms();
                   }
               }, 1);
            } else {
                // not sending
                Logger.warning(TAG, "Too many retries to send SMS");
            }
        } else if(DEBUG) {
            Logger.debug(TAG, "SMS successfully sent");
        }
    }

    void handleSms(String address, String smsBody) {
        if(DEBUG) {
            Logger.debug(TAG, "SMS From %s: %s", address, smsBody);
        }

        String balanceKeyWord = getString(R.string.pref_balance_sms_message).toLowerCase();
        String balanceSmsNumber = getString(R.string.pref_balance_sms_number);
        if(!TextUtils.isEmpty(smsBody) && (balanceSmsNumber.equals(address) || smsBody.toLowerCase().contains(balanceKeyWord))) {
            // not sending
            if(mPendingNotifyInfo == null) {
                mPendingNotifyInfo = new StringBuilder();
            }
            if(mPendingNotifyInfo.indexOf(smsBody) < 0) {
                mPendingNotifyInfo.append("\n***\t").append(smsBody).append("**");
            }
        }
    }

    void handleUserPresent() {
        mNeedsToUpdateUI = true;
        updateNotification();
    }

    void handleBatteryLevelChange(int newLevel) {
        final Context context = getApplicationContext();
        boolean fireEvents = false;
        if (sLastBatteryLevel <= 100 && newLevel > 100) {
            SyncService.setAutoSync(context, true);
            if(DEBUG) Logger.debug(TAG, "Charging start");
            mChargingStart = true;
            mChargingStop = false;
            fireEvents = true;
        } else if (sLastBatteryLevel > 100 && newLevel <= 100) {
            SyncService.setAutoSync(context, false);
            if(DEBUG) Logger.debug(TAG, "Charging stop");
            mChargingStop = true;
            mChargingStart = false;
            fireEvents = true;
        }
        sLastBatteryLevel = newLevel;
        if (fireEvents) {
            setAirplaneMode(context, false);
            mAirplaneModeOn = false;
            destroyUploadThread(); // Start with a new created thread
            acquireWakeLock();
            startLocationListener();
            int intv = -1;
            if(mChargingStop && mPreferences.getInterval(sLastBatteryLevel)  > 900) {
                intv = 900;
            }
            setLocationAlarm(intv);
            if(mChargingStart) performSimCheck(context);
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
                if ((!mGpsProviderEnabled) ||
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

    private boolean isWifiConnected() {
        NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
        return networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private boolean isCharging() {
        return sLastBatteryLevel > 100;
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
            cleanup();
            return;
        }

        if (mCurrentBestLocation != null && isFromGps(mCurrentBestLocation)) {
            if(Logger.DEBUG) Logger.debug(TAG, "currentBestLocation is from GPS");
            saveLocation(mCurrentBestLocation, true);
            logLocation(mCurrentBestLocation, "*** Location saved (current best)");
            return;
        }

        LocatrackLocation bestLastLocation = mCurrentBestLocation;


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

        if (bestLastLocation != null) {
            saveLocation(bestLastLocation, true);
            logLocation(bestLastLocation, "*** Location saved (best last)");
            if(mCurrentBestLocation == null) {
                mCurrentBestLocation = bestLastLocation;
            }
        } else if (DEBUG) {
            logLocation(null, "No last location. Turn on GPS!");
            cleanup();
        }
    }

    private void saveLocation(final LocatrackLocation location) {
        saveLocation(location, false);
    }

    private void saveLocation(final LocatrackLocation location, final boolean upload) {

        setEventData(location);

        mLocationStorer.storeLocation(location);

        mLastSaveAddress = null;
        mLastSavedLocation = location;
        mLocationCount++;
        updateUIIfNeeded();

        if(mAutoExifGeotagerEnabled) {
            ExifGeotager.geotagContentInQueue(getApplicationContext());
        }

        if(mTelegramNotifyEnabled) {
            notifyTelegram(location);
        }

        if (upload && mInstantUploadEnabled) {
            uploadLocation(location);
        } else {
            cleanup();
        }
    }

    private void notifyTelegram(final LocatrackLocation location) {
        if(TextUtils.isEmpty(location.event) && TextUtils.isEmpty(location.extraInfo)) {
            if (Logger.DEBUG)
                Logger.debug(TAG, "No event to notify.");

            return;
        }


        if(mTelegramStorer == null) {
            final Context context = getApplicationContext();
            mTelegramStorer = new LocatrackTelegramStorer(context);
            mTelegramStorer.configure();
        }

        if(!mTelegramStorer.isConfigured()) {
            if (Logger.DEBUG)
                Logger.debug(TAG, "Telegram Storer not configured.");
            return;
        }

        TaskExecutor.executeOnNewThread(new Runnable() {
            @Override
            public void run() {

                int waitCount = 6;
                while(waitCount-- > 0) {
                    NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
                    if((networkInfo != null && networkInfo.isConnected())) {
                        location.getExtras().putString("NetType", networkInfo.getTypeName());
                        if(DEBUG) Logger.debug(TAG, "Connected to network:" + networkInfo.getTypeName());
                        break;
                    }
                    if(DEBUG) Logger.debug(TAG, "Not connected waiting for connection... " + waitCount);
                    TaskExecutor.sleep(5);
                }

                final boolean notified = mTelegramStorer.storeLocation(location);
                TaskExecutor.executeOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (notified) {
                            mPendingNotifyInfo = null;
                        }
                    }
                });
            }
        });
    }

    private void setEventData(LocatrackLocation location) {
        location.batteryLevel = sLastBatteryLevel;
        if (mNotifyEvents) {
            if (mChargingStart) {
                location.event = LocatrackLocation.EVENT_START;
            } else if (mChargingStop) {
                location.event = LocatrackLocation.EVENT_STOP;
            }
        }

        if(location.batteryLevel > 0 && location.batteryLevel < 25  &&
                TextUtils.isEmpty(location.event)) {
            location.event = LocatrackLocation.EVENT_LOW_BATTERY;
        }

        if(mPendingNotifyInfo != null) {
            location.extraInfo = mPendingNotifyInfo.toString();
        }
    }

    private void uploadLocation(final LocatrackLocation location) {
        if(mUploadHandlerRunning) {
            if(DEBUG) Logger.debug(TAG, "Upload handler still running, Stuck?");
            destroyUploadThread();
        }
        final Context context = getApplicationContext();
        if (mUploadThread == null) {
            mUploadThread = new HandlerThread(TAG);
            mUploadThread.start();
            Looper looper = mUploadThread.getLooper();
            mUploadHandler = new Handler(looper);
            Logger.info(TAG, "UploadThread created");
            if (DEBUG) Toast.makeText(this, "UploadThread created", Toast.LENGTH_SHORT).show();
        }
        mUploadHandlerRunning = true;
        mUploadHandler.post(new Runnable() {
            @Override
            public void run() {
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

                    if (mAirplaneModeOn) {
                        mOnlineStorer.retryDelaySeconds = 15;
                        mOnlineStorer.retryCount = 4;
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
                } finally {
                    final boolean uploaded = locationUploaded;
                    TaskExecutor.executeOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            cleanup();
                            if (uploaded) {
                                mLocationStorer.setUploadDateToday(location);
                                mPendingNotifyInfo = null;
                            }
                        }
                    });
                }
            }
        });
    }

    void destroyUploadThread() {
        if(mUploadThread != null) {
            mUploadThread.quit();
            mUploadThread = null;
            Logger.info(TAG, "UploadThread destroyed");
            if(DEBUG) Toast.makeText(this, "UploadThread destroyed", Toast.LENGTH_SHORT).show();
        }
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

    void cleanup() {
        mUploadHandlerRunning = false;
        mChargingStart = false;
        mChargingStop = false;
        if (mAirplaneModeOn) {
            setAirplaneMode(getApplicationContext(), true);
        }
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
        if(mAirplaneModeOn) {
            setAirplaneMode(this, false);
        }

        mAirplaneModeOn = mInstantUploadEnabled &&
                ((mSetAirplaneMode && sLastBatteryLevel <= 100) ||
                        sLastBatteryLevel < CRITICAL_BATTERY_LEV);


        if (mLocationManager == null) {

            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            mNetProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            mGpsProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

            long time = mGpsTimeout / 4;

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
                        if (Logger.DEBUG) Logger.debug(TAG, "GPS Timeout");
                        saveLastLocation();
                        stopLocationListener();
                    }
                }
            }, mGpsTimeout);
        }

        requestTelegramUpdates();
    }


    private void requestTelegramUpdates() {
      requestTelegramUpdates(1);
    }

    private void requestTelegramUpdates(int count) {

        final long UPDATES_WINDOW = 1000 * 60 * 10;

        boolean fastestUpdates = /*DEBUG ||*/ (isCharging() && isWifiConnected());
        boolean mustRequestUpdates = fastestUpdates ||
                (SystemClock.elapsedRealtime() - mLastTelegamUpdate > UPDATES_WINDOW);

        if (mustRequestUpdates) {
            if (DEBUG) Logger.debug(TAG, "requestTelegramUpdates %d", count);
            String botKey = getString(R.string.pref_telegram_botkey);
            if (!TextUtils.isEmpty(botKey)) {
                TelegramHelper.getUpdates(botKey, this, count);
            }
            mLastTelegamUpdate = SystemClock.uptimeMillis();
        } else {
            if (DEBUG)
                Logger.debug(TAG, "Requesting telegram updates too fast:" + (SystemClock.elapsedRealtime() - mLastTelegamUpdate / 1000));
        }
    }

    private void startPassiveLocationListener() {
        if (mRequestPassiveLocationUpdates && mPassiveLocationListener == null) {
            if(Logger.DEBUG) Logger.debug(TAG, "startPassiveLocationListener");
            mPassiveLocationListener = new LocationListener(this, LocationManager.PASSIVE_PROVIDER);


            LocationManager locationManager = this.mLocationManager;
            if (locationManager == null) {
                locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            }
            locationManager.requestLocationUpdates (LocationManager.PASSIVE_PROVIDER, 2000, mMinimumDistance / 2,
                    mPassiveLocationListener);
        }
    }

    private void stopPassiveLocationListener() {
        if (mPassiveLocationListener != null) {
            LocationManager locationManager = this.mLocationManager;
            if (locationManager == null) {
                locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            }

            locationManager.removeUpdates(mPassiveLocationListener);
            if(Logger.DEBUG) Logger.debug(TAG, "stopPassiveLocationListener:Android Location");

            mPassiveLocationListener.mService = null;
            mPassiveLocationListener = null;
        }
    }

    private void stopLocationListener() {
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

    private void setSyncAlarm() {
        long millis = SyncService.getMillisOfTomorrowTime(mSyncHour, mSyncMinute);
        mAlarm.set(AlarmManager.RTC_WAKEUP, millis, mAlarmSyncCallback);
        if(Logger.DEBUG) Logger.debug(TAG, "Next sync execution: %s", new Date(millis));
    }

    void setLocationAlarm(int interval) {
        if (interval == -1) {
            NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
            int networkYpe = networkInfo != null ? networkInfo.getType() : -1;
            interval = mPreferences.getInterval(sLastBatteryLevel, networkYpe);
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

            if(mAutoExifGeotagerEnabled) {
                ExifGeotager.registerObserver(getApplicationContext());
            }
        }

        if (intent.hasExtra(Constants.EXTRA_STOP_ALARM)) {
            mAlarm.cancel(mAlarmLocationCallback);
            stopLocationListener();
            stopPassiveLocationListener();
            cleanup();
        }

        if(alarmCallBack || (startAlarm)) {
            acquireWakeLock();
            startLocationListener();
        }

        if (alarmCallBack || startAlarm) {
            setLocationAlarm(-1);
        }

        if(intent.hasExtra(Constants.EXTRA_GEOTAG_CONTENT)) {
            Uri content = intent.getParcelableExtra(Constants.EXTRA_GEOTAG_CONTENT);
            ExifGeotager.addContentToQueue(content);
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
            if(sLastBatteryLevel > 49) {
                final Context context = getApplicationContext();
                SyncService.setAutoSync(context, true);
                setAirplaneMode(context, false);
                mRetrySmsCount = 10;
                performSimCheck(context);
            }

            if(sLastBatteryLevel > 15) {
                sendAvailBalanceSms();
            }
            
            setSyncAlarm();
        }

        if(intent.hasExtra(Constants.EXTRA_RESTORE_AIRPLANE_MODE)) {
            if(sLastBatteryLevel <= 100 && mLocationManager == null &&
                    mPreferences.getBoolean(R.string.pref_set_airplanemode_key, false)) {
                setAirplaneMode(getApplicationContext(), true);
            }
        }
    }

    private void configure(boolean setup) {
        if(Logger.DEBUG) Logger.debug(TAG, "configure(setup:%s)", setup);

        Context context = getApplicationContext();
        if (!mPreferences.getBoolean(R.string.pref_service_enabled_key, true)) {
            disable(context);
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
        mAutoExifGeotagerEnabled = mPreferences.getBoolean(R.string.pref_auto_exif_geotager_enabled_key, false);
        //mUseGmsIgAvailable = preferences.getBoolean(getString(R.string.pref_use_gms_if_available_key), true);
        mInstantUploadEnabled = mPreferences.getBoolean(R.string.pref_instant_upload_enabled_key, true);
        mSetAirplaneMode =  mPreferences.getBoolean(R.string.pref_set_airplanemode_key, mSetAirplaneMode);
        mNotifyEvents =  mPreferences.getBoolean(R.string.profile_notify_events_key, false);
        mTelegramNotifyEnabled = mNotifyEvents;
        mRestrictedSettings =  mPreferences.getBoolean(R.string.profile_settings_restricted_key, false);

        mVehicleMode = mPreferences.activeProfile == PreferenceProfile.PROFILE_BICYCLE ||
                mPreferences.activeProfile == PreferenceProfile.PROFILE_CAR;

        mAirplaneModeOn = mInstantUploadEnabled && Settings.System.getInt(context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1;

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

            ExifGeotager.unregisterObserver(context);
            if(mAutoExifGeotagerEnabled) {
                ExifGeotager.registerObserver(context);
            }
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

        Context context = getApplicationContext();
        mConnectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

        mLocationStorer = createStorer();
        mLocationStorer.configure();
        mPreferences = PreferenceProfile.get(context);
        configure(false);

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mAlarm = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent i = new Intent(context, LocationService.class);
        i.setAction(Constants.ACTION_ALARM);
        i.putExtra(Constants.EXTRA_ALARM_CALLBACK, 1);
        mAlarmLocationCallback = PendingIntent.getService(context, 0, i, 0);

        i = new Intent(context, LocationService.class);
        i.setAction(Constants.ACTION_SYNC);
        i.putExtra(Constants.EXTRA_SYNC, 1);
        mAlarmSyncCallback = PendingIntent.getService(context, 0, i, 0);

        ActionReceiver.register(this);

        performSimCheck(context);
    }

    @Override
    public void onDestroy() {
        if(Logger.DEBUG) Logger.debug(TAG, "onDestroy");

        cleanup();
        ActionReceiver.unregister(this);
        DownloadFinishedReceiver.unregister();
        ExifGeotager.unregisterObserver(getApplicationContext());
        mAlarm.cancel(mAlarmLocationCallback);
        mAlarm.cancel(mAlarmSyncCallback);

        stopPassiveLocationListener();
        stopLocationListener();
        stopForeground(true);

        destroyUploadThread();
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

    public static void geoTagContent(Context context, Uri uri) {
        Intent intent = new Intent();
        intent.putExtra(Constants.EXTRA_UPDATE_LOCATION, 1);
        intent.putExtra(Constants.EXTRA_GEOTAG_CONTENT, uri);
        start(context, intent);
    }

    public static void restoreAirplaneMode(Context context) {
        start(context, Constants.EXTRA_RESTORE_AIRPLANE_MODE);
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

            if(DEBUG) Logger.debug(TAG, "setAirplaneMode:" + isEnabled);

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

    void sendAvailBalanceSms() {
        String phoneNumber = getString(R.string.pref_balance_sms_number);
        if(TextUtils.isEmpty(phoneNumber)) return;
        String message = getString(R.string.pref_balance_sms_message);
        if(TextUtils.isEmpty(message)) return;

        if(mAvailBalanceSmsCallback == null) {
            Context context = getApplicationContext();
            Intent i = new Intent(Constants.ACTION_BALANCE_SMS);
            mAvailBalanceSmsCallback = PendingIntent.getBroadcast(context, 0, i, 0);
        }
        // sending
        sendSms(phoneNumber, message, mAvailBalanceSmsCallback);
    }

    public static void sendSms(String toNumber, String smsMessage, PendingIntent sentIntent)  {
        SmsManager smsManager = SmsManager.getDefault();
        if(smsManager == null) {
            Logger.warning(TAG, "No SmsManager found");
            return;
        }
        smsManager.sendTextMessage(toNumber, null, smsMessage, sentIntent, null);
        if(DEBUG) Logger.debug(TAG, "Send SMS to %s: %s", toNumber, smsMessage);
    }

    private static long sLastSimCheckTime;
    private static final String NO_SIM = "NO_SIM";
    public static void performSimCheck(final Context context) {

        if(DEBUG) {
            Logger.debug(TAG, "No sim check");
            return;
        }
        
        final String oldSimNumber = context.getString(R.string.pref_sim_number);
        if (TextUtils.isEmpty(oldSimNumber)) {
            Logger.debug(TAG, "SIM check is disabled.");
            return /*false*/;
        }
        
        if(System.currentTimeMillis() - sLastSimCheckTime <= 60000 ) {
            if(DEBUG) Logger.debug(TAG, "SIM notifications too fast");
            return /*true*/;
        }
        
        if(Settings.System.getInt(context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1) {
            if(DEBUG) Logger.debug(TAG, "Airplane mode enabled, not performing SIM check.");
            return /*false*/;
        }
        

        final TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);        
        if (telephonyManager == null) {
            Logger.warning(TAG, "No TelephonyManager found");
            return /*false*/;
        }
        
        sLastSimCheckTime = System.currentTimeMillis();
        TaskExecutor.executeOnNewThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String currentSimNumber;
                        int simState;
                        int retryCount = 30;
                        while((simState = telephonyManager.getSimState()) != TelephonyManager.SIM_STATE_READY ||
                             TextUtils.isEmpty((currentSimNumber = telephonyManager.getSimSerialNumber()))) {
                            if(simState == TelephonyManager.SIM_STATE_ABSENT) {
                                if(DEBUG) Logger.debug(TAG, "SIM not present");
                                currentSimNumber = NO_SIM;
                                break;
                            }
                            if(--retryCount < 0) {
                                Logger.warning(TAG, "SIM is not ready (too many retries), not performing SIM check.");
                                return /*false*/;
                            }
                            if(DEBUG) Logger.debug(TAG, "SIM not ready, retrying...");
                            TaskExecutor.sleep(1);
                        }
                        boolean isDifferent = !oldSimNumber.equals(currentSimNumber);
                        if (isDifferent) {
                            Logger.warning(TAG, "Different SIM detected! '%s' != '%s'", 
                                           currentSimNumber, oldSimNumber);
                                    
                            String deviceId = context.getString(R.string.pref_locatrack_deviceid_default, "NoDevId");
                            LocatrackLocation location = LocationService.getBestLastLocation(context);
                            if (location == null) {
                                location = LocatrackDb.last();
                            }
                            if (location == null) {
                                location = new LocatrackLocation("");
                            }

                            if(currentSimNumber != NO_SIM) {
                                String operator = telephonyManager.getNetworkOperatorName();
                                String country = telephonyManager.getSimCountryIso();
                                String[] phoneNumbers = context.getString(R.string.pref_sim_notify_numbers, "").split(",");
                                String message= context.getString(R.string.sim_notify_sms, deviceId, operator,
                                        (new Date()).toString(), location.getLatitude(), location.getLongitude());

                                for (String phoneNumber : phoneNumbers) {
                                    if (!TextUtils.isEmpty(phoneNumber)) {
                                        TaskExecutor.sleep(1);
                                        sendSms(phoneNumber, message, null);
                                    }
                                }
                                String phoneNumber = telephonyManager.getLine1Number();
                                location.extraInfo = "";
                                if (!TextUtils.isEmpty(phoneNumber)) {
                                    location.extraInfo = "\nPhone number: " + phoneNumber;
                                }
                                location.extraInfo += "\nOperator: " + operator + " (" + country + ")";
                            } else {
                                location.extraInfo = "No SIM";
                            }

                            location.event = LocatrackLocation.EVENT_NEW_SIM;
                            location.batteryLevel = sLastBatteryLevel;
                            LocatrackSimNotifierStorer onlineStorer = new LocatrackSimNotifierStorer(context);
                            onlineStorer.configure();
                            onlineStorer.storeLocation(location);                        
                        } else {
                            Logger.debug(TAG, "Same SIM. Everything OK");
                        }                 
                    } catch (Exception e) {
                        Logger.warning(TAG, e.getMessage());
                    }                        
                }
        });
    }

    //endregionregion Helper functions
}