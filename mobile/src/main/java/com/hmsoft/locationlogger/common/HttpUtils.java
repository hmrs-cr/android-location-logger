package com.hmsoft.locationlogger.common;

import android.text.TextUtils;

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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class HttpUtils {
    private HttpUtils() {}

    private static final String TAG = "HttpUtils";

    public static class MultipartUtility {
        private static final String TAG = "MultipartUtility";

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
        public MultipartUtility addFormField(String name, String value) {
            writer.append("--" + boundary).append(LINE_FEED);
            writer.append("Content-Disposition: form-data; name=\"" + name + "\"")
                    .append(LINE_FEED);
            writer.append("Content-Type: text/plain; charset=" + charset).append(
                    LINE_FEED);
            writer.append(LINE_FEED);
            writer.append(value).append(LINE_FEED);
            writer.flush();

            return this;
        }

        /**
         * Adds a upload file section to the request
         *
         * @param fieldName  name attribute in <input type="file" name="..." />
         * @param uploadFile a File to be uploaded
         * @throws IOException
         */
        public MultipartUtility addFilePart(String fieldName, File uploadFile, String fileName)
                throws IOException {
            fileName = TextUtils.isEmpty(fileName) ? uploadFile.getName() : fileName;
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

            return this;
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

    public static String httpGetResponseText(String url) throws IOException {
        StringBuilder response = new StringBuilder();
        httpGet(url, response);
        return response.toString();
    }

    public static JSONObject httpGetResponseJson(String url) throws IOException, JSONException {
        return new JSONObject(httpGetResponseText(url));
    }

    public static int httpGet(String url) throws IOException {
        return httpGet(url, null);
    }

    public static int httpGet(String url, StringBuilder response) throws IOException {

        HttpURLConnection con = (HttpURLConnection) (new URL(url)).openConnection();
        try {
            con.setConnectTimeout(10000);
            con.setReadTimeout(180000);
            con.setRequestMethod("GET");

            if (Logger.DEBUG) {
                if (response == null) {
                    response = new StringBuilder();
                }
            }

            int responseCode = con.getResponseCode();
            if (response != null) {
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(con.getInputStream()));
                String inputLine;


                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                con.disconnect();

                if (Logger.DEBUG) {
                    Logger.debug(TAG, response.toString());
                }
            }

            return responseCode;
        } finally {
            con.disconnect();
        }
    }
}
