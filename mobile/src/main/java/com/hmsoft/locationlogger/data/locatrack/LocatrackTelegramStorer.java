package com.hmsoft.locationlogger.data.locatrack;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;
import com.hmsoft.locationlogger.data.Geocoder;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;
import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LocatrackTelegramStorer extends LocationStorer {

    private static final String TAG = "LocatrackTelegramStorer";
    private static final boolean DEBUG = Logger.DEBUG;


    private final Context mContext;

    private String mBotKey;
    private String mChatId;
    private DateFormat mDateFormat;
    private ConnectivityManager mConnectivityManager;

    public LocatrackTelegramStorer(Context context) {
        mContext = context;
        mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss a", Locale.US);
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public boolean storeLocation(LocatrackLocation location) {
        if(TextUtils.isEmpty(location.event) && TextUtils.isEmpty(location.extraInfo)) {
            if (Logger.DEBUG)
                Logger.debug(TAG, "No event to notify.");

            return true;
        }

        String netTypeName = "-";

        int waitCount = 10;
        while(waitCount-- > 0) {
            NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
            if((networkInfo != null && networkInfo.isConnected())) {
                netTypeName = networkInfo.getTypeName();
                if(DEBUG) Logger.debug(TAG, "Connected to network:" + networkInfo.getTypeName());
                break;
            }
            if(DEBUG) Logger.debug(TAG, "Not connected waiting for connection... " + waitCount);
            TaskExecutor.sleep(5);
        }

        String message = getEventMessage(location, netTypeName);
        long messageId = TelegramHelper.sendTelegramMessage(mBotKey, mChatId, message);
        return messageId > 0;
    }

    private String getEventMessage(LocatrackLocation location, String netWorkType) {
        StringBuilder message = new StringBuilder(128);

        String event = TextUtils.isEmpty(location.event) ? "INFO" : location.event.toUpperCase();
        int batteryLevel = location.batteryLevel;
        if(batteryLevel > 100) {
            batteryLevel -= 100;
        }


        message.append("*").append(event).append("!").append("*\n\n");

        if(!TextUtils.isEmpty(location.extraInfo)) {
            message.append("_").append(location.extraInfo.trim()).append("_").append("\n\n");
        }

        message
            .append("*Location:*\t[").append(getAddressLabel(location)).append("](https://www.google.com/maps/search/?api=1&query=").append(location.getLatitude()).append(",").append(location.getLongitude()).append(")\n")
            .append("*Accuracy:*\t").append(Math.round(location.getAccuracy() * 100.0) / 100.0).append("m ").append(location.getProvider().charAt(0)).append(netWorkType.charAt(0)).append("\n")
            .append("*Time:*\t").append(mDateFormat.format(new Date(location.getTime()))).append("\n")
            .append("*Battery:*\t").append(batteryLevel).append("%");

        return message.toString();
    }

    private String getAddressLabel(LocatrackLocation location) {
        String address = Geocoder.getFromCache(location);
        if(TextUtils.isEmpty(address)) {
            address = Geocoder.getFromRemote(mContext, location);
            if (!TextUtils.isEmpty(address)) {
                Geocoder.addToCache(location, address);
            }
        }
        if(TextUtils.isEmpty(address)) {
            address = location.getLatitude() + "," + location.getLongitude();
        }

        return address;
    }

    public boolean isConfigured() {
        return !TextUtils.isEmpty(mChatId) && !TextUtils.isEmpty(mChatId);
    }

    @Override
    public void configure() {
        mChatId = PreferenceProfile.get(mContext).getString(R.string.pref_telegram_chatid_key, mContext.getString(R.string.pref_telegram_chatid_default));

        mBotKey = PreferenceProfile.get(mContext).getString(R.string.pref_telegram_botkey_key,
                mContext.getString(R.string.pref_telegram_botkey_default));
    }
}
