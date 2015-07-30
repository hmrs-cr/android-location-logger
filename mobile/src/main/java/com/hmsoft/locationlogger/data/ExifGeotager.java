package com.hmsoft.locationlogger.data;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.location.Location;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import com.hmsoft.locationlogger.BuildConfig;
import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Constants;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.data.locatrack.LocatrackDb;
import com.hmsoft.locationlogger.service.LocationService;
import com.hmsoft.locationlogger.ui.MainActivity;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class ExifGeotager {

    private static final int GEOTAG_STATUS_SUCCESS = 0;
    private static final int GEOTAG_STATUS_ALREADY_GEOTAGGED = 1;
    private static final int GEOTAG_STATUS_NO_DATE = 2;
    private static final int GEOTAG_STATUS_NO_LOCATION_FOR_DATE = 3;
    private static final int GEOTAG_STATUS_IO_ERROR = 4;

    public abstract static class GeotagFinishListener implements Runnable {
        private int totalCount;
        private int geotagedCount;

        protected abstract void onGeotagTaskFinished(int totalCount, int geotagedCount);

        @Override
        public void run() {
            onGeotagTaskFinished(totalCount, geotagedCount);
        }
    }

    public static final int NOTIFICATION_ID = 2;

    private static final String TAG = "GeotagerService";
    private static final String LAST_GEOTAGED_PICT_DATE_KEY = "last_geotaged_pic_date";

    private static final SimpleDateFormat sFormatter = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
    private static final SimpleDateFormat sFilenameFormatter = new SimpleDateFormat("yyyyMMddHHmmss");

    private static final long MIN_EXIF_DATE_OFFSET = 60000L * 60L * 24L * 365L * 11L;
    private static final long MAX_EXIF_DATE_OFFSET = 60000L * 60L * 24L * 30L;

    private static final String[] PROJECTION_COLUMNS = new String[] {
            MediaStore.Images.ImageColumns.DATA,
            MediaStore.Images.ImageColumns.DATE_ADDED
    };

    private static PendingIntent sLocationActivityIntent;
    private static PendingIntent sDeleteIntent;
    private static String[] sSelectionArgs = new String[1];
    private static HandlerThread sExecutorThread = null;
    private static Handler sHandler;
    private static volatile int sNotifyTotalCount = 0;
    private static volatile int sNotifyOkCount = 0;
    private static NotificationCompat.Builder sNotificationBuilder;

    private static void createNotificationBuilder(Context context) {
        if(sNotificationBuilder == null) {
            if (sLocationActivityIntent == null) {
                Intent activityIntent = new Intent(context, MainActivity.class);
                activityIntent.setAction(Intent.ACTION_MAIN);
                activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NEW_TASK);
                sLocationActivityIntent = PendingIntent.getActivity(context, 0, activityIntent, 0);
            }
            sNotificationBuilder = (new NotificationCompat.Builder(context)).
                    setSmallIcon(R.drawable.ic_stat_pic).
                    setContentIntent(sLocationActivityIntent).
                    setContentTitle(context.getString(R.string.geotagger_notify_title)).
                    setPriority(NotificationCompat.PRIORITY_DEFAULT);
        }        
    }
    private static void notifyProgress(Context context, int currentIndex, int total, 
                                       int tagged, String mediaName) {
        createNotificationBuilder(context);

        sNotificationBuilder.
                setAutoCancel(false).
                setOngoing(true).
                setNumber(tagged).
                setContentText(context.getString(R.string.sync_progress_geotag, currentIndex, total,
                        mediaName)).
                setProgress(total, currentIndex, false);
        
        ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, sNotificationBuilder.build());
    }
    
    public static void notify(Context context, int totalCount, int okCount) {
        if(totalCount == 0) return;

        if(sDeleteIntent == null) {
            Intent i = new Intent(context, LocationService.class);
            i.putExtra(Constants.EXTRA_NOTIFICATION_DELETED, NOTIFICATION_ID);
            sDeleteIntent = PendingIntent.getService(context, 0, i, 0);
        }

        sNotifyTotalCount += totalCount;
        sNotifyOkCount += okCount;

        String text;
        if(sNotifyTotalCount == sNotifyOkCount) {
            text = String.format(context.getString(R.string.geotagger_notify_text_ok), sNotifyTotalCount);
        } else {
            text = String.format(context.getString(R.string.geotagger_notify_text_failed),
                    sNotifyOkCount,   sNotifyTotalCount - sNotifyOkCount);
        }

        createNotificationBuilder(context);
        sNotificationBuilder.
                setContentText(text).
                setDeleteIntent(sDeleteIntent).
                setWhen(System.currentTimeMillis()).
                setAutoCancel(true).
                setOngoing(false).
                setProgress(0, 0, false).
                setNumber(0).
                setContentTitle(context.getString(R.string.geotagger_notify_title));
                
        ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, sNotificationBuilder.build());

        sNotificationBuilder = null;
    }

    public static void clearNotifyCounts() {
        sNotifyOkCount = 0;
        sNotifyTotalCount = 0;
        if(Logger.DEBUG)  Logger.debug(TAG, "clearNotifyCounts");
    }

    private static long getDateFromFileName(String fileName) {
        File file = new File(fileName);
        fileName = file.getName().replaceAll("[^\\d]", "");
        try {
            Date date = sFilenameFormatter.parse(fileName);
            if(date != null) {
                long time = date.getTime();
                long todayTime = System.currentTimeMillis();
                if((time > todayTime - MIN_EXIF_DATE_OFFSET) &&
                        (time < todayTime + MAX_EXIF_DATE_OFFSET)) {
                    return time;
                }
            }
        } catch (ParseException e) {
            Logger.warning(TAG, "getDateFromFileName parseException", e);
        }
        return file.lastModified();
    }

    private static String coordToDms(double coord) {
        coord = coord > 0 ? coord : -coord;
        String result = Integer.toString((int)coord) + "/1,";
        coord = (coord % 1) * 60;
        result = result + Integer.toString((int)coord) + "/1,";
        coord = (coord % 1) * 60000;
        result = result + Integer.toString((int)coord) + "/1000";
        return result;
    }

    public static int geoTagPicture(String fileName) {
        try {
            ExifInterface exif = new ExifInterface(fileName);

            boolean hasGeotag =
                    exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null &&
                            exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF) != null &&
                            exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) != null &&
                            exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF) != null;


            if (hasGeotag) {
                if(Logger.DEBUG)  Logger.debug(TAG, "'%s' already has geotag.", fileName);
                log(fileName, "Already has geotag", null);
                return GEOTAG_STATUS_ALREADY_GEOTAGGED;
            }

            long picDate = 0;

            String dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME);
            if (!TextUtils.isEmpty(dateTime)) {
                if(Logger.DEBUG)  Logger.debug(TAG, "Exif date: %s", dateTime);
                try {
                    Date datetime = sFormatter.parse(dateTime);
                    if (datetime != null) {
                        picDate = datetime.getTime();
                    }
                } catch (ParseException ex) {
                    Logger.warning(TAG, "Exception on geoTagPicture", ex);
                }
            }

            if (picDate < 1) {
                picDate = getDateFromFileName(fileName);
                if (picDate < 1) {
                    if(Logger.DEBUG)  Logger.debug(TAG, "Could not determine picture date.");
                    log(fileName, "No picture date.", null);
                    return GEOTAG_STATUS_NO_DATE;
                }
            }

            if (Logger.DEBUG) {
                if(Logger.DEBUG) Logger.debug(TAG, "Found  picture date: %s", new Date(picDate));
            }

            Location location = LocatrackDb.getLocationFromTimestamp(picDate, 0);
            if (location == null) {
                if(Logger.DEBUG) Logger.debug(TAG, "No location found for picture %s", fileName);
                log(fileName, "No location for picture date:" + picDate, null);
                return GEOTAG_STATUS_NO_LOCATION_FOR_DATE;
            }

            if (Logger.DEBUG) {
                if(Logger.DEBUG) Logger.debug(TAG, "Location found with date: %s", new Date(location.getTime()));
            }

            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, coordToDms(location.getLatitude()));
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, coordToDms(location.getLongitude()));
            if (location.getLatitude() > 0) {
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N");
            } else {
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "S");
            }
            if (location.getLongitude() > 0) {
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "E");
            } else {
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W");
            }

            exif.saveAttributes();
            if(Logger.DEBUG) Logger.debug(TAG, "Picture %s geotaged successfully!", fileName);
            log(fileName, "Tagged!", null);
            return GEOTAG_STATUS_SUCCESS;

        } catch (IOException e) {
            Logger.warning(TAG, "geoTagPicture error", e);
            log(fileName, "Fatal error", e);
            return GEOTAG_STATUS_IO_ERROR;
        }
    }

    private static void log(String fileName, String msg, Throwable e) {
        Logger.log2file(TAG, String.format("%s: %s", fileName, msg),
                "geotager-%s.log", e);
    }

    public static void geoTagContent(final Context context, 
                                     final Uri contentUri, 
                                     final boolean goTagAll,
                                     final boolean notifyProgress,
                                     final GeotagFinishListener geotagFinishListener) {

        if(sExecutorThread == null) {
            sExecutorThread = new HandlerThread(BuildConfig.APPLICATION_ID + "." + TAG);
            sExecutorThread.start();
            Looper looper = sExecutorThread.getLooper();
            sHandler = new Handler(looper);
        }

        sHandler.post(new Runnable() {
            @Override
            public void run() {
                SharedPreferences preferences =
                        PreferenceManager.getDefaultSharedPreferences(context);

                long lastGeotagedPictureDate = preferences.getLong(LAST_GEOTAGED_PICT_DATE_KEY, 0);
                if(lastGeotagedPictureDate < 1) {
                    lastGeotagedPictureDate = (System.currentTimeMillis() / 1000) -
                            (60 * 60 * 24 * 7);
                }

                String selectionCondition = null;
                String[] selectionArgs = null;
                if(!goTagAll) {
                    sSelectionArgs[0] = String.valueOf(lastGeotagedPictureDate);
                    selectionCondition = MediaStore.Images.ImageColumns.DATE_ADDED + " > ?";
                    selectionArgs = sSelectionArgs;
                }
                
                Cursor result = context.getContentResolver().query(contentUri, PROJECTION_COLUMNS,
                        selectionCondition/*selection*/,
                        selectionArgs/*selection args*/,
                        MediaStore.Images.ImageColumns.DATE_ADDED/*sort order*/);

                int total = 0;
                int tagedCount = 0;
                int totalCount = 0;
                int currentIndex = 0;
                if(result != null && result.moveToFirst()) {
                    total = totalCount = result.getCount();
                    if (Logger.DEBUG) {
                        if(Logger.DEBUG) Logger.debug(TAG, "Uri %s returned %d results", contentUri, total);
                    }

                    String mediaName = contentUri.getPath();
                    long lastPictureDate;
                    while (true) {
                        if(notifyProgress) {
                            notifyProgress(context, ++currentIndex, totalCount, tagedCount, mediaName);
                        }
                        String fileName = result.getString(0);
                        if(Logger.DEBUG) Logger.debug(TAG, "Found image: %s", fileName);
                        int status = geoTagPicture(fileName);
                        if (status == GEOTAG_STATUS_SUCCESS) {
                            tagedCount++;
                        }else if(status == GEOTAG_STATUS_ALREADY_GEOTAGGED) {
                            total--;
                        }
                        lastPictureDate = result.getLong(1);
                        if (!result.moveToNext()) {
                            break;
                        }
                    }

                    if (lastPictureDate > 1) {
                        preferences.edit().putLong(LAST_GEOTAGED_PICT_DATE_KEY,
                                lastPictureDate).commit();
                    }
                } else {
                    if(Logger.DEBUG) Logger.debug(TAG, "No results for uri:%s", contentUri);
                }

                if(geotagFinishListener != null) {
                    geotagFinishListener.geotagedCount = tagedCount;
                    geotagFinishListener.totalCount = total;
                    TaskExecutor.executeOnUIThread(geotagFinishListener);
                }
            }
        });
    }
}
