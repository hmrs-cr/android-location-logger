package com.hmsoft.locationlogger.service;

import android.accounts.Account;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.PerfWatch;
import com.hmsoft.locationlogger.data.locatrack.LocatrackDb;
import com.hmsoft.locationlogger.data.locatrack.LocatrackLocation;
import com.hmsoft.locationlogger.data.locatrack.LocatrackOnlineStorer;
import com.hmsoft.locationlogger.ui.MainActivity;
import com.hmsoft.locationlogger.ui.SettingsActivity;

import java.io.IOException;
import java.util.Calendar;

/**
 * Define a Service that returns an IBinder for the
 * sync adapter class, allowing the sync adapter framework to call
 * onPerformSync().
 */
public class SyncService extends Service {

    private static final String TAG = "SyncService";

    private static final int NOTIFICATION_ID = 2;

    /**
     * Handle the transfer of data between a server and an
     * app, using the Android sync adapter framework.
     */
    private static class SyncAdapter extends AbstractThreadedSyncAdapter {

        private static LocatrackOnlineStorer sStorer;
        private static  final Object sLock = new Object();
        private static boolean sIsSyncing = false;
		private static volatile int sAllFailCount = 0;


        private int mLastSyncNotifSuccessCount = 0;
        private int mLastSyncNotifErrorCount = 0;
        private int mSyncNotifId = 10;
        private PendingIntent mLocatrackIntent = null;

        private volatile boolean cancelled;

        /**
         * Set up the sync adapter
         */
        public SyncAdapter(Context context, boolean autoInitialize) {
            super(context, autoInitialize);
        }

        private void updateSyncNotification(int total, int fail) {
            if(total > 0 || fail > 0) {
                String contentText;
                 Context context = getContext();
                if(fail > 0) {
                    contentText = context.getString(R.string.sync_fail_notif_content_text, total, fail);
                } else {
                    contentText = context.getString(R.string.sync_notif_content_text, total);
                }

                if(mLocatrackIntent == null) {
                    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
                    String locatrackUri = preferences.getString(context.getString(R.string.pref_locatrack_uri_key),
                            context.getString(R.string.pref_locatrack_settings_title));

                    if(!TextUtils.isEmpty(locatrackUri)) {
                        Uri uri = Uri.parse(locatrackUri);
                        String path = uri.getPath();
                        if(!TextUtils.isEmpty(path)) {
                            uri = Uri.parse(locatrackUri.replace(path, ""));
                        }
                        Intent i = new Intent(Intent.ACTION_VIEW, uri);
                        mLocatrackIntent = PendingIntent.getActivity(context, 0, i, 0);
                    } else {
                        Intent activityIntent = new Intent(context, MainActivity.class);
                        activityIntent.setAction(Intent.ACTION_MAIN);
                        activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NEW_TASK);
                        mLocatrackIntent = PendingIntent.getActivity(context, 0, activityIntent, 0);
                    }
                }

                NotificationCompat.Builder notificationBuilder = (new NotificationCompat.Builder(context)).
                        setSmallIcon(R.drawable.ic_stat_cloud).
                        setContentIntent(mLocatrackIntent).
                        setAutoCancel(true).
                        setOngoing(false).
                        setContentTitle(context.getString(R.string.sync_notif_content_title)).
                        setContentText(contentText).
                        setPriority(NotificationCompat.PRIORITY_MIN);

                if(total != mLastSyncNotifSuccessCount || fail != mLastSyncNotifErrorCount) {
                    mSyncNotifId++;
                    mLastSyncNotifSuccessCount = total;
                    mLastSyncNotifErrorCount = fail;
                } else {
                    if(Logger.DEBUG) Logger.debug(TAG, "updateSyncNotification: total and fail counts are the same form last time.");
                }

                ((NotificationManager)context.getSystemService(NOTIFICATION_SERVICE)).notify(mSyncNotifId,
                        notificationBuilder.build());
            }
        }

        @Override
        public void onSyncCanceled() {
            if(Logger.DEBUG) Logger.debug(TAG, "onSyncCanceled");
            cancelled = true;
            super.onSyncCanceled();
        }

        /*
         * Specify the code you want to run in the sync adapter. The entire
         * sync adapter runs in a background thread, so you don't have to set
         * up your own background processing.
         */
        @Override
        public void onPerformSync(
                Account account,
                Bundle extras,
                String authority,
                ContentProviderClient provider,
                SyncResult syncResult) {

            synchronized (sLock) {
                if(sIsSyncing) {
                    return;
                }
                sIsSyncing = true;
            }

            final Context context = getContext();

            boolean manual = extras != null &&
                    extras.getBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, false);

            NotificationCompat.Builder builder = null;
            NotificationManager notifyManager = null;
            try {
                if(manual) {
                    Intent activityIntent = new Intent(context, SettingsActivity.class);
                    activityIntent.setAction(Intent.ACTION_MAIN);
                    activityIntent.addCategory(Intent.CATEGORY_LAUNCHER);
                    activityIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_NEW_TASK);

                    notifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    builder = new NotificationCompat.Builder(context);
                    builder.setContentTitle(context.getString(R.string.sync_progress_title))
                            .setContentText(context.getString(R.string.sync_progress_text))
                            .setSmallIcon(R.drawable.ic_stat_cloud)
                            .setContentIntent(PendingIntent.getActivity(context, 0, activityIntent, 0));
                }

