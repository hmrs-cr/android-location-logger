package com.hmsoft.locationlogger.data.locatrack;

import android.content.Context;
import android.text.TextUtils;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

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

        String messageUrl =  getMessageUrl(message);

        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(messageUrl);
        get.setHeader("User-Agent", "Locatrack");

        try {
            HttpResponse response = client.execute(get);
            int status = response.getStatusLine().getStatusCode();
            return (status == 200 || status == 201);
        } catch (IOException e) {
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

        return message.toString();
    }


    @Override
    public boolean storeLocation(LocatrackLocation location) {
        if(TextUtils.isEmpty(location.event) || TextUtils.isEmpty(location.extraInfo)) {
            if (Logger.DEBUG)
                Logger.debug(TAG, "internalUploadLocation UPDATE: distanceTo LUL: %f");
            return false;
        }

        if(TextUtils.isEmpty(mChatId) || TextUtils.isEmpty(mChatId)) {
            configure();
        }

        if(TextUtils.isEmpty(mChatId) || TextUtils.isEmpty(mChatId)) {
            Logger.error(TAG, "Not configured");
            return false;
        }


        String message = getEventMessage(location);
        return  sentTelegramMessage(message);
    }

    private String getEventMessage(LocatrackLocation location) {
        StringBuilder message = new StringBuilder();

        String event = TextUtils.isEmpty(location.event) ? "INFO" : location.event.toUpperCase();

        message
            .append("*").append(event).append("!").append("*\n\n")
            .append("*Location:* _[").append(location.getLatitude()).append(",").append(location.getLongitude()).append("](http://maps.google.com/maps?daddr=").append(location.getLatitude()).append(",").append(location.getLongitude()).append(")_\n")
            .append("*Time:*     _").append(location.getTime()).append("_\n")
            .append("*Battery:*  _").append(location.batteryLevel).append("_");

        if(!TextUtils.isEmpty(location.extraInfo)) {
            message.append("%\n\n_").append(location.extraInfo).append("_");
        }

        return message.toString();
    }

    @Override
    public void configure() {
        mChatId = mContext.getString(R.string.pref_telegram_chatid);
        mBotKey = mContext.getString(R.string.pref_telegram_botkey);
    }
}
