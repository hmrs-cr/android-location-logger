package com.hmsoft.locationlogger.data.commands;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TelegramHelper;
import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

class DocumentCommand extends Command {

    static final String COMMAND_NAME = "document";
    private static final boolean DEBUG = Logger.DEBUG;
    private static final String TAG = COMMAND_NAME;

    @Override
    public String getSummary() {
        return "Internal usage only.";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {
        if(params.length == 2) {
            String[] values = params[1].split("\\|");

            String fileName = values[0];
            String fileId = values[1];

            String downloadUrl = TelegramHelper.getFileDownloadUrl(context.botKey, fileId);
            if (DEBUG) Logger.debug(TAG, "DownloadUrl: %s", downloadUrl);

            if (!TextUtils.isEmpty(downloadUrl) && !TextUtils.isEmpty(context.messageId)) {
                long id = downloadFile(fileName, downloadUrl);
                DownloadFinishedReceiver.addDownload(id, context.messageId);
            }
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        DownloadFinishedReceiver.unregister();
    }

    private long downloadFile(String fileName, String downloadUrl) {

        Context context = this.context.androidContext;

        if(DEBUG) Logger.debug(TAG, "Downloading file %s from %s", fileName, downloadUrl);
        DownloadManager downloadManager = (DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request .setTitle(fileName)
                .setDestinationUri(Uri.fromFile(new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)));

        /*
        PreferenceProfile preferences = PreferenceProfile.get(context);
        if(!preferences.getBoolean(0, false)) {
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
        }
        */

        DownloadFinishedReceiver.register(context);

        return downloadManager.enqueue(request);
    }

    private static class DownloadFinishedReceiver extends BroadcastReceiver {

        private static DownloadFinishedReceiver sInstance;
        private Context mContext;
        private Map<Long, String> mDownloads;

        @SuppressLint("UseSparseArrays")
        private DownloadFinishedReceiver(Context context) {
            mContext = context;
            mDownloads = new HashMap<>();
        }

        public static void register(Context context) {
            if (sInstance == null) {
                sInstance = new DownloadFinishedReceiver(context);
                IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
                sInstance.mContext.registerReceiver(sInstance, filter);
            }
        }

        public static void unregister() {
            if (sInstance != null) {
                sInstance.mContext.unregisterReceiver(sInstance);
                sInstance.mContext = null;
                sInstance.mDownloads = null;
                sInstance = null;
            }
        }

        public static void addDownload(long id, String messageId) {
            if (sInstance != null) {
                sInstance.mDownloads.put(id, messageId);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
            if (mDownloads.containsKey(id)) {
                String messageId = mDownloads.get(id);
                String botKey = mContext.getString(R.string.pref_telegram_botkey);
                String channelId = mContext.getString(R.string.pref_telegram_chatid);

                DownloadManager downloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
                Cursor c = downloadManager.query(new DownloadManager.Query().setFilterById(id));

                int status = -1;
                int reason = -1;

                if (c.moveToNext()) {
                    int i = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    if (i > -1) {
                        status = c.getInt(i);
                    }

                    i = c.getColumnIndex(DownloadManager.COLUMN_REASON);
                    if (i > -1) {
                        reason = c.getInt(i);
                    }
                }

                String message;
                if (status == -1) {
                    message = "Download done, unknown status.";
                } else if (DownloadManager.STATUS_SUCCESSFUL == status) {
                    message = "Download done!";
                } else {
                    message = "Download failed. " + reason;
                }
                c.close();

                TelegramHelper.sendTelegramMessageAsync(botKey, channelId, messageId, message);

                mDownloads.remove(id);
            }
        }
    }
}