                cancelled = false;
                boolean allUploaded = true;
                PerfWatch watch = PerfWatch.start(TAG, "PerformSync begin");
                try {
                    LocatrackLocation[] toUpload = LocatrackDb.getNotUploadedLocations().toArray();
                    int toUploadCount = toUpload.length;
                    Logger.info(TAG, "Locations to upload: %d", toUpload.length);
                    if (toUploadCount > 0) {
                        synchronized (sLock) {
                            if (SyncAdapter.sStorer == null) {
                                SyncAdapter.sStorer = new LocatrackOnlineStorer(context);
                            }
                        }

                        SyncAdapter.sStorer.configure();
                        syncResult.stats.numIoExceptions = 0;
                        syncResult.stats.numEntries = 0;
                        // Get all not uploaded from DB
                        try {
                            SyncAdapter.sStorer.open();
                            // upload each one
                            for (LocatrackLocation loc : toUpload) {
                                if(builder != null) {
                                    int progress = (int)syncResult.stats.numEntries +
                                            (int)syncResult.stats.numIoExceptions + 1;
                                    
                                    builder.setProgress(toUploadCount, progress, false);
                                    builder.setContentText(context.getString(R.string.sync_progress_advance,
                                                progress, toUploadCount,
                                                Math.round((float)progress/(float)toUploadCount*100.0F)));
                                    notifyManager.notify(NOTIFICATION_ID, builder.build());
                                }

                                boolean ok = SyncAdapter.sStorer.storeLocation(loc);
                                if (ok) {
                                    LocatrackDb.setUploadDate(loc);
                                    syncResult.stats.numEntries++;
                                } else {
                                    allUploaded = false;
                                    syncResult.stats.numIoExceptions++;
                                    syncResult.delayUntil = 60;
                                }

                                if(cancelled) {
                                    if(Logger.DEBUG) Logger.debug(TAG, "Sync cancelled.");
                                    allUploaded = false;
                                    break;
                                }
                            }
                            if (syncResult.stats.numIoExceptions == toUploadCount) {
                                if (++sAllFailCount >= 3) {
                                    if(Logger.DEBUG) Logger.debug(TAG, "Too many fails.");
                                    syncResult.tooManyRetries = true;
                                    sAllFailCount = 0;
                                }
                            } else {
                                sAllFailCount = 0;
                            }
                        } finally {
                            sStorer.close();
                            updateSyncNotification((int)syncResult.stats.numEntries,
                                    (int)syncResult.stats.numIoExceptions);
                        }
                    }
                }
                catch(IOException e) {
                    syncResult.tooManyRetries = true;
                    allUploaded = true;
                    Logger.warning(TAG, "PerformSync IOException", e);
                } catch (LocatrackOnlineStorer.MissingConfigurationException e) {
                    syncResult.tooManyRetries = true;
                    allUploaded = true;
                    Logger.warning(TAG, "PerformSync Missing config", e);
                }
                catch (Exception e) {
                    syncResult.tooManyRetries = true;
                    allUploaded = true;
                    Logger.warning(TAG, "PerformSync unknown error", e);
                }
                watch.stop(TAG, String.format("PerformSync end, allUploaded: %s", allUploaded));
                if(!cancelled) {
                    SyncService.setAutoSync(context, !allUploaded);
                }
            } finally {
                if(notifyManager != null) {
                    notifyManager.cancel(NOTIFICATION_ID);
                }
                synchronized (sLock) {
                    sIsSyncing = false;
                }
            }
        }
    }

    // Storage for an instance of the sync adapter
    private static SyncAdapter sSyncAdapter = null;
    // Object to use as a thread-safe lock
    private static final Object sSyncAdapterLock = new Object();

    public static void setAutoSync(Context context, boolean sync) {
        Account account = SyncAuthenticatorService.getSyncAccount(context);
        String authority =  context.getString(R.string.location_provider_authority);
        ContentResolver.setSyncAutomatically(account, authority, sync);
    }

    public static void syncNow(Context context) {
        Bundle settingsBundle = new Bundle();
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
		
        Account account = SyncAuthenticatorService.getSyncAccount(context);
        String authority = context.getString(R.string.location_provider_authority);
        /*
         * Request the sync for the default account, authority, and
         * manual sync settings
         */
        ContentResolver.requestSync(account, authority, settingsBundle);

    }

    public static long getMillisOfTomorrowTime(int hour, int minute) {
        long curMillis = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(curMillis);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);

        long millis = calendar.getTimeInMillis();
        if(curMillis >= millis) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            millis = calendar.getTimeInMillis();
        }
        return millis;
    }

    /*
     * Instantiate the sync adapter object.
     */
    @Override
    public void onCreate() {
        /*
         * Create the sync adapter as a singleton.
         * Set the sync adapter as syncable
         * Disallow parallel syncs
         */
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new SyncAdapter(getApplicationContext(), true);
            }
        }
    }

    /**
     * Return an object that allows the system to invoke
     * the sync adapter.
     */
    @Override
    public IBinder onBind(Intent intent) {
        /*
         * Get the object that allows external processes
         * to call onPerformSync(). The object is created
         * in the base class code when the SyncAdapter
         * constructors call super()
         */
        return sSyncAdapter.getSyncAdapterBinder();
    }
}
