package com.hmsoft.locationlogger.common;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.SmsManager;

import com.hmsoft.locationlogger.LocationLoggerApp;
import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.data.LocatrackLocation;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class Utils {

    private static final boolean DEBUG = Logger.DEBUG;
    private static final String TAG = "Utils";

    public static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd HH:mm", Locale.US);

    private Utils(){}

    public static long getMillisOfTomorrowTime(int hour, int minute) {
        long curMillis = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(curMillis);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);

        long millis = calendar.getTimeInMillis();
        if(curMillis >= millis) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            millis = calendar.getTimeInMillis();
        }
        return millis;
    }

    public static boolean sendSms(String toNumber, String smsMessage, PendingIntent sentIntent)  {
        SmsManager smsManager = LocationLoggerApp.getContext().getSystemService(SmsManager.class);
        if(smsManager == null) {
            smsManager = SmsManager.getDefault();
            if(smsManager == null) {
                Logger.warning(TAG, "No SmsManager found");
                return false;
            }
        }
        smsManager.sendTextMessage(toNumber, null, smsMessage, sentIntent, null);
        if(DEBUG) Logger.debug(TAG, "Send SMS to " + toNumber + ": " + smsMessage);
        return true;
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

    public static boolean hasPermission(String permission) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return LocationLoggerApp.getContext().checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    public static String[] getAllNeededPermissions() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new String[]{
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? Manifest.permission.BLUETOOTH_CONNECT : null,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? Manifest.permission.BLUETOOTH_SCAN : null,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.CALL_PHONE
            };
        }
        return new String[0];
    }

    public static boolean hasAllPermissions() {
        if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }

        String[] permissions = getAllNeededPermissions();

        for(String permission : permissions) {
            if(permission != null && !hasPermission(permission)) {
                if(DEBUG) {
                    Logger.debug(TAG, "Missing permission: " + permission);
                }
                return false;
            }
        }

        return true;
    }

    public static void showSettingActivity(Context context) {
        final Intent i = new Intent();
        i.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        i.addCategory(Intent.CATEGORY_DEFAULT);
        i.setData(Uri.parse("package:" + context.getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(i);
    }

    public static void requestAllPermissions(Activity activity) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = getAllNeededPermissions();
            activity.requestPermissions(permissions,0);
        }
    }

    /*public static void setAirplaneMode(Context context, boolean  isEnabled) {
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
    }*/

    public static boolean isFromGps(Location location) {
        return LocationManager.GPS_PROVIDER.equals(location.getProvider()) ||
                location.hasAltitude() || location.hasBearing() || location.hasSpeed();
    }

    public static boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }

    public static boolean isBetterLocation(Location location, Location currentBestLocation,
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

    public static boolean isInternetAvailable() {
        try {
            final InetAddress address = InetAddress.getByName("www.google.com");
            return !address.equals("");
        } catch (UnknownHostException e) {
            Logger.error(TAG, e.getMessage());
        }
        return false;
    }

    public static String getGeneralInfo(Context context) {

        StringBuilder generalInfo = new StringBuilder();

        ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        String netName = networkInfo != null ? networkInfo.getTypeName() : "None";
        boolean connected = networkInfo != null && networkInfo.isConnected();

        Intent batteryIntent = getBatteryChangedIntent();
        double temp = getBatteryTemp(batteryIntent);
        int level = getBatteryLevel(batteryIntent);
        boolean charging = false;
        if (level > 100) {
            charging = true;
            level -= 100;
        }

        LocatrackLocation lastLocation = LocatrackLocation.getLastLocation();
        StringBuilder locationInfo = new StringBuilder();
        if (lastLocation != null) {
            locationInfo
                    .append("[")
                    .append(lastLocation.getAddressLabel())
                    .append("](https://www.google.com/maps/search/?api=1&query=")
                    .append(lastLocation.getLatitude())
                    .append(",")
                    .append(lastLocation.getLongitude())
                    .append(") ")
                    .append(lastLocation.getTimeString())
                    .append(" (")
                    .append(lastLocation.getAccuracyString())
                    .append(")");
        } else {
            locationInfo.append("Unknown");
        }

        generalInfo.append("\nNetwork: ").append(netName).append(" - Connected:").append(connected).append("\n")
                .append("Internet: ").append(connected && Utils.isInternetAvailable()).append("\n")
                .append("Battery: ").append(level).append("%").append(charging ? " (charging)" : "").append("\n")
                .append("Temp: ").append(temp).append("\n")
                .append("Loc: ").append(locationInfo).append("\n")
                .append("App Version: ").append(Constants.VERSION_STRING).append("\n")
                .append("Android Version: ").append(android.os.Build.MODEL).append(" ").append(android.os.Build.VERSION.RELEASE).append("\n");

        return generalInfo.toString();
    }

    public static void playAudio(Uri fileUri, boolean loudestPossible) {

        if(DEBUG) {
            Logger.debug(TAG, "Playing audio: " + fileUri);
        }

        AudioManager _audioManager = null;
        int _originalVolume = 0;
        if(loudestPossible) {
            _audioManager = (AudioManager) LocationLoggerApp.getContext().getSystemService(Context.AUDIO_SERVICE);
            _originalVolume = _audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            _audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, _audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0);

            if(DEBUG) {
                Logger.debug(TAG, "Original volume: " + _originalVolume + ", Max Volume: " +
                        _audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            }
        }

        final AudioManager audioManager = _audioManager;
        final int originalVolume = _originalVolume;

        MediaPlayer mp = new MediaPlayer();
        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        mp.setAudioAttributes(audioAttributes);
        try {
            mp.setDataSource(LocationLoggerApp.getContext(), fileUri);
            mp.prepare();
            mp.start();
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
            {
                @Override
                public void onCompletion(MediaPlayer mp)
                {
                    if(DEBUG) {
                        Logger.debug(TAG, "MediaPlayer.onCompletion v" + originalVolume);
                    }
                    if(audioManager != null) {
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                                originalVolume, 0);
                    }
                }
            });
        } catch (IOException e) {
            if(audioManager != null) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
                        originalVolume, 0);
            }
            Logger.error(TAG, "Failed to play audio", e.getClass().getName() + ": " + e.getMessage());
        }
    }


    private static final IntentFilter batteryIntentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private static int sLastBatteryLevel = -1;

    public static int resetBatteryLevel() {
        int lastBatteryLevel = sLastBatteryLevel;
        sLastBatteryLevel = -1;
        return lastBatteryLevel;
    }

    public static int getBatteryLevel(Intent intent) {
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        boolean isPlugged = (plugged != 0);
        sLastBatteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
        if (isPlugged) sLastBatteryLevel += 100;

        return sLastBatteryLevel;
    }

    public static double getBatteryTemp(Intent intent) {
        double temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10F;
        return  temp;
    }

    public static int getBatteryLevel() {
        return getBatteryLevel(false);
    }

    public  static Intent getBatteryChangedIntent() {
        Intent intent = LocationLoggerApp.getContext().registerReceiver(null,
                batteryIntentFilter);
        return  intent;
    }

    public static int getBatteryLevel(boolean fresh) {
        if (fresh || sLastBatteryLevel < 0) {
            sLastBatteryLevel = getBatteryLevel(getBatteryChangedIntent());

            if(DEBUG) {
                Logger.debug(TAG, "Getting battery level: " + sLastBatteryLevel);
            }
        }

        return sLastBatteryLevel;
    }

    public static void turnBluetoothOn() {
        try {
            if (LocationLoggerApp.getContext().checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (!bluetoothAdapter.isEnabled()) {
                    if (bluetoothAdapter.enable()) {
                        if (DEBUG) Logger.debug(TAG, "Bluetooth enabled successfully.");
                    } else {
                        Logger.warning(TAG, "Failed to enable bluetooth");
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    public static void turnBluetoothOff() {
        try {
            if (LocationLoggerApp.getContext().checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
                BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                if (bluetoothAdapter.isEnabled()) {
                    if (bluetoothAdapter.disable()) {
                        if (DEBUG) Logger.debug(TAG, "Bluetooth disabled successfully.");
                    } else {
                        Logger.warning(TAG, "Failed to disable bluetooth");
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }
}
