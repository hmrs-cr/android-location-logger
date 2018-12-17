package com.hmsoft.locationlogger.data;

import android.location.Location;
import android.os.Bundle;


public class LocatrackLocation extends Location {

    public static final String EVENT_START = "start";
    public static final String EVENT_MOVEMENT_START = "movement/" + EVENT_START;
    public static final String EVENT_RESTART = "re" + EVENT_START;

    public static final String EVENT_STOP = "stop";
    public static final String EVENT_MOVEMENT_STOP = "movement/" + EVENT_STOP;
    public static final String EVENT_RESTOP = "re" + EVENT_STOP;

    public static final String EVENT_LOW_BATTERY = "low-battery";

    public String event = "";
    public String extraInfo;
    public int batteryLevel = -1;
    public long uploadTime;

    public LocatrackLocation(String provider) {
        super(provider);
    }

    public LocatrackLocation(Location location) {
        super(location);
    }

    public LocatrackLocation(LocatrackLocation location) {
        super(location);
        event = location.event;
        batteryLevel = location.batteryLevel;
        uploadTime = location.uploadTime;
    }

    @Override
    public Bundle getExtras() {
        if(super.getExtras() == null) {
            super.setExtras(new Bundle());
        }
        return super.getExtras();
    }
}
