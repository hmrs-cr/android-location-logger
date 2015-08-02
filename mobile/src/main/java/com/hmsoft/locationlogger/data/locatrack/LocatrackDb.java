package com.hmsoft.locationlogger.data.locatrack;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;
import android.preference.PreferenceManager;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.data.LocationSet;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.sqlite.Helper;
import com.hmsoft.locationlogger.data.sqlite.LocationTable;

import java.io.IOException;

public class LocatrackDb extends LocationStorer {
    private static final String TAG = "LocatrackDatabase";

    private int mMinimunDistance;
    private Context mContext;

    public LocatrackDb(Context context) {
        mContext = context;
    }

    public static Location getLocationFromTimestamp(long timestamp, long timeRange) {
        return LocationTable.getFromTimestamp(Helper.getInstance(),
                timestamp, timeRange);
    }

    public static LocationSet getNotUploadedLocations() {
        return LocationTable.getAllNotUploaded(Helper.getInstance());
    }

    public static LocationSet getAllFromDate(long date) {
        return LocationTable.getAllFromDate(Helper.getInstance(), date);
    }

    public static void transactionBegin() {
        Helper.getInstance().getWritableDatabase().beginTransaction();
    }


    public static void transactionCommit() {
        SQLiteDatabase db = Helper.getInstance().getWritableDatabase();
        db.setTransactionSuccessful();
        db.endTransaction();
    }

    public static void setUploadDate(Location location) {
        LocationTable.setUploadDate(Helper.getInstance(), location);
    }

    @Override
    public void setUploadDateToday(Location location) {
        LocationTable.setUploadDate(Helper.getInstance(), location);
    }

    public static int getCount(boolean includeNotUploadedOnly) {
        return  LocationTable.getCount(Helper.getInstance(), includeNotUploadedOnly);
    }
	
    public static Location last() {
        return LocationTable.getLast(Helper.getInstance());
    }
	public static long getLastLocationTime() {
        return  LocationTable.getLastTime(Helper.getInstance());
    }

    @Override
    public boolean storeLocation(LocatrackLocation location) {
        mTotalItems++;
        if (Logger.DEBUG) {
            if(Logger.DEBUG) Logger.debug(TAG, "saveLocationToLocalDatabase");
        }

        long i = LocationTable.saveToDatabase(Helper.getInstance(), location, mMinimunDistance);
        if (Logger.DEBUG) {
            if(Logger.DEBUG) Logger.debug(TAG, "saveLocationToLocalDatabase location saved: %d", i);
        }

        boolean success = i > 0;
        if (success) {
            mTotalSuccess++;
        } else {
            mTotalFail++;
        }

        return success;
    }

    public void prepareDmlStatements() {
        LocationTable.prepareDmlStatements(Helper.getInstance());
    }

    @Override
    public void configure() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);

        mMinimunDistance = Integer.valueOf(preferences.getString(mContext.getString(R.string.pref_minimun_distance_key), String.valueOf(mMinimunDistance)));
    }

    @Override
    public void open() throws IOException {
        super.open();
        LocationTable.startBulkInsert(Helper.getInstance());
    }

    @Override
    public void close() {
        LocationTable.stopBulkInsert(Helper.getInstance());
        super.close();
    }
}

