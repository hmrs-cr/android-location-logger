package com.hmsoft.locationlogger.data.locatrack;

import android.content.Context;
import android.text.TextUtils;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.data.Geocoder;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LocatrackTelegramStorer extends LocationStorer {

    private static final String TAG = "LocatrackTelegramStorer";

    private final String TELEGRAM_API_PROTOCOL = "https://";
    private final String TELEGRAM_API_HOST = "api.telegram.org";
    private final String TELEGRAM_API_URL = TELEGRAM_API_PROTOCOL + TELEGRAM_API_HOST;
    private final String TELEGRAM_API_BOT_URL = TELEGRAM_API_URL + "/bot";
    private final Context mContext;

    private String mBotKey;
    private String mChatId;

    public LocatrackTelegramStorer(Context context) {
        mContext = context;
    }

    public boolean sentTelegramMessage(String message) {
        try {
            String messageUrl =  getMessageUrl(message);

            HttpClient client = new DefaultHttpClient();
            HttpGet get = new HttpGet(messageUrl);

            HttpResponse response = client.execute(get);
            int status = response.getStatusLine().getStatusCode();
            return (status == 200 || status == 201);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private String getMessageUrl(String message) {
        StringBuilder messageUrl = new StringBuilder();

        try {
            messageUrl
                    .append(TELEGRAM_API_BOT_URL)
                    .append(mBotKey).append("/sendMessage?")
                    .append("chat_id=").append(mChatId).append("&")
                    .append("parse_mode=Markdown&")
                    .append("text=").append(URLEncoder.encode(message, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return messageUrl.toString();
    }


    @Override
    public boolean storeLocation(LocatrackLocation location) {
        String message = getEventMessage(location);
        return  sentTelegramMessage(message);
    }

    private String getEventMessage(LocatrackLocation location) {
        StringBuilder message = new StringBuilder();

        String event = TextUtils.isEmpty(location.event) ? "INFO" : location.event.toUpperCase();
        int batteryLevel = location.batteryLevel;
        if(batteryLevel > 100) {
            batteryLevel -= 100;
        }




        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd KK:mm:ss a");

        message
            .append("*").append(event).append("!").append("*\n\n")
            .append("*Location:* [").append(getAddressLabel(location)).append("](http://maps.google.com/maps?daddr=").append(location.getLatitude()).append(",").append(location.getLongitude()).append(")\n")
            .append("*Time:*      ").append(dateFormat.format(new Date(location.getTime()))).append("\n")
            .append("*Battery:*   ").append(batteryLevel).append("%");

        if(!TextUtils.isEmpty(location.extraInfo)) {
            message.append("\n\n_").append(location.extraInfo.trim()).append("_");
        }

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
