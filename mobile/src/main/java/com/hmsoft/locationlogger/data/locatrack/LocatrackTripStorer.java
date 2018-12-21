package com.hmsoft.locationlogger.data.locatrack;

import android.location.Location;
import android.text.TextUtils;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;
import com.hmsoft.locationlogger.data.sqlite.TripTable;

public class LocatrackTripStorer extends LocationStorer {


    private static final String TAG = "LocatrackTripStorer";
    private final long STOP_TIME =  60 * 1000 * 2;

    private Location mLastReportedLocation = null;
    private Location mLastTripLocation = null;
    private Location mStopedLocation = null;
    private float mDistance;

    private boolean mIsMoving = false;
    private boolean mIsStoped = false;

    private int mMovingCount = 0;
    private long mFirstStopedTime = 0;


    @Override
    public boolean storeLocation(LocatrackLocation location) {
        if (LocatrackLocation.EVENT_START.equals(location.event)) {
            if (mLastTripLocation == null) {
                mDistance = 0;
                mLastTripLocation = location;
                if(Logger.DEBUG) {
                    Logger.debug(TAG, "Starting new trip.");
                }
            } else {
                location.event = LocatrackLocation.EVENT_RESTART;
            }
        } else if (LocatrackLocation.EVENT_STOP.equals(location.event)) {
            TripTable.TripDetail trip = TripTable.insertTrip(location.getTime(), mDistance, true);
            if(Logger.DEBUG) {
                Logger.debug(TAG, "Ending trip. Distance:" + (mDistance * 3.6));
            }
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
                    float distanceTo = mLastTripLocation.distanceTo(location);
                    mDistance += distanceTo;
                }
                mLastTripLocation = location;
            }
            if(mLastReportedLocation != null) {
                calculateMovement(mLastReportedLocation, location);
            }
            mLastReportedLocation = location;

        }
        return true;
    }

    private void calculateMovement(Location firstLocation, LocatrackLocation lastLocation) {
        float duration = (lastLocation.getTime() - firstLocation.getTime()) / 1000.0f;
        float distanceTo = lastLocation.distanceTo(firstLocation);
        float speed = distanceTo / duration;

        if(Logger.DEBUG) {
            Logger.debug(TAG, "Calculating movement: Speed: " + speed + ", Distance: " +
                    distanceTo + ", Duration: " + duration);
        }

        if (speed < 0.15f) {
            if(mFirstStopedTime == 0) {
                mFirstStopedTime = lastLocation.getTime();
            } else {
                long timeStoped = lastLocation.getTime() - mFirstStopedTime;
                if (timeStoped > STOP_TIME && !mIsStoped) {
                    mIsStoped = true;
                    mIsMoving = false;

                    mStopedLocation = lastLocation;
                    onMovementStop(lastLocation);
                }
                mMovingCount = 0;
            }
        } else if (speed > 0.2f) {
            if(!mIsMoving && (mMovingCount++ > 2 || distanceTo > 500 ||
                    (mStopedLocation != null && mStopedLocation.distanceTo(lastLocation) > 900))) {
                mIsMoving = true;
                mIsStoped = false;

                onMovementStart(lastLocation);
            }
            mFirstStopedTime = 0;
        }
    }

    private void onMovementStart(LocatrackLocation location) {
        if(TextUtils.isEmpty(location.event)) {
            location.event = LocatrackLocation.EVENT_MOVEMENT_START;
        }
        if(Logger.DEBUG) {
            Logger.debug(TAG, "Moving!");
        }
    }

    private void onMovementStop(LocatrackLocation location) {
        if(TextUtils.isEmpty(location.event)) {
            location.event = LocatrackLocation.EVENT_MOVEMENT_STOP;
        }
        if(Logger.DEBUG) {
            Logger.debug(TAG, "Stoped!");
        }
    }

    @Override
    public void configure() {

    }
}
