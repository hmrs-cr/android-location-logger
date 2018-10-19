package com.hmsoft.locationlogger.common;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.SmsManager;

import com.hmsoft.locationlogger.data.LocatrackLocation;

import java.util.Calendar;
import java.util.List;

public class Utils {

    private static final boolean DEBUG = Logger.DEBUG;
    private static final String TAG = "Utils";

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

    public static void sendSms(String toNumber, String smsMessage, PendingIntent sentIntent)  {
        SmsManager smsManager = SmsManager.getDefault();
        if(smsManager == null) {
            Logger.warning(TAG, "No SmsManager found");
            return;
        }
        smsManager.sendTextMessage(toNumber, null, smsMessage, sentIntent, null);
        if(DEBUG) Logger.debug(TAG, "Send SMS to %s: %s", toNumber, smsMessage);
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

    public static void setAirplaneMode(Context context, boolean  isEnabled) {
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
}
