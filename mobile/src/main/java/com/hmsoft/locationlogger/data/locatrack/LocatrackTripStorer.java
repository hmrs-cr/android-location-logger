package com.hmsoft.locationlogger.data.locatrack;

import android.location.Location;
import android.text.TextUtils;

import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;
import com.hmsoft.locationlogger.data.sqlite.TripTable;

public class LocatrackTripStorer extends LocationStorer {


    private Location mLastTripLocation = null;
    private float mDistance;

    @Override
    public boolean storeLocation(LocatrackLocation location) {
        if (LocatrackLocation.EVENT_START.equals(location.event)) {
            if (mLastTripLocation == null) {
                mDistance = 0;
                mLastTripLocation = location;
            } else {
                location.event = LocatrackLocation.EVENT_RESTART;
            }
        } else if (LocatrackLocation.EVENT_STOP.equals(location.event)) {
            TripTable.TripDetail trip = TripTable.insertTrip(location.getTime(), mDistance, true);
            mDistance = 0;
            mLastTripLocation = null;
            if (trip != null) {
                String extraInfo = location.extraInfo;
                location.extraInfo = trip.toString();
                if (!TextUtils.isEmpty(extraInfo)) {
                    location.extraInfo += "\n\n" + extraInfo;
                }
            }
        } else {
            if (mLastTripLocation != null) {
                if(Utils.isFromGps(mLastTripLocation)) {
                    mDistance += mLastTripLocation.distanceTo(location);
                }
                mLastTripLocation = location;
            }
        }
        return true;
    }

    @Override
    public void configure() {

    }
}
