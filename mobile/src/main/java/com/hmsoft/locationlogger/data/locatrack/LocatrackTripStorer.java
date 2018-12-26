package com.hmsoft.locationlogger.data.locatrack;

import android.content.SharedPreferences;
import android.location.Location;
import android.text.TextUtils;

import com.hmsoft.locationlogger.LocationLoggerApp;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;
import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;
import com.hmsoft.locationlogger.data.sqlite.TripTable;

public class LocatrackTripStorer extends LocationStorer {


    private static final String TAG = "LocatrackTripStorer";
    private static final String MOVEMENT_PREF_KEY = "calculate_movement";

    private final long STOP_TIME =  60 * 1000 * 2;

    private Location mLastReportedLocation = null;
    private Location mLastTripLocation = null;
    private Location mStopedLocation = null;
    private float mDistance;

    private boolean mIsMoving = false;
    private boolean mIsStoped = false;

    private int mMovingCount = 0;
    private long mFirstStopedTime = 0;
    private boolean mCalculateMovement;


    public interface MovementChangeCallback {
        void onMovementChange(Boolean isMoving, Boolean isNotMoving, float distance);
    }

    private final MovementChangeCallback movementChangeCallback;

    public LocatrackTripStorer() {
        this.movementChangeCallback = null;
    }

    public LocatrackTripStorer(MovementChangeCallback movementChangeCallback) {
        this.movementChangeCallback = movementChangeCallback;
    }


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
            configure();
        } else if (LocatrackLocation.EVENT_STOP.equals(location.event)) {
            if(mDistance > 500) {
                TripTable.TripDetail trip = TripTable.insertTrip(location.getTime(), mDistance, true);
                if (Logger.DEBUG) {
                    Logger.debug(TAG, "Ending trip. Distance:" + (mDistance * 3.6));
                }
                if (trip != null) {
                    String extraInfo = location.extraInfo;
                    location.extraInfo = trip.toString();
                    if (!TextUtils.isEmpty(extraInfo)) {
                        location.extraInfo += "\n\n" + extraInfo;
                    }
                }
                configure();
            }
            mDistance = 0;
            mLastTripLocation = null;
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
        doCallback();
        return true;
    }

    private void calculateMovement(Location firstLocation, LocatrackLocation lastLocation) {
        if(!mCalculateMovement) {
            if(Logger.DEBUG) {
                Logger.debug(TAG, "Calculate movement disabled");
            }
           return;
        }

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

    private void doCallback() {
        if(this.movementChangeCallback != null) {
            Boolean moving = mCalculateMovement ? mIsMoving : null;
            Boolean notMoving = mCalculateMovement ? mIsStoped : null;
            this.movementChangeCallback.onMovementChange(moving, notMoving, mDistance);
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
    public LocationStorer configure() {
        SharedPreferences prefs = PreferenceProfile.get(LocationLoggerApp.getContext()).getPreferences();
        if(!prefs.contains(MOVEMENT_PREF_KEY)) {
            prefs.edit().putBoolean(MOVEMENT_PREF_KEY, true).apply();
        }
        mCalculateMovement = prefs.getBoolean(MOVEMENT_PREF_KEY, true);

        return this;
    }
}
