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

import com.hmsoft.locationlogger.LocationLoggerApp;
import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;
import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;
import com.hmsoft.locationlogger.data.sqlite.Helper;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class DocumentCommand extends InternalCommand {

    static final String COMMAND_NAME = "document";
    private static final boolean DEBUG = Logger.DEBUG;
    private static final String TAG = COMMAND_NAME;

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params, CommandContext context) {
        if(params.length == 2) {
            String[] values = params[1].split("\\|");

            String fileName = values[0];
            String fileId = values[1];

            String downloadUrl = TelegramHelper.getFileDownloadUrl(context.botKey, fileId);
            if (DEBUG) Logger.debug(TAG, "DownloadUrl: %s", downloadUrl);

            if (!TextUtils.isEmpty(downloadUrl) && !TextUtils.isEmpty(context.messageId)) {
                long id = downloadFile(fileName, downloadUrl);
                DownloadFinishedReceiver.addDownload(id, context);
            }
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        DownloadFinishedReceiver.unregister();
    }

    private long downloadFile(String fileName, String downloadUrl) {

        Context context = LocationLoggerApp.getContext();

        boolean isSilentDownload = false;
        File downloadDestination = null;
        if(isSilent(fileName)) {
            isSilentDownload = true;
            try {
                 fileName = TextUtils.isEmpty(fileName) ?  "document_" : fileName;
                downloadDestination = File.createTempFile(fileName, null,
                        context.getExternalCacheDir());
                downloadDestination.delete();
            } catch (IOException e) {
                downloadDestination = new File(context.getExternalCacheDir(), "document.tmp");
            }
        } else {
            downloadDestination = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
        }

        if(DEBUG) Logger.debug(TAG, "Downloading file %s from %s", fileName, downloadUrl);

        DownloadManager downloadManager = (DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        request.setDestinationUri(Uri.fromFile(downloadDestination));

        PreferenceProfile preferences = PreferenceProfile.get(context);
        if(isSilentDownload) {
            request.setAllowedOverMetered(true);
        } else {
            request.setTitle(fileName);
            if (!preferences.getBoolean(R.string.pref_unlimited_data_key, false)) {
                request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI);
            }
        }


        DownloadFinishedReceiver.register(context);

        return downloadManager.enqueue(request);
    }

    private boolean isSilent(String fileName) {
        return TextUtils.isEmpty(fileName) || fileName.startsWith(TelegramHelper.VOICE_PREFIX);
    }

    private static class DownloadFinishedReceiver extends BroadcastReceiver {

        private static DownloadFinishedReceiver sInstance;
        private Context mContext;
        private Map<Long, CommandContext> mDownloads;

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
                sInstance.mDownloads.clear();
                sInstance.mDownloads = null;
                sInstance = null;
            }
        }

        public static void addDownload(long id, CommandContext commandContext) {
            if (sInstance != null) {
                sInstance.mDownloads.put(id, commandContext);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
            if (mDownloads.containsKey(id)) {

                CommandContext commandContext = mDownloads.get(id);
                DownloadManager downloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
                Cursor c = downloadManager.query(new DownloadManager.Query().setFilterById(id));

                int status = -1;
                int reason = -1;
                String fileUri = "";

                if (c.moveToNext()) {
                    int i = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                    if (i > -1) {
                        status = c.getInt(i);
                    }

                    i = c.getColumnIndex(DownloadManager.COLUMN_REASON);
                    if (i > -1) {
                        reason = c.getInt(i);
                    }
                    i = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                    if (i > -1) {
                        fileUri = c.getString(i);
                    }
                }

                String message;
                if (status == -1) {
                    message = "Download done, unknown status.";
                } else if (DownloadManager.STATUS_SUCCESSFUL == status) {
                    boolean processed = false;
                    try {
                        processed = processDownload(Uri.parse(fileUri));
                    } catch (Exception e) {
                        Logger.error(TAG, "Error processing download", e);
                    }
                    message = processed ? "Download and processed successfully!" : "Download done, failed to process.";
                } else {
                    message = "Download failed. " + reason;
                }
                c.close();

                commandContext.sendTelegramReplyAsync(message);

                mDownloads.remove(id);
            }
        }

        private boolean processDownload(final Uri fileUri) {
            String fileName = fileUri.getPath();
            if (fileName.contains("database.backup") && fileName.endsWith(".db")) {
                return Helper.getInstance().importDB(fileName);
            }

            if (fileName.contains("/LocationLogger-") && fileName.endsWith(".apk")) {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(fileUri,"application/vnd.android.package-archive");
                 LocationLoggerApp.getContext().startActivity(intent);
                 return true;
            }

            if(fileName.contains("/" + TelegramHelper.VOICE_PREFIX) && fileName.endsWith(".tmp")) {
                TaskExecutor.executeOnNewThread(new Runnable() {
                    @Override
                    public void run() {
                        Utils.playAudio(fileUri, true);
                    }
                });
                return true;
            }

            return false;
        }
    }
}
