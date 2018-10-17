package com.hmsoft.locationlogger.common;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.hmsoft.locationlogger.LocationLoggerApp;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
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

    private static final String TELEGRAM_API_PROTOCOL = "https://";
    private static final String TELEGRAM_API_HOST = "api.telegram.org";
    private static final String TELEGRAM_API_URL = TELEGRAM_API_PROTOCOL + TELEGRAM_API_HOST;
    private static final String TELEGRAM_API_BOT_URL = TELEGRAM_API_URL + "/bot";


    private static long updatesOffset = 0;

    public interface UpdateCallback {
        void onTelegramUpdateReceived(String chatId, String messageId, String text);
    }

    public static class MultipartUtility {
        private final String boundary;
        private static final String LINE_FEED = "\r\n";
        private HttpURLConnection httpConn;
        private String charset;
        private OutputStream outputStream;
        private PrintWriter writer;

        /**
         * This constructor initializes a new HTTP POST request with content type
         * is set to multipart/form-data
         *
         * @param requestURL
         * @throws IOException
         */
        public MultipartUtility(String requestURL)
                throws IOException {
            this.charset = "UTF-8";

            // creates a unique boundary based on time stamp
            boundary = "===" + System.currentTimeMillis() + "===";
            URL url = new URL(requestURL);
            httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setUseCaches(false);
            httpConn.setDoOutput(true);    // indicates POST method
            httpConn.setDoInput(true);
            httpConn.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);
            outputStream = httpConn.getOutputStream();
            writer = new PrintWriter(new OutputStreamWriter(outputStream, charset),
                    true);
        }

        /**
         * Adds a form field to the request
         *
         * @param name  field name
         * @param value field value
         */
        public void addFormField(String name, String value) {
            writer.append("--" + boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"" + name + "\"")
                    .append(LINE_FEED);
            writer.append("Content-Type: text/plain; charset=" + charset).append(
                    LINE_FEED);
            writer.append(LINE_FEED);
            writer.append(value).append(LINE_FEED);
            writer.flush();
        }

        /**
         * Adds a upload file section to the request
         *
         * @param fieldName  name attribute in <input type="file" name="..." />
         * @param uploadFile a File to be uploaded
         * @throws IOException
         */
        public void addFilePart(String fieldName, File uploadFile)
                throws IOException {
            String fileName = uploadFile.getName();
            writer.append("--" + boundary).append(LINE_FEED);
            writer.append(
                    "Content-Disposition: form-data; name=\"" + fieldName
                            + "\"; filename=\"" + fileName + "\"")
                    .append(LINE_FEED);
            writer.append(
                    "Content-Type: "
                            + URLConnection.guessContentTypeFromName(fileName))
                    .append(LINE_FEED);
            writer.append("Content-Transfer-Encoding: binary").append(LINE_FEED);
            writer.append(LINE_FEED);
            writer.flush();

            FileInputStream inputStream = new FileInputStream(uploadFile);
            byte[] buffer = new byte[4096];
            int bytesRead = -1;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();
            inputStream.close();
            writer.append(LINE_FEED);
            writer.flush();
        }

        /**
         * Completes the request and receives response from the server.
         *
         * @return a list of Strings as response in case the server returned
         * status OK, otherwise an exception is thrown.
         * @throws IOException
         */
        public String finish() throws IOException {

            writer.append(LINE_FEED).flush();
            writer.append("--" + boundary + "--").append(LINE_FEED);
            writer.close();

            StringBuilder response = new StringBuilder();
            // checks server's status code first
            int status = httpConn.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        httpConn.getInputStream()));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
                reader.close();
                httpConn.disconnect();
            } else {
                Logger.debug(TAG, "Response:" + status);
            }
            return response.toString();
        }
    }

    public static long sendTelegramMessage(String botKey, String chatId, String message) {
        return sendTelegramMessage(botKey, chatId, null, message);
    }

    public static void sendTelegramMessageAsync(String botKey, String chatId, String message) {
        sendTelegramMessageAsync(botKey, chatId, null, message);
    }

    public static void sendTelegramDocumentsAsync(final String botKey,
                                            final String chatId,
                                            final String replyId,
                                            final File[] documentFiles) {
        TaskExecutor.executeOnNewThread(new Runnable() {
            @Override
            public void run() {
                for(File doc  :documentFiles) {
                    sendTelegramDocument(botKey, chatId, replyId, doc);
                }
            }
        });
    }

    public static void sendTelegramDocument(final String botKey,
                                                 final String chatId,
                                                 final String replyId,
                                                 final File documentFile) {


        StringBuilder messageUrl = getTelegramApiUrl(botKey, "sendDocument");

        if (Logger.DEBUG) {
            Logger.debug(TAG, "Sending Telegram document: %s", documentFile.getAbsolutePath());
        }

        try {
            MultipartUtility multipartUtility = new MultipartUtility(messageUrl.toString());
            multipartUtility.addFormField("chat_id", chatId);
            multipartUtility.addFilePart("document", documentFile);
            String response = multipartUtility.finish();
            Logger.debug(TAG, response);
        } catch (IOException e) {
            Logger.error(TAG, e.getMessage());
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
        try {

            if (Logger.DEBUG) {
                message = "* ***** DEBUG ***** *\n" + message;
            }

            String messageUrl = getMessageUrl(botKey, chatId, replyId, message);

            if (Logger.DEBUG) {
                Logger.debug(TAG, "Sending Telegram message: %s", message.replace("%", ""));
                Logger.debug(TAG, "Url length: %d", messageUrl.length());
            }

            HttpResponse response = httpGet(messageUrl);

            if (Logger.DEBUG) {
                Logger.debug(TAG, getResponseText(response));
            }

            int status = response.getStatusLine().getStatusCode();
            return ++mid;
            //return (status == 200 || status == 201);

        } catch (Exception e) {
            Logger.error(TAG, e.getMessage());
            return -1;
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

    private static String getMessageUrl(String botKey, String chatId, String replyId, String message) {
        StringBuilder messageUrl = getTelegramApiUrl(botKey, "sendMessage");

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

                                        updatesOffset = result.getInt("update_id") + 1;
                                        JSONObject message = result.optJSONObject("channel_post");
                                        if (message == null) {
                                            message = result.optJSONObject("message");
                                        }
                                        if (message != null) {
                                            String text = message.optString("text");
                                            if (TextUtils.isEmpty(text)) {
                                                JSONObject document = message.optJSONObject("document");
                                                if (document != null) {
                                                    text = String.format("document|%s|%s",
                                                            document.getString("file_name"),
                                                            document.getString("file_id"));
                                                }
                                            }
                                            if (!TextUtils.isEmpty(text)) {
                                                String chatId = message.getJSONObject("chat").getString("id");
                                                String messageId = message.optString("message_id");
                                                mUpdateCallback.onTelegramUpdateReceived(chatId, messageId, text);
                                            }
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
                if (result != null) {
                    String filePath = result.optString("file_path");
                    if (!TextUtils.isEmpty(filePath)) {
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
