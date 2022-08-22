package com.hmsoft.locationlogger.data.locatrack;

import android.content.Context;
import android.location.Location;
import android.text.TextUtils;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.data.LocationSet;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;
import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;
import com.hmsoft.locationlogger.data.sqlite.GeoFenceTable;
import com.hmsoft.locationlogger.data.sqlite.LocationTable;

import java.io.IOException;

public class LocatrackDb extends LocationStorer {
    private static final String TAG = "LocatrackDatabase";

    private int mMinimunDistance;
    private Context mContext;

    public LocatrackDb(Context context) {
        mContext = context;
    }

    public static LocationSet getAllFromDate(long date) {
        return LocationTable.getAllFromDate(date);
    }

    public static void setUploadDate(Location location) {
        LocationTable.setUploadDate(location);
    }

    @Override
    public void setUploadDateToday(Location location) {
        LocationTable.setUploadDate(location);
    }

    public static LocatrackLocation last() {
        return LocationTable.getLast();
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
            saveGeoFence(location);
            mTotalSuccess++;
        } else {
            mTotalFail++;
        }

        return success;
    }

    private void saveGeoFence(LocatrackLocation location) {
        if (!TextUtils.isEmpty(location.newGeoFenceLabel)) {
            int radio = 35;
            String label = location.newGeoFenceLabel;

            if (label.startsWith("radio:")) {
                int i1 = label.indexOf(':') + 1;
                int i2 = label.indexOf(',', i1);
                if (i2 > 0) {
                    try {
                        String s = label.substring(i1, i2);
                        radio = Integer.parseInt(s);
                    } catch (Exception e) {
                        // Ignore
                    }

                    label = label.substring(i2 + 1);
                }
            }

            long c = GeoFenceTable.saveGeofence(location.getLatitude(), location.getLongitude(), radio, label);
            if (c > 0 && Logger.DEBUG) Logger.debug(TAG, "Geofence saved: " + location.newGeoFenceLabel);
            location.newGeoFenceLabel = null;
        }
    }

    @Override
    public LocationStorer configure() {
        PreferenceProfile preferences = PreferenceProfile.get(mContext);
        mMinimunDistance = preferences.getInt(R.string.pref_minimun_distance_key, String.valueOf(mMinimunDistance));
        LocationTable.prepareDmlStatements();

        return this;
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

