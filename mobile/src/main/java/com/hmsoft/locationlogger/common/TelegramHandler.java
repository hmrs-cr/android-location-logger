package com.hmsoft.locationlogger.common;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class TelegramHandler {

    private static final String TAG = "TelegramHandler";

    private static final String TELEGRAM_API_PROTOCOL = "https://";
    private static final String TELEGRAM_API_HOST = "api.telegram.org";
    private static final String TELEGRAM_API_URL = TELEGRAM_API_PROTOCOL + TELEGRAM_API_HOST;
    private static final String TELEGRAM_API_BOT_URL = TELEGRAM_API_URL + "/bot";

    public static boolean sendTelegramMessage(String botKey, String chatId, String message) {
        try {

            if(Logger.DEBUG) {
                message = "* ***** DEBUG ***** *\n" + message;
            }

            String messageUrl =  getMessageUrl(botKey, chatId, message);

            if(Logger.DEBUG) {
                Logger.debug(TAG, "Sending Telegram message: %s", message.replace("%", ""));
                Logger.debug(TAG, "Url length: %d", messageUrl.length());
            }

            HttpResponse response = httpGet(messageUrl);
            int status = response.getStatusLine().getStatusCode();
            return (status == 200 || status == 201);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static HttpResponse httpGet(String url) throws IOException {
        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(url);
        return client.execute(get);
    }

    private static StringBuilder getTelegramApiUrl(String botKey, String method) {
        StringBuilder builderUrl = new StringBuilder(256);

        builderUrl
                .append(TELEGRAM_API_BOT_URL)
                .append(botKey).append("/")
                .append(method);

        return builderUrl;
    }


    private static String getMessageUrl(String botKey, String chatId, String message) {
        StringBuilder messageUrl = getTelegramApiUrl(botKey, "sendMessage");

        try {
            messageUrl
                    .append("?chat_id=").append(chatId).append("&")
                    .append("parse_mode=Markdown&")
                    .append("disable_web_page_preview=true&")
                    .append("text=").append(URLEncoder.encode(message, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return messageUrl.toString();
    }
}
