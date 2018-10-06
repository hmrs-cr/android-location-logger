package com.hmsoft.locationlogger.common;

import android.text.TextUtils;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class TelegramHandler {

    private static final String TAG = "TelegramHandler";

    private static final String TELEGRAM_API_PROTOCOL = "https://";
    private static final String TELEGRAM_API_HOST = "api.telegram.org";
    private static final String TELEGRAM_API_URL = TELEGRAM_API_PROTOCOL + TELEGRAM_API_HOST;
    private static final String TELEGRAM_API_BOT_URL = TELEGRAM_API_URL + "/bot";


    private static int updatesOffset = 0;

    public interface UpdateCallback {
        void onTelegramUpdateReceived(String chatId, String text);
    }


    public static boolean sendTelegramMessage(String botKey, String chatId, String message) {
        try {

            if (Logger.DEBUG) {
                message = "* ***** DEBUG ***** *\n" + message;
            }

            String messageUrl = getMessageUrl(botKey, chatId, message);

            if (Logger.DEBUG) {
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

    private static Thread updaterThread = null;

    private static class Updater implements Runnable {

        private int mCount;
        private String mBotKey;
        private UpdateCallback mUpdateCallback;

        public Updater(String botKey, UpdateCallback updateCallback, int count) {
            mBotKey = botKey;
            mUpdateCallback = updateCallback;
            mCount = count < 0 ? count * -1 : count;
        }

        @Override
        public void run() {
            try {
                while (mCount-- > 0) {
                    boolean increaseUpdateOffset = false;
                    try {

                        StringBuilder updatesUrl = getTelegramApiUrl(mBotKey, "getUpdates");

                        updatesUrl
                                .append("?timeout=60")
                                .append("&limit=10")
                                .append("&allowed_updates=channel_post");

                        if (updatesOffset > 0) {
                            updatesUrl.append("&offset=").append(updatesOffset);
                        }

                        String url = updatesUrl.toString();

                        if (Logger.DEBUG)
                            Logger.debug(TAG, url + " - ThreadId:" + Thread.currentThread().getId());
                        HttpResponse response = httpGet(url);
                        int status = response.getStatusLine().getStatusCode();

                        if (status == 200 || status == 201) {
                            if (mUpdateCallback != null) {

                                String responseText = getResponseText(response);

                                if (Logger.DEBUG) {
                                    Logger.debug(TAG, responseText + " - Count:" + mCount);
                                }

                                JSONObject jsonObject = new JSONObject(responseText);

                                if (jsonObject.getBoolean("ok")) {

                                    JSONArray results = jsonObject.getJSONArray("result");
                                    for (int c = 0; c < results.length(); c++) {

                                        JSONObject result = results.getJSONObject(c);

                                        increaseUpdateOffset = true;
                                        updatesOffset = result.getInt("update_id");
                                        JSONObject message = result.optJSONObject("channel_post");
                                        if (message == null) {
                                            message = result.optJSONObject("message");
                                        }
                                        if (message != null) {
                                            String text = message.optString("text");
                                            if(TextUtils.isEmpty(text)) {
                                                JSONObject document = message.optJSONObject("document");
                                                if(document != null) {
                                                    text = String.format("document|%s|%s",
                                                            document.getString("file_name"),
                                                            document.getString("file_id"));
                                                }
                                            }
                                            if (!TextUtils.isEmpty(text)) {
                                                String chatId = message.getJSONObject("chat").getString("id");
                                                mUpdateCallback.onTelegramUpdateReceived(chatId, text);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    } finally {
                        if (increaseUpdateOffset) {
                            updatesOffset++;
                        }
                    }
                }
            } finally {
                updaterThread = null;
                if (Logger.DEBUG)
                    Logger.debug(TAG, "Ending telegram update thread. Offset: %d - ThreadId:%d", updatesOffset, Thread.currentThread().getId());
            }
        }
    }

    private static String getResponseText(HttpResponse response) {
        String inputLine;
        HttpEntity entity = response.getEntity();
        StringBuilder sb = new StringBuilder((int) entity.getContentLength());
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(entity.getContent()));
            while ((inputLine = br.readLine()) != null) {
                sb.append(inputLine).append("\n");
            }
            br.close();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return sb.toString();
    }

    public static String getFileDownloadUrl(String botKey, String fileId) {
        StringBuilder updatesUrl = getTelegramApiUrl(botKey, "getFile");
        updatesUrl.append("?file_id=").append(fileId);

        HttpResponse response = null;
        try {
            response = httpGet(updatesUrl.toString());
            int status = response.getStatusLine().getStatusCode();
            if (status == 200 || status == 201) {
                String responseText = getResponseText(response);
                JSONObject jresponse = new JSONObject(responseText);
                JSONObject result = jresponse.optJSONObject("result");
                if(result != null) {
                    String filePath = result.optString("file_path");
                    if(!TextUtils.isEmpty(filePath)) {
                        return String.format("%s/file/bot%s/%s", TELEGRAM_API_URL, botKey, filePath);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static synchronized void getUpdates(String botKey, UpdateCallback updateCallback,
                                               int count) {

        if (updaterThread == null) {

            updaterThread = new Thread(new Updater(botKey, updateCallback, count));

            if (Logger.DEBUG)
                Logger.debug(TAG, "Starting telegram update thread. - ThreadId:%d", updaterThread.getId());

            updaterThread.start();
        } else {
            if (Logger.DEBUG)
                Logger.debug(TAG, "Updater still running. - ThreadId:%d", updaterThread.getId());
        }

    }
}
