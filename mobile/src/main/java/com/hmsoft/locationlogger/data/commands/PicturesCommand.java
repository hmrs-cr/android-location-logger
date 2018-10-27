package com.hmsoft.locationlogger.data.commands;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;

import com.hmsoft.locationlogger.common.TelegramHelper;

import java.io.File;

class PicturesCommand extends Command {
    static final String COMMAND_NAME = "Pictures";

    private static final String LAST_GEOTAGED_PICT_DATE_KEY = "last_geotaged_pic_date";

    @Override
    public String getSummary() {
        return "Get pictures in device";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {

        final String[] PROJECTION_COLUMNS = new String[] {
                MediaStore.Images.ImageColumns.DATA,
                MediaStore.Images.ImageColumns.DATE_ADDED
        };

        SharedPreferences preferences =
                PreferenceManager.getDefaultSharedPreferences(context.androidContext);

        long lastGeotagedPictureDate = preferences.getLong(LAST_GEOTAGED_PICT_DATE_KEY, 0);
        if(lastGeotagedPictureDate == 0) {
            lastGeotagedPictureDate = System.currentTimeMillis() - (1000 * 60 * 60 * 24 * 8);
        }

        String selectionCondition = MediaStore.Images.ImageColumns.DATE_ADDED + " > ?";;
        String[] selectionArgs = new String[1];;

        selectionArgs[0] = String.valueOf(lastGeotagedPictureDate);

        Uri contentUri = MediaStore.Images.Media.INTERNAL_CONTENT_URI;

        String[] subParams = getSubParams(params);
        if(contains(subParams, "external")) {
            contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        }

        Cursor result = context.androidContext.getContentResolver().query(contentUri, PROJECTION_COLUMNS,
                selectionCondition/*selection*/,
                selectionArgs/*selection args*/,
                MediaStore.Images.ImageColumns.DATE_ADDED/*sort order*/);

        long lastPictureDate = 0;
        int c = 0;
        while(result.moveToNext()) {
            String fileName = result.getString(0);
            TelegramHelper.sendTelegramDocument(context.botKey, context.fromId, context.messageId, new File(fileName));
            lastPictureDate = result.getLong(1);
            c++;
        }

        if(c == 0) {
            sendTelegramReply("No pictures found.");
        }

        if (lastPictureDate > 1) {
            preferences.edit().putLong(LAST_GEOTAGED_PICT_DATE_KEY,
                    lastPictureDate).commit();
        }
    }
}
