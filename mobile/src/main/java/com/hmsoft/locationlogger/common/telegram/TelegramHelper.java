package com.hmsoft.locationlogger.common.telegram;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.hmsoft.locationlogger.LocationLoggerApp;
import com.hmsoft.locationlogger.common.HttpUtils;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TaskExecutor;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

public class TelegramHelper {

    private static final String TAG = "TelegramHelper";

    public static final String VOICE_PREFIX = "voice_";
    private static final String TELEGRAM_API_PROTOCOL = "https://";
    private static final String TELEGRAM_API_HOST = "api.telegram.org";
    private static final String TELEGRAM_API_URL = TELEGRAM_API_PROTOCOL + TELEGRAM_API_HOST;
    private static final String TELEGRAM_API_BOT_URL = TELEGRAM_API_URL + "/bot";


    private static long updatesOffset = 0;

    public static String escapeMarkdown(String plainText) {
        if (plainText != null) {
            plainText = plainText
                    .replace("_", "\\_");
        }

        return plainText;
    }

    public interface UpdateCallback {
        void onTelegramUpdateReceived(String chatId, String messageId, String text, final String userName, final String fillName);
    }

    public static long sendTelegramMessage(String botKey, String chatId, String message) {
        return sendTelegramMessage(botKey, chatId, null, message);
    }

    public static void sendTelegramMessageAsync(String botKey, String chatId, String message) {
        sendTelegramMessageAsync(botKey, chatId, null, message);
    }

    public static void sendTelegramDocuments(final String botKey,
                                             final String chatId,
                                             final String replyId,
                                             final File[] documentFiles) {

        for (File doc : documentFiles) {
            sendTelegramDocument(botKey, chatId, replyId, doc, null);
        }
    }



    public static void sendTelegramDocumentsAsync(final String botKey,
                                                  final String chatId,
                                                  final String replyId,
                                                  final File[] documentFiles) {
        TaskExecutor.executeOnNewThread(new Runnable() {
            @Override
            public void run() {
                for(File doc  :documentFiles) {
                    sendTelegramDocument(botKey, chatId, replyId, doc, null);
                }
            }
        });
    }

