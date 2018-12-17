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
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
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
import android.telephony.SmsMessage;
import android.text.TextUtils;
import android.widget.Toast;

import com.hmsoft.locationlogger.BuildConfig;
import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Constants;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.PerfWatch;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;
import com.hmsoft.locationlogger.data.Geocoder;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;
import com.hmsoft.locationlogger.data.commands.Command;
import com.hmsoft.locationlogger.data.locatrack.LocatrackDb;
import com.hmsoft.locationlogger.data.locatrack.LocatrackTelegramStorer;
import com.hmsoft.locationlogger.data.locatrack.LocatrackTripStorer;
import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;
import com.hmsoft.locationlogger.receivers.StartServiceReceiver;
import com.hmsoft.locationlogger.ui.MainActivity;

import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CoreService extends Service
    implements TelegramHelper.UpdateCallback {

    //region Static fields
    private static final String TAG = "CoreService";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    private static final boolean DIAGNOSTICS = DEBUG;
    private static final int HALF_MINUTE = 1000 * 30;
    private static final int CRITICAL_BATTERY_LEV = 50;

    //endregion Static fields

    //region Settings fields
    private int mMinimumDistance = 20; //meters
    private int mGpsTimeout = 60; //seconds
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
    private boolean mUnlimitedData;
    LocationManager mLocationManager;
    private boolean mNetProviderEnabled;
    private boolean mGpsProviderEnabled;
    private boolean mTimeoutRoutinePending;
    private LocationListener mNetLocationListener;
    private LocationListener mGpsLocationListener;
    private LocatrackLocation mCurrentBestLocation;
    private PowerManager mPowerManager;
    private ComponentName mMapIntentComponent = null;
    private ConnectivityManager mConnectivityManager;
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
    private PendingIntent mAvailBalanceSmsCallback = null;
    private PendingIntent mLocationActivityIntent = null;
    private Intent mMapIntent = null;
    private PendingIntent mUpdateLocationIntent = null;
    private boolean mStoreHandlerRunning;
    private int mRetrySmsCount;

    LocationStorer[] mLocationStorers;

    boolean mNeedsToUpdateUI;
    private WakeLock mWakeLock;

    private HandlerThread mStoreThread = null;
    private Handler mStoreHandler;

    static int sLastBatteryLevel = 99;
    boolean mChargingStart;
    boolean mChargingStop;
    boolean mChargingStartStop;
    boolean mAirplaneModeOn;


    StringBuilder mPendingNotifyInfo;

    private String[] mTelegramAllowedFrom = null;
    private long mLastTelegamUpdate;
    private String[] mPhoneNumbers;

    //endregion Core fields

    //region Telegram Methods
    @Override
    public void onTelegramUpdateReceived(String chatId, final String messageId, final String text) {
        if (DEBUG)
            Logger.debug(TAG, "Telegram:onTelegramUpdateReceived: ChatId: %s, TelegramMessage: %s", chatId, text);

        final String channelId = mPreferences.getString(R.string.pref_telegram_chatid_key, getString(R.string.pref_telegram_chatid_default));
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
            String msg = String.format("You are not my master!\n\nmsg:\"%s\"\nid:%s", text, chatId);
            Logger.warning(TAG, msg);
            final String botKey = mPreferences.getString(R.string.pref_telegram_botkey_key, getString(R.string.pref_telegram_botkey_default));
            TelegramHelper.sendTelegramMessage(botKey, channelId, null, msg);
            return;
        }

        processTextMessage(null, messageId, text);

        TaskExecutor.executeOnUIThread(new Runnable() {
            @Override
            public void run() {
                requestTelegramUpdates(2);
            }
        }, 1);
    }

    private void processSmsMessage(final String fromSmsNumber, final String text) {
        TaskExecutor.executeOnNewThread(new Runnable() {
            @Override
            public void run() {
                processTextMessage(fromSmsNumber, null, text);
            }
        });
    }

    private synchronized void processTextMessage(final String fromSmsNumber, final String messageId, String text) {

        final String botKey = mPreferences.getString(R.string.pref_telegram_botkey_key, getString(R.string.pref_telegram_botkey_default));
        final String channelId = mPreferences.getString(R.string.pref_telegram_chatid_key, getString(R.string.pref_telegram_chatid_default));

        int source;
        String fromId;
        if (TextUtils.isEmpty(fromSmsNumber)) {
            source = Command.SOURCE_TELEGRAM;
            fromId = channelId;
        } else {
            source = Command.SOURCE_SMS;
            fromId = fromSmsNumber;
        }

        Command.CommandContext context = new Command.CommandContext(this, source, botKey, fromId, messageId);

        String[] commnadParams = text.split(" ", 2);
        Command command = Command.getCommand(commnadParams[0]);
        if (command != null) {
            command.setContext(context);
            command.execute(commnadParams);
        } else {
            Command.sendReply(context, "Command not found.");
        }
    }

    //endregion Telegram Methods

    //region Helper Inner Classes

    private static class ActionReceiver extends BroadcastReceiver {
        private static final String TAG = "UserPresentReceiver";
        private static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";

        private static ActionReceiver sInstance;

        private CoreService mService;

        private ActionReceiver(CoreService service) {
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

        public static void register(CoreService service){
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

        CoreService mService;
        String mProvider;

        public LocationListener(CoreService service, String provider) {
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

        private CoreService mService;

        private GetAddressTask(CoreService service) {
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

        public static void run(CoreService service, Location location) {
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
        } else {
            if(mPhoneNumbers == null) {
                mPhoneNumbers = getApplicationContext().getString(R.string.pref_sim_notify_numbers).split(",");
            }
            for(String pn : mPhoneNumbers) {
                if(pn.equals(address)) {
                    processSmsMessage(address, smsBody);
                    break;
                }
            }
        }
    }

    void handleUserPresent() {
        mNeedsToUpdateUI = true;
        updateNotification();

        requestTelegramUpdates(2, true);
    }

    void handleBatteryLevelChange(int newLevel) {
        boolean fireEvents = false;
        if (sLastBatteryLevel <= 100 && newLevel > 100) {
            if(DEBUG) Logger.debug(TAG, "Charging start");
            mChargingStart = true;
            mChargingStop = false;
            fireEvents = true;
            requestTelegramUpdates();
        } else if (sLastBatteryLevel > 100 && newLevel <= 100) {
            if(DEBUG) Logger.debug(TAG, "Charging stop " + mChargingStart);
            mChargingStartStop = mChargingStart;
            mChargingStop = !mChargingStartStop;
            mChargingStart = false;
            fireEvents = true;
        }
        sLastBatteryLevel = newLevel;
        if (fireEvents) {
            mAirplaneModeOn = false;
            destroyStoreThread(); // Start with a new created thread
            acquireWakeLock();
            startLocationListener();
            int intv = -1;
            if(mChargingStop && mPreferences.getInterval(sLastBatteryLevel)  > 150) {
                intv = 150;
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

        if (Logger.DEBUG) Logger.debug(TAG, "handleLocation %s", location);

        if (Utils.isBetterLocation(location, mCurrentBestLocation, timeDelta, mMinimumAccuracy,
                mMaxReasonableSpeed)) {


            mCurrentBestLocation = new LocatrackLocation(location);
            if ((!mGpsProviderEnabled) ||
                    (Utils.isFromGps(mCurrentBestLocation) && location.getAccuracy() <= mBestAccuracy)) {
                saveLocation(mCurrentBestLocation);
                message = "*** Location saved";
                stopLocationListener();
            } else {
                message = "No good GPS location.";
            }

        } else {
            message = "Location is not better than last location.";
        }

        logLocation(location, message);
    }

    private void insertNotifyInfo(String notifyInfo) {
        if (mPendingNotifyInfo == null) {
            mPendingNotifyInfo = new StringBuilder();
        }
        mPendingNotifyInfo.insert(0, notifyInfo);
    }

    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        return Utils.isBetterLocation(location, currentBestLocation, HALF_MINUTE, mMinimumAccuracy,
                mMaxReasonableSpeed);
    }

    private boolean isWifiConnected() {
        NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }

    private boolean isCharging() {
        return sLastBatteryLevel > 100;
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

        if (mCurrentBestLocation != null && Utils.isFromGps(mCurrentBestLocation)) {
            if(Logger.DEBUG) Logger.debug(TAG, "currentBestLocation is from GPS");
            saveLocation(mCurrentBestLocation);
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
            saveLocation(bestLastLocation);
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

        setEventData(location);


        storeLocation(location);

        mLastSaveAddress = null;
        mLastSavedLocation = location;
        mLocationCount++;
        updateUIIfNeeded();

    }

    private void setEventData(LocatrackLocation location) {
        location.batteryLevel = sLastBatteryLevel;
        if (mNotifyEvents) {
            if (mChargingStart) {
                location.event = LocatrackLocation.EVENT_START;
            } else if (mChargingStop) {
                location.event = LocatrackLocation.EVENT_STOP;
            } else if(mChargingStartStop) {
                location.event = LocatrackLocation.EVENT_RESTOP;
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

    private void storeLocation(final LocatrackLocation location) {
        if (mStoreHandlerRunning) {
            if (DEBUG) Logger.debug(TAG, "Store handler still running, Stuck?");
            destroyStoreThread();
        }

        if (mStoreThread == null) {
            mStoreThread = new HandlerThread(TAG);
            mStoreThread.start();
            Looper looper = mStoreThread.getLooper();
            mStoreHandler = new Handler(looper);
            if(DEBUG) Logger.info(TAG, "StoreThread created");
            if (DEBUG) Toast.makeText(this, "StoreThread created", Toast.LENGTH_SHORT).show();
        }

        mStoreHandlerRunning = true;
        mStoreHandler.post(new Runnable() {
            @Override
            public void run() {
                boolean locationStored = true;

                try {
                    if (DIAGNOSTICS && mLocationLogEnabled) {
                        Logger.info(TAG, "Store: %s", location);
                    }

                    PerfWatch pw = null;

                    if (DIAGNOSTICS && mLocationLogEnabled) {
                        pw = PerfWatch.start(TAG, "Start: Store location");
                    }

                    for (LocationStorer storer : mLocationStorers) {
                        locationStored = locationStored & storer.storeLocation(location);
                    }

                    if (DIAGNOSTICS && mLocationLogEnabled) {
                        if (pw != null) {
                            pw.stop(TAG, "End: Upload location Success: " + locationStored);
                        }
                    }
                } finally {
                    final boolean uploaded = locationStored;
                    TaskExecutor.executeOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            cleanup();
                            if (uploaded) {
                                mPendingNotifyInfo = null;
                            }
                        }
                    });
                }
            }
        });
    }

    void destroyStoreThread() {
        if(mStoreThread != null) {
            mStoreThread.quit();
            mStoreThread = null;
            if(DEBUG) Logger.info(TAG, "StoreThread destroyed");
            if(DEBUG) Toast.makeText(this, "StoreThread destroyed", Toast.LENGTH_SHORT).show();
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
                mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "locatrack:wakelock:location");
                mWakeLock.setReferenceCounted(false);
            }
            if(!mWakeLock.isHeld()) {
                if (DIAGNOSTICS && mLocationLogEnabled) Logger.info(TAG, "acquireLocationLock");
                mWakeLock.acquire();
            }
        }
    }

    void cleanup() {
        mStoreHandlerRunning = false;
        mChargingStart = false;
        mChargingStop = false;
        mChargingStartStop = false;
        if (mAirplaneModeOn) {
            Utils.setAirplaneMode(getApplicationContext(), true);
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
                    Intent updateIntent = new Intent(context, CoreService.class);
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
            Utils.setAirplaneMode(this, false);
        }

        mAirplaneModeOn = ((mSetAirplaneMode && sLastBatteryLevel <= 100) ||
                        sLastBatteryLevel < CRITICAL_BATTERY_LEV);

        if (mLocationManager == null) {

            mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

            mNetProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            mGpsProviderEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

            long time = mGpsTimeout / 4;
            if(DEBUG) {
                mGpsTimeout = 10;
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
      requestTelegramUpdates(1, false);
    }

    private void requestTelegramUpdates(int count) {
        requestTelegramUpdates(count, false);
    }

    private void requestTelegramUpdates(int count, boolean now) {

        final long UPDATES_WINDOW = 1000 * 60 * 10;

        boolean fastestUpdates = DEBUG || now || (isCharging() && (mUnlimitedData || isWifiConnected()));
        boolean mustRequestUpdates = fastestUpdates ||
                (SystemClock.elapsedRealtime() - mLastTelegamUpdate > UPDATES_WINDOW);

        if (mustRequestUpdates) {
            if (DEBUG) Logger.debug(TAG, "requestTelegramUpdates %d", count);
            final String botKey = mPreferences.getString(R.string.pref_telegram_botkey_key, getString(R.string.pref_telegram_botkey_default));
            if (!TextUtils.isEmpty(botKey)) {
                TelegramHelper.getUpdates(botKey, this, count);
            }
            mLastTelegamUpdate = SystemClock.uptimeMillis();
        } else {
            if (DEBUG)
                Logger.debug(TAG, "Requesting telegram updates too fast:" + (SystemClock.elapsedRealtime() - mLastTelegamUpdate / 1000));
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
        long millis = Utils.getMillisOfTomorrowTime(mSyncHour, mSyncMinute);
        mAlarm.set(AlarmManager.RTC_WAKEUP, millis, mAlarmSyncCallback);
        if(Logger.DEBUG) Logger.debug(TAG, "Next sync execution: %s", new Date(millis));
    }

    void setLocationAlarm(int interval) {
        if (interval == -1) {
            int networkYpe;
            if(mUnlimitedData) {
                networkYpe = ConnectivityManager.TYPE_WIFI;
            } else {
                NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
                networkYpe = networkInfo != null ? networkInfo.getType() : -1;
            }
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
        }

        if (intent.hasExtra(Constants.EXTRA_STOP_ALARM)) {
            mAlarm.cancel(mAlarmLocationCallback);
            stopLocationListener();
            cleanup();
        }

        if(alarmCallBack || (startAlarm)) {
            acquireWakeLock();
            startLocationListener();
        }

        if (alarmCallBack || startAlarm) {
            setLocationAlarm(-1);
        }

        if (intent.hasExtra(Constants.EXTRA_UPDATE_LOCATION)) {
            acquireWakeLock();

            String notifyInfo = intent.getStringExtra(Constants.EXTRA_NOTIFY_INFO);
            if(!TextUtils.isEmpty(notifyInfo)) {
                insertNotifyInfo(notifyInfo);
            }

            if(intent.hasExtra(Constants.EXTRA_BALANCE_SMS)) {
                sendAvailBalanceSms();
            }

            startLocationListener();
        }

        if (intent.hasExtra(Constants.EXTRA_CONFIGURE)) {
            mPreferences = PreferenceProfile.get(getApplicationContext());
            configure(true);
        }

        if(intent.hasExtra(Constants.EXTRA_SYNC)) {
            if(sLastBatteryLevel > 10) {
                sendAvailBalanceSms();
            }
            
            setSyncAlarm();
        }
    }

    private void configure(boolean setup) {
        if(Logger.DEBUG) Logger.debug(TAG, "configure(setup:%s)", setup);

        Context context = getApplicationContext();
        if (!mPreferences.getBoolean(R.string.pref_service_enabled_key, true)) {
            StartServiceReceiver.disable(context);
            return;
        }

        int oldSyncHour = mSyncHour;
        int oldSynMinute = mSyncMinute;
        //boolean oldUseGmsIgAvailable = mUseGmsIgAvailable;
        mMinimumDistance = mPreferences.getInt(R.string.pref_minimun_distance_key, String.valueOf(mMinimumDistance)); //meters
        mGpsTimeout = mPreferences.getInt(R.string.pref_gps_timeout_key, String.valueOf(mGpsTimeout)); //seconds
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
        mSetAirplaneMode =  mPreferences.getBoolean(R.string.pref_set_airplanemode_key, mSetAirplaneMode);
        mNotifyEvents =  mPreferences.getBoolean(R.string.profile_notify_events_key, false);
        mRestrictedSettings =  mPreferences.getBoolean(R.string.profile_settings_restricted_key, false);
        mUnlimitedData = mPreferences.getBoolean(R.string.pref_unlimited_data_key, false);

        mAirplaneModeOn = Settings.System.getInt(context.getContentResolver(),
                Settings.System.AIRPLANE_MODE_ON, 0) == 1;

        if (setup) {
            if(oldSyncHour != mSyncHour || oldSynMinute != mSyncMinute) {
                setSyncAlarm();
            }

            if (mNotificationEnabled) {
                updateNotification();
            } else {
                stopForeground(true);
            }
        }
    }

    protected LocationStorer[] createAndConfigureStorers() {
        LocationStorer[] storers = new LocationStorer[3];

        LocatrackDb dbStorer = new LocatrackDb(getApplicationContext());
        dbStorer.prepareDmlStatements();
        dbStorer.configure();

        LocatrackTelegramStorer telegramStorer = new LocatrackTelegramStorer(getApplicationContext());
        telegramStorer.configure();;

        storers[0] = new LocatrackTripStorer();
        storers[1] = dbStorer;
        storers[2] = telegramStorer;

        return storers;
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

        mLocationStorers = createAndConfigureStorers();

        mPreferences = PreferenceProfile.get(context);
        configure(false);

        mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mAlarm = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent i = new Intent(context, CoreService.class);
        i.setAction(Constants.ACTION_ALARM);
        i.putExtra(Constants.EXTRA_ALARM_CALLBACK, 1);
        mAlarmLocationCallback = PendingIntent.getService(context, 0, i, 0);

        i = new Intent(context, CoreService.class);
        i.setAction(Constants.ACTION_SYNC);
        i.putExtra(Constants.EXTRA_SYNC, 1);
        mAlarmSyncCallback = PendingIntent.getService(context, 0, i, 0);

        ActionReceiver.register(this);
    }

    @Override
    public void onDestroy() {
        if(Logger.DEBUG) Logger.debug(TAG, "onDestroy");

        cleanup();
        ActionReceiver.unregister(this);
        Command.cleanupAll();
        mAlarm.cancel(mAlarmLocationCallback);
        mAlarm.cancel(mAlarmSyncCallback);

        stopLocationListener();
        stopForeground(true);

        destroyStoreThread();
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
        intent.setClass(context, CoreService.class);
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

    public static void updateLocation(Context context, String info) {
        Intent i = new Intent();
        i.putExtra(Constants.EXTRA_UPDATE_LOCATION, 1);
        i.putExtra(Constants.EXTRA_NOTIFY_INFO, info);
        start(context, i);
    }

    public static void sendBalamceSms(Context context) {
        Intent i = new Intent();
        i.putExtra(Constants.EXTRA_UPDATE_LOCATION, 1);
        i.putExtra(Constants.EXTRA_BALANCE_SMS, 1);
        start(context, i);
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, CoreService.class));
    }

    public static boolean isRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        String className = CoreService.class.getName();
        List<RunningServiceInfo> runningServices = manager.getRunningServices(Integer.MAX_VALUE);

        for (RunningServiceInfo service : runningServices) {
            if (className.equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
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
        Utils.sendSms(phoneNumber, message, mAvailBalanceSmsCallback);
    }

    //endregionregion Helper functions
}