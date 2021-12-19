package com.hmsoft.locationlogger.data.locatrack;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.text.TextUtils;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.Utils;
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
    private static final long EVENT_TIME_WINDOW = 60 * 1000 * 3;

    private final Context mContext;

    private String mBotKey;
    private String mChatId;
    private DateFormat mDateFormat;
    private long mLastStartTime = 0;
    private long mLastStopTime = 0;
    private ConnectivityManager mConnectivityManager;

    public LocatrackTelegramStorer(Context context) {
        mContext = context;
        mDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a", Locale.US);
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public boolean storeLocation(LocatrackLocation location) {
        if(TextUtils.isEmpty(location.event) && TextUtils.isEmpty(location.extraInfo)) {
            if (Logger.DEBUG)
                Logger.debug(TAG, "No event to notify.");

            return true;
        }

        if(eventTooFast(location)) {
            if (Logger.DEBUG) {
                Logger.debug(TAG, "Event '" + location.event + "' too fast.");
            }
            return true;
        }

        if (Logger.DEBUG) {
            Logger.debug(TAG, "Notifing event " + location.event);
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

        String chatId = LocatrackLocation.EVENT_INFO.equals(location.event) && location.replyToId != null ? location.replyToId : mChatId;
        String replyMessageId = LocatrackLocation.EVENT_INFO.equals(location.event) ? location.replyToMessageId : null;
        long messageId = TelegramHelper.sendTelegramMessage(mBotKey, chatId, replyMessageId, message);

        location.replyToId = null;
        location.replyToMessageId = null;

        boolean success = messageId > 0;
        if(!success) {
            sendBackupMessage(message);
        }

        return success;
    }

    private void sendBackupMessage(String message) {
        if(Logger.DEBUG) {
            Logger.debug(TAG, "Sending sms event notification.");
        }

        String number = PreferenceProfile.get(mContext).getString(R.string.pref_notification_number_key,
                mContext.getString(R.string.pref_notification_number_default));
        if(!TextUtils.isEmpty(number)) {
            message = message.replace("*", "").replace("_", "").replace("%", "");
            Utils.sendSms(number, message, null);
        } else if(Logger.DEBUG) {
            Logger.debug(TAG, "No notification number.");
        }
    }

    private boolean eventTooFast(LocatrackLocation location) {

        if(!TextUtils.isEmpty(location.extraInfo)) {
            return false;
        }

        if(!TextUtils.isEmpty(location.event)) {
            if(location.event.contains(LocatrackLocation.EVENT_START)) {
                if(location.getTime() - mLastStartTime < EVENT_TIME_WINDOW) {
                    return true;
                }
                mLastStopTime = 0;
                mLastStartTime = location.getTime();
            } else if (location.event.contains(LocatrackLocation.EVENT_STOP)) {
                if(location.getTime() - mLastStopTime < EVENT_TIME_WINDOW) {
                    return true;
                }
                mLastStartTime = 0;
                mLastStopTime = location.getTime();
            }
        }

        return false;
    }

    private String getEventMessage(LocatrackLocation location, String netWorkType) {
        StringBuilder message = new StringBuilder(128);

        int batteryLevel = location.batteryLevel;
        if(batteryLevel > 100) {
            batteryLevel -= 100;
        }

        String event = location.event = TextUtils.isEmpty(location.event) ? LocatrackLocation.EVENT_INFO : location.event;
        switch (event) {
            case LocatrackLocation.EVENT_INFO:
                event = "INFO";
                break;
            case LocatrackLocation.EVENT_START:
                event = mContext.getString(R.string.str_start);
                break;
            case LocatrackLocation.EVENT_STOP:
                event = mContext.getString(R.string.str_stop);
                break;
            case LocatrackLocation.EVENT_LOW_BATTERY:
                event = mContext.getString(R.string.str_low_battery);
                break;
            case LocatrackLocation.EVENT_RESTART:
                event = mContext.getString(R.string.str_restart);
                break;
            case LocatrackLocation.EVENT_RESTOP:
                event = mContext.getString(R.string.str_restop);
                break;
        }

        message.append("*").append(event).append("!").append("*\n\n");

        if(!TextUtils.isEmpty(location.extraInfo)) {
            message.append("_").append(TelegramHelper.escapeMarkdown(location.extraInfo.trim())).append("_").append("\n\n");
        }

        message
            .append("*").append(mContext.getString(R.string.str_location)).append("*\t[").append(getAddressLabel(location)).append("](https://www.google.com/maps/search/?api=1&query=").append(location.getLatitude()).append(",").append(location.getLongitude()).append(")\n")
            .append("*").append(mContext.getString(R.string.str_time)).append("*\t").append(mDateFormat.format(new Date(location.getTime()))).append("\n")
            .append("*").append(mContext.getString(R.string.str_accuracy)).append("*\t").append(Math.round(location.getAccuracy() * 100.0) / 100.0).append("m ").append(location.getProvider().charAt(0)).append(netWorkType.charAt(0)).append("\n")
            .append("*").append(mContext.getString(R.string.str_battery)).append("*\t").append(batteryLevel).append("%");

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
    public LocationStorer configure() {
        mChatId = PreferenceProfile.get(mContext).getString(R.string.pref_telegram_chatid_key, mContext.getString(R.string.pref_telegram_chatid_default));

        mBotKey = PreferenceProfile.get(mContext).getString(R.string.pref_telegram_botkey_key,
                mContext.getString(R.string.pref_telegram_botkey_default));

        return this;
    }
}
