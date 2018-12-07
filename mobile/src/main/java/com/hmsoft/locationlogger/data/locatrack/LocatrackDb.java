package com.hmsoft.locationlogger.data.locatrack;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.data.LocationSet;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;
import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;
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
        return LocationTable.getFromTimestamp(timestamp, timeRange);
    }

    public static LocationSet getNotUploadedLocations() {
        return LocationTable.getAllNotUploaded();
    }

    public static LocationSet getAllFromDate(long date) {
        return LocationTable.getAllFromDate(date);
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
        LocationTable.setUploadDate(location);
    }

    @Override
    public void setUploadDateToday(Location location) {
        LocationTable.setUploadDate(location);
    }

    public static long getCount(boolean includeNotUploadedOnly) {
        return  LocationTable.getCount(includeNotUploadedOnly);
    }
	
    public static LocatrackLocation last() {
        return LocationTable.getLast();
    }
	public static long getLastLocationTime() {
        return  LocationTable.getLastTime();
    }

    @Override
    public boolean storeLocation(LocatrackLocation location) {
        mTotalItems++;
        if (Logger.DEBUG) {
            if(Logger.DEBUG) Logger.debug(TAG, "saveLocationToLocalDatabase");
        }

        long i = LocationTable.saveToDatabase(location, mMinimunDistance);
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
        LocationTable.prepareDmlStatements();
    }

    @Override
    public void configure() {
        PreferenceProfile preferences = PreferenceProfile.get(mContext);
        mMinimunDistance = preferences.getInt(R.string.pref_minimun_distance_key, String.valueOf(mMinimunDistance));
    }

    @Override
    public void open() throws IOException {
        super.open();
        LocationTable.startBulkInsert();
    }

    @Override
    public void close() {
        LocationTable.stopBulkInsert();
        super.close();
    }
}

