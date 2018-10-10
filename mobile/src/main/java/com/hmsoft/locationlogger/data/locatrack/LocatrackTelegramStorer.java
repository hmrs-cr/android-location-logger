package com.hmsoft.locationlogger.data.locatrack;

import android.content.Context;
import android.text.TextUtils;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.TelegramHelper;
import com.hmsoft.locationlogger.data.Geocoder;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LocatrackTelegramStorer extends LocationStorer {

    private static final String TAG = "LocatrackTelegramStorer";


    private final Context mContext;

    private String mBotKey;
    private String mChatId;
    private DateFormat mDateFormat;

    public LocatrackTelegramStorer(Context context) {
        mContext = context;
        mDateFormat = new SimpleDateFormat("yyyy-MM-dd KK:mm:ss a");
    }


    @Override
    public boolean storeLocation(LocatrackLocation location) {
        String message = getEventMessage(location);
        return TelegramHelper.sendTelegramMessage(mBotKey, mChatId, message);
    }

    private String getEventMessage(LocatrackLocation location) {
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
            .append("*Location:*\t[").append(getAddressLabel(location)).append("](http://maps.google.com/maps?q=").append(location.getLatitude()).append(",").append(location.getLongitude()).append(")\n")
            .append("*Accuracy:*\t").append(Math.round(location.getAccuracy() * 100.0) / 100.0).append("m\n")
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
        mChatId = mContext.getString(R.string.pref_telegram_chatid);
        mBotKey = mContext.getString(R.string.pref_telegram_botkey);
    }
}