    public static String getBotName(String botKey) {
        try {
            JSONObject response = HttpUtils.httpGetResponseJson(getTelegramApiUrl(botKey, "getMe").toString());
            if(response.optBoolean("ok")) {
                response = response.getJSONObject("result");
                return response.optString("first_name");
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void sendTelegramDocument(final String botKey,
                                            final String chatId,
                                            final String replyId,
                                            final File documentFile,
                                            final String caption) {

        sendTelegramDocument(
                botKey,
                "sendDocument",
                "document",
                chatId,
                replyId,
                documentFile,
                caption,
                null,
                null,
                -1);
    }

    public static void sendTelegramAudio(final String botKey,
                                         final String chatId,
                                         final String replyId,
                                         final File documentFile,
                                         final String caption,
                                         final String performer,
                                         long len) {
        sendTelegramDocument(
                botKey,
                "sendAudio",
                "audio",
                chatId,
                replyId,
                documentFile,
                null,
                caption,
                performer,
                len);
    }

    private  static void sendTelegramDocument(
         final String botKey,
         final String method,
         final String documentType,
         final String chatId,
         final String replyId,
         final File documentFile,
         final String caption,
         final String fileName,
         final String performer,
         final long len) {

        StringBuilder messageUrl = getTelegramApiUrl(botKey, method);

        if (Logger.DEBUG) {
            Logger.debug(TAG, "Sending Telegram document: %s", documentFile.getAbsolutePath());
        }

        int retryCount = 3;
        while (retryCount-- > 0) {
            try {
                HttpUtils.MultipartUtility multipartUtility = new HttpUtils.MultipartUtility(messageUrl.toString()).addFormField("chat_id", chatId)
                        .addFilePart(documentType, documentFile, fileName);

                if (!TextUtils.isEmpty(replyId)) {
                    multipartUtility.addFormField("reply_to_message_id", replyId);
                }

                if (!TextUtils.isEmpty(caption)) {
                    multipartUtility.addFormField("caption", caption);
                }

                if (!TextUtils.isEmpty(performer)) {
                    multipartUtility.addFormField("performer", performer);
                }

                if (len > 0) {
                    multipartUtility.addFormField("duration", len + "");
                }

                String response =  multipartUtility.finish();
                Logger.debug(TAG, response);
                return;
            } catch (IOException e) {
                Logger.error(TAG, e.getMessage() + " - Retry: " + retryCount);
                TaskExecutor.sleep(1);
            }
        }
    }

    public static void sendTelegramMessageAsync(final String botKey,
                                                final String chatId,
                                                final String replyId,
                                                final String message) {
        TaskExecutor.executeOnNewThread(new Runnable() {
            @Override
            public void run() {
                sendTelegramMessage(botKey, chatId, replyId, message);
            }
        });

    }

    private static long mid = 0;

    public static long sendTelegramMessage(String botKey, String chatId, String replyId,
                                           String message) {

        int retryCount = 3;
        while (retryCount-- > 0) {
            try {
                String messageUrl = getMessageUrl(botKey, chatId, replyId, message);

                if (Logger.DEBUG) {
                    Logger.debug(TAG, "Sending Telegram message: %s", message.replace("%", ""));
                    //Logger.debug(TAG, "Url length: %d", messageUrl.length());
                }

                int status = HttpUtils.httpGet(messageUrl);
                return status == 200 ?  ++mid : 0;

            } catch (Exception e) {
                Logger.warning(TAG, "Retry: " + retryCount, e);
                TaskExecutor.sleep(1);
            }
        }
        return 0;
    }

    private static StringBuilder getTelegramApiUrl(String botKey, String method) {
        StringBuilder builderUrl = new StringBuilder(256);

        builderUrl
                .append(TELEGRAM_API_BOT_URL)
                .append(botKey).append("/")
                .append(method);

        return builderUrl;
    }

    private static String getMessageUrl(String botKey, String chatId, String replyId, String message) {
        StringBuilder messageUrl = getTelegramApiUrl(botKey, "sendMessage");

        if (Logger.DEBUG) {
            message = message + "\n\n*----- DEBUG -----*";
        }

        try {
            messageUrl
                    .append("?chat_id=").append(chatId).append("&")
                    .append("parse_mode=Markdown&")
                    .append("disable_web_page_preview=true&")
                    .append("text=").append(URLEncoder.encode(message, "UTF-8"));

            if (!TextUtils.isEmpty(replyId)) {
                messageUrl.append("&reply_to_message_id=").append(replyId);
            }
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
            final String OFFSET_PREF_KEY = "telegram_update_offset";
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(LocationLoggerApp.getContext());
            if(updatesOffset == 0) {
                updatesOffset = preferences.getLong(OFFSET_PREF_KEY,0);
            }

            try {
                while (mCount-- > 0) {
                    try {

                        StringBuilder updatesUrl = getTelegramApiUrl(mBotKey, "getUpdates");

                        updatesUrl
                                .append("?timeout=60")
                                .append("&limit=10");

                        if (updatesOffset > 0) {
                            updatesUrl.append("&offset=").append(updatesOffset);
                        }

                        String url = updatesUrl.toString();

                        if (Logger.DEBUG)
                            Logger.debug(TAG, url + " - ThreadId:" + Thread.currentThread().getId());


                        if (mUpdateCallback != null) {

                            String responseText = HttpUtils.httpGetResponseText(url);

                            if (Logger.DEBUG) {
                                Logger.debug(TAG, "Count:" + mCount);
                            }

                            JSONObject jsonObject = new JSONObject(responseText);

                            if (jsonObject.getBoolean("ok")) {

                                JSONArray results = jsonObject.getJSONArray("result");
                                for (int c = 0; c < results.length(); c++) {

                                    JSONObject result = results.getJSONObject(c);

                                    updatesOffset = result.getInt("update_id") + 1;
                                    JSONObject message = result.optJSONObject("channel_post");
                                    if (message == null) {
                                        message = result.optJSONObject("message");
                                    }
                                    if (message != null) {
                                        String text = message.optString("text");
                                        if (TextUtils.isEmpty(text)) {
                                            JSONObject document = message.optJSONObject("document");
                                            if(document == null) {
                                                document = message.optJSONObject("voice");
                                                if(document != null) {
                                                    document.put("file_name", VOICE_PREFIX);
                                                }
                                            }
                                            if (document != null) {
                                                text = String.format("document %s|%s",
                                                        document.optString("file_name"),
                                                        document.getString("file_id"));
                                            }
                                        }
                                        if (!TextUtils.isEmpty(text)) {
                                            JSONObject chat = message.getJSONObject("chat");
                                            String chatId = chat.getString("id");
                                            String userName = chat.optString("username");
                                            String fullName = chat.optString("first_name") + " " + chat.optString("last_name");
                                            String messageId = message.optString("message_id");
                                            mUpdateCallback.onTelegramUpdateReceived(chatId, messageId, text, userName, fullName);
                                        }
                                    }
                                }
                            }
                        }

                    } catch (Exception e) {
                        Logger.error(TAG, e.getMessage());
                    }
                }
            } finally {
                updaterThread = null;
                preferences.edit().putLong(OFFSET_PREF_KEY, updatesOffset).commit();
                if (Logger.DEBUG)
                    Logger.debug(TAG, "Ending telegram update thread. Offset: %d - ThreadId:%d", updatesOffset, Thread.currentThread().getId());
            }
        }
    }

    public static String getFileDownloadUrl(String botKey, String fileId) {
        StringBuilder updatesUrl = getTelegramApiUrl(botKey, "getFile");
        updatesUrl.append("?file_id=").append(fileId);

        try {
            String responseText = HttpUtils.httpGetResponseText(updatesUrl.toString());
            JSONObject jresponse = new JSONObject(responseText);
            JSONObject result = jresponse.optJSONObject("result");
            if (result != null) {
                String filePath = result.optString("file_path");
                if (!TextUtils.isEmpty(filePath)) {
                    return String.format("%s/file/bot%s/%s", TELEGRAM_API_URL, botKey, filePath);
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
