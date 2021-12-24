package com.hmsoft.locationlogger.data;

import android.location.Location;
import android.os.Bundle;
import android.text.TextUtils;

import com.hmsoft.locationlogger.LocationLoggerApp;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class LocatrackLocation extends Location {

    public static final String EVENT_INFO = "info";
    public static final String EVENT_START = "start";
    public static final String EVENT_MOVEMENT_START = "movement/" + EVENT_START;
    public static final String EVENT_RESTART = "re" + EVENT_START;

    public static final String EVENT_STOP = "stop";
    public static final String EVENT_MOVEMENT_STOP = "movement/" + EVENT_STOP;
    public static final String EVENT_RESTOP = "re" + EVENT_STOP;

    public static final String EVENT_LOW_BATTERY = "low-battery";

    private final DateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss a",Locale.US);;
    private static LocatrackLocation sLastLocation;

    public String event = "";
    public String extraInfo;
    public int batteryLevel = -1;
    public long uploadTime;
    public String replyToMessageId;
    public String replyToId;

    public LocatrackLocation(String provider) {
        super(provider);
        sLastLocation = this;
    }

    public LocatrackLocation(Location location) {
        super(location);
        sLastLocation = this;
    }

    public LocatrackLocation(LocatrackLocation location) {
        super(location);
        event = location.event;
        batteryLevel = location.batteryLevel;
        uploadTime = location.uploadTime;
        sLastLocation = this;
    }

    @Override
    public Bundle getExtras() {
        if(super.getExtras() == null) {
            super.setExtras(new Bundle());
        }
        return super.getExtras();
    }

    public String getAddressLabel() {
        String address = Geocoder.getFromCache(this);
        if(TextUtils.isEmpty(address)) {
            address = Geocoder.getFromRemote(LocationLoggerApp.getContext(), this);
            if (!TextUtils.isEmpty(address)) {
                Geocoder.addToCache(this, address);
            }
        }
        if(TextUtils.isEmpty(address)) {
            address = this.getLatitude() + "," + this.getLongitude();
        }

        return address;
    }

    public static LocatrackLocation getLastLocation() {
        return sLastLocation;
    }

    public String getTimeString() {
        return sDateFormat.format(new Date(this.getTime()));
    }

    public String getAccuracyString() {
        return (Math.round(this.getAccuracy() * 100.0) / 100.0) + "m " + this.getProvider().charAt(0);
    }
}
