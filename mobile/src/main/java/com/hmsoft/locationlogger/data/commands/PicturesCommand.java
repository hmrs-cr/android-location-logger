package com.hmsoft.locationlogger.data.commands;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.TextUtils;

import com.hmsoft.locationlogger.LocationLoggerApp;
import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;
import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;

import java.io.File;

class PicturesCommand extends Command {
    static final String COMMAND_NAME = "Pictures";

    private static final String LAST_UPLOADED_PICT_DATE_KEY = "last_uploaded_pic_date";


    static class PictureContentObserver extends ContentObserver {

        private static final String TAG = "PictureContentObserver";

        private static PictureContentObserver sExternalInstance = null;
        private static PictureContentObserver sInternalInstance = null;

        private final Uri mUri;
        private final Context mContext;
        public String chatId;

        private PictureContentObserver(Context context, Uri uri) {
            super(null);
            mUri = uri;
            mContext = context;
        }

        public void onChange(boolean selfChange, Uri uri) {
            if(Logger.DEBUG) Logger.debug(TAG, "onChange:%s,%s", selfChange, uri);
            boolean sent = false;
            if(uri != null) {
                if(uri.equals(mUri)) {
                    sent = true;
                }
            }  else {
                sent = true;
            }

            if(sent && !TextUtils.isEmpty(chatId)) {
                TaskExecutor.executeOnNewThread(new Runnable() {
                    @Override
                    public void run() {
                        Context context = LocationLoggerApp.getContext();
                        CommandContext cmdContext = new CommandContext(
                                context,
                                SOURCE_TELEGRAM,
                                PreferenceProfile.get(context).getString(R.string.pref_telegram_botkey_key, context.getString(R.string.pref_telegram_botkey_default)),
                                chatId,
                                null,
                                null,
                                null,
                                PreferenceProfile.get(context).getString(R.string.pref_telegram_chatid_key, mContext.getString(R.string.pref_telegram_chatid_default)),
                                true
                        );
                        sendPictures(cmdContext, mUri);
                    }
                });
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        public static void register(CommandContext context) {
            if(sExternalInstance == null) {
                sExternalInstance = new PictureContentObserver(context.androidContext,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            }
            if(sInternalInstance == null) {
                sInternalInstance = new PictureContentObserver(context.androidContext,
                        MediaStore.Images.Media.INTERNAL_CONTENT_URI);
            }

            sExternalInstance.chatId = context.fromId;
            sInternalInstance.chatId = context.fromId;

            ContentResolver resolver = context.androidContext.getContentResolver();
            resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    false, sExternalInstance);
            resolver.registerContentObserver(MediaStore.Images.Media.INTERNAL_CONTENT_URI,
                    false, sInternalInstance);

            if(Logger.DEBUG) Logger.debug(TAG, "ContentObserver registered");
        }

        public static void unregister(Context context) {
            ContentResolver resolver = context.getContentResolver();
            if(sExternalInstance != null) {
                resolver.unregisterContentObserver(sExternalInstance);
                sExternalInstance = null;
                if(Logger.DEBUG) Logger.debug(TAG, "ContentObserver UNregistered (external)");
            }
            if(sInternalInstance != null) {
                resolver.unregisterContentObserver(sInternalInstance);
                sInternalInstance = null;
                if(Logger.DEBUG) Logger.debug(TAG, "ContentObserver UNregistered (internal)");
            }
        }
    }

    @Override
    public void cleanup() {
        super.cleanup();
        PictureContentObserver.unregister(LocationLoggerApp.getContext());
    }

    @Override
    public String getSummary() {
        return "Get pictures in device";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    public static void sendPictures(CommandContext context, Uri contentUri) {
        final String[] PROJECTION_COLUMNS = new String[] {
                MediaStore.Images.ImageColumns.DATA,
                MediaStore.Images.ImageColumns.DATE_ADDED
        };

        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context.androidContext);


        String lastUploadedPictureDateKey = LAST_UPLOADED_PICT_DATE_KEY + "_" + Math.abs(contentUri.hashCode());
        long lastGeotagedPictureDate = preferences.getLong(LAST_UPLOADED_PICT_DATE_KEY, 0);
        if(lastGeotagedPictureDate == 0) {
            lastGeotagedPictureDate = (System.currentTimeMillis() / 1000) - (60 * 60 * 24 * 8);
        }
        lastGeotagedPictureDate = preferences.getLong(lastUploadedPictureDateKey, lastGeotagedPictureDate);


        String selectionCondition = MediaStore.Images.ImageColumns.DATE_ADDED + " > ?";
        String[] selectionArgs = new String[1];

        selectionArgs[0] = String.valueOf(lastGeotagedPictureDate);

        Cursor result = context.androidContext.getContentResolver().query(contentUri, PROJECTION_COLUMNS,
                selectionCondition/*selection*/,
                selectionArgs/*selection args*/,
                MediaStore.Images.ImageColumns.DATE_ADDED/*sort order*/);

        long lastPictureDate = 0;
        int c = 0;
        String chatId = context.source == SOURCE_SMS ? context.channelId : context.fromId;
        while(result.moveToNext()) {
            String fileName = result.getString(0);
            TelegramHelper.sendTelegramDocument(context.botKey, context.fromId, chatId, new File(fileName), null);
            lastPictureDate = result.getLong(1);
            c++;
        }

        result.close();

        if(c == 0) {
            sendReply(context,"No pictures found.");
        }

        if (lastPictureDate > 1) {
            preferences.edit().putLong(lastUploadedPictureDateKey, lastPictureDate).commit();
        }
    }

    @Override
    public void execute(String[] params, CommandContext context) {

        Uri contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] subParams = getSubParams(params);
        if(contains(subParams, "internal")) {
            contentUri = MediaStore.Images.Media.INTERNAL_CONTENT_URI;
        }

        sendPictures(context, contentUri);

        PictureContentObserver.register(context);
    }
}
