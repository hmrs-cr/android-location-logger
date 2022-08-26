package com.hmsoft.locationlogger.data.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import com.hmsoft.locationlogger.LocationLoggerApp;
import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Gpx;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.data.LocationSet;
import com.hmsoft.locationlogger.data.LocatrackLocation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class TripTable {
    private static final String TAG = "TripTable";

    public static final String TABLE_NAME = "trip";
    public static final String VIEW_NAME = TABLE_NAME + "View";
    public static final String DETAIL_VIEW_NAME = TABLE_NAME + "DetailView";

    public static final String SQL_CREATE_TABLE = "CREATE TABLE " + TABLE_NAME +
            " (id INTEGER PRIMARY KEY, startLocation INTEGER, endLocation INTEGER, distance INTEGER)";

    public static final String SQL_CREATE_VIEW = "CREATE VIEW " + VIEW_NAME + " AS " +
            "SELECT t.id,t.distance,t.startLocation AS startTimestamp, COALESCE(gfs.label, gs.address, 'Trip Start #' || t.id)  " +
            "AS startAddress, ls.latitude AS startLat,ls.longitude AS startLong,t.endLocation AS endTimestamp," +
            "COALESCE(gfe.label, ge.address, 'Trip End #' || t.id)  AS endAddress,le.latitude AS endLat,le.longitude AS endLong " +
            "FROM trip AS t JOIN location AS ls ON ls.timestamp=t.startLocation LEFT JOIN geofence AS gfs ON gfs.latitude BETWEEN  ls.latitude - (gfs.radio * 0.00001) AND  ls.latitude + (gfs.radio * 0.00001)  AND gfs.longitude BETWEEN  ls.longitude - (gfs.radio * 0.00001) AND  ls.longitude + (gfs.radio * 0.00001) " +
            "LEFT JOIN geocoder AS gs ON gs.latitude = ROUND(ls.latitude, 3) " +
            "AND gs.longitude = ROUND(ls.longitude, 3) JOIN location AS le ON le.timestamp=t.endLocation " +
            "LEFT JOIN geofence AS gfe ON gfe.latitude BETWEEN  le.latitude - (gfe.radio * 0.00001) AND  le.latitude + (gfe.radio * 0.00001)  AND gfe.longitude BETWEEN  le.longitude - (gfe.radio * 0.00001) AND  le.longitude + (gfe.radio * 0.00001)  " +
            "LEFT JOIN geocoder AS ge ON ge.latitude = ROUND(le.latitude, 3) AND ge.longitude = ROUND(le.longitude, 3) " +
            "ORDER BY t.endLocation DESC";

    public static final String SQL_CREATE_DETAIL_VIEW = "CREATE VIEW " + DETAIL_VIEW_NAME + " AS SELECT t.id, l.timestamp, " +
            "l.latitude, l.longitude, l.altitude, l.accuracy, l.speed, l.batteryLevel, l.event FROM trip AS t JOIN " +
            "location AS l ON l.timestamp BETWEEN t.startLocation AND t.endLocation";

    private static final String[] locationSelection = new String[2];
    private static final String[] tripSelection = new String[1];
    private static final String[] locationColumns = new String[1];
    private static final String[] tripColumns = new String[3];

    //public interface

    public static class Trip {
        public String id;
        public final double distance;
        public final String startAddress;
        public final String endAddress;
        public final Date endDate;
        public final double endLat;
        public final double endLong;

        public static Trip loadFromCursor(Cursor cursor) {
            String id = cursor.getString(0);
            String starAddress = cursor.getString(1);
            String endAddress = cursor.getString(2);
            Date endDate = new Date(cursor.getLong(3));
            float distance = cursor.getFloat(4);

            double endLat = cursor.getDouble(5);
            double endLong = cursor.getDouble(6);

            return new Trip(id, starAddress, endAddress, endDate, endLat, endLong, distance);
        }

        Trip(String id, String startAddress, String endAddress, Date endDate,
             double endLat, double endLong, float distance) {
            this.id = id;
            this.distance = distance;
            this.startAddress = startAddress;
            this.endDate = endDate;
            this.endAddress = endAddress;
            this.endLat = endLat;
            this.endLong = endLong;
        }

        void setId(String id) {
            this.id = id;
        }

        public LocationSet getLocations() {
            Helper helper = Helper.getInstance();
            SQLiteDatabase database = helper.getReadableDatabase();
            tripSelection[0] = this.id;
            Cursor cursor = database.query(DETAIL_VIEW_NAME, null,
                    "id = ?", tripSelection, null, null, null, null);
            DatabaseLocationSet locationSet = new DatabaseLocationSet(cursor);
            locationSet.setAutoClose(false);
            return locationSet;
        }

        public LocationSet getLocations(long startTimestamp, long endTimestamp) {
            Helper helper = Helper.getInstance();
            SQLiteDatabase database = helper.getReadableDatabase();

            locationSelection[0] = String.valueOf(startTimestamp);
            locationSelection[1] = String.valueOf(endTimestamp);

            Cursor cursor = database.query(DETAIL_VIEW_NAME, null,
                    "timestamp BETWEEN ? AND ?", locationSelection, null, null, null, null);

            DatabaseLocationSet locationSet = new DatabaseLocationSet(cursor);
            locationSet.setAutoClose(false);

            return locationSet;
        }

        @Override
        public String toString() {
            String endAddress = this.endAddress.replace(", Costa Rica", "")
                    .replace("Unnamed Road, ", "");
            String date = Utils.dateFormat.format(this.endDate);

            //https://www.google.com/maps/dir/?api=1&origin=9.9898463,-84.09502069&destination=9.98742675,-84.15252516
            endAddress = "[" + endAddress + "](https://www.google.com/maps/search/?api=1&query=" + endLat + "," + endLong + ")";

            return "#" + id + ": " + date + " " + endAddress + " (" + (Math.round((distance / 1000.0) * 100.0) / 100.0) + ")";
        }
    }

    public static class TripDetail extends Trip {

        public final long startTimeStamp;
        public final long endTimeStamp;

        public final String duration; // HH:mm:ss        
        public final double maxSpeed; // km/h
        public final double avgSpeed; // km/h
        public final double maxAltitude;
        public final double minAltitude;
        public final int pointNumber;

        private String objectString = null;

        public static TripDetail createTrip(long startTimeStamp, long endTimeStamp, float distance) {
            TripDetail trip = new TripDetail(startTimeStamp, endTimeStamp, distance);
            return trip;
        }

        private TripDetail(long startTimeStamp, long endTimeStamp, float distance) {
            super("", "","", null, -1,-1, distance);
            this.startTimeStamp = startTimeStamp;
            this.endTimeStamp = endTimeStamp;

            SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");

            Date date = new Date(endTimeStamp - startTimeStamp);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            this.duration = format.format(date);

            Helper helper = Helper.getInstance();

            String query = "SELECT MAX(" + LocationTable.COLUMN_NAME_SPEED + "), AVG(" + LocationTable.COLUMN_NAME_SPEED +
                    "), MAX(" + LocationTable.COLUMN_NAME_ALTITUDE + "), MIN(" + LocationTable.COLUMN_NAME_ALTITUDE + "), COUNT(1)  FROM " +
                    LocationTable.TABLE_NAME + " WHERE " + LocationTable.COLUMN_NAME_TIMESTAMP + " BETWEEN " +
                    this.startTimeStamp + " AND " + this.endTimeStamp + " AND " + LocationTable.COLUMN_NAME_SPEED + " > 0";

            double maxSpeed = 0;
            double avgSpeed = 0;
            double maxAltitude = 0;
            double minAltitude = 0;
            int count = 0;

            Cursor cursor = helper.getReadableDatabase().rawQuery(query, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        count = cursor.getInt(4);
                        if (count > 0) {
                            maxSpeed = cursor.getDouble(0) * 3.6;
                            avgSpeed = cursor.getDouble(1) * 3.6;
                            maxAltitude = cursor.getDouble(2);
                            minAltitude = cursor.getDouble(3);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }

            this.maxSpeed = maxSpeed;
            this.avgSpeed = avgSpeed;
            this.maxAltitude = maxAltitude;
            this.minAltitude = minAltitude;
            this.pointNumber = count;
        }

        @Override
        public String toString() {
            if (objectString == null) {
                Context context = LocationLoggerApp.getContext();
                
                double constSpeed = (this.distance / ((endTimeStamp - startTimeStamp) / 1000)) * 3.6;
                objectString = context.getString(R.string.str_duration) + this.duration + "\n" +
                        context.getString(R.string.str_distance) + (Math.round((this.distance / 1000.0) * 100.0) / 100.0) + "\n" +
                        context.getString(R.string.str_max_speed) + (Math.round(this.maxSpeed * 100.0) / 100.0) + "\n" +
                        context.getString(R.string.str_avg_speed) + (Math.round(this.avgSpeed * 100.0) / 100.0) + " (" + (Math.round(constSpeed * 100.0) / 100.0) + ")\n" +
                        context.getString(R.string.str_max_altitude) + (Math.round(this.maxAltitude * 100.0) / 100.0) + "\n" +
                        context.getString(R.string.str_min_altitude) + (Math.round(this.minAltitude * 100.0) / 100.0) + "\n" +
                        context.getString(R.string.str_points) + this.pointNumber;
            }
            return objectString;
        }

        public String toGpxString() {
            String gpxName = "Trip " + Gpx.df.format(new Date(this.endTimeStamp));
            String gpxDesc = this.toString();
            LocationSet points;

            if(TextUtils.isEmpty(this.id)) {
                points = this.getLocations(this.startTimeStamp, this.endTimeStamp);
            } else {
                points = this.getLocations();
            }

            return Gpx.createGpx(points, gpxName, gpxDesc);
        }
    }

    private static long getLocationTimeStamp(long fromTimeStamp, String event) {
        Helper helper = Helper.getInstance();


        locationSelection[0] = event;
        locationSelection[1] = String.valueOf(fromTimeStamp);
        locationColumns[0] = LocationTable.COLUMN_NAME_TIMESTAMP;

        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor cursor = database.query(LocationTable.TABLE_NAME, locationColumns,
                LocationTable.COLUMN_NAME_EVENT + " = ? AND " + LocationTable.COLUMN_NAME_TIMESTAMP + "< ?",
                locationSelection, null, null, LocationTable.COLUMN_NAME_TIMESTAMP + " DESC", "1");

        long result = 0;
        if (cursor.moveToFirst()) {
            result = cursor.getLong(0);
        }

        cursor.close();

        return result;
    }

    public static long insertTrip(long startLocation, long endLocation, float distance) {
        Helper helper = Helper.getInstance();
        ContentValues values = new ContentValues();
        values.put("startLocation", startLocation);
        values.put("endLocation", endLocation);
        values.put("distance", distance);
        return helper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null, values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    public static TripDetail getTrip(long fromTimeStamp, boolean isEndTimeStamp, float distance) {
        if (fromTimeStamp == 0) {
            fromTimeStamp = Long.MAX_VALUE;
        }

        long endTimeStamp = isEndTimeStamp ? fromTimeStamp : getLocationTimeStamp(fromTimeStamp, LocatrackLocation.EVENT_STOP);
        if (endTimeStamp > 0) {
            long startTimeStamp = getLocationTimeStamp(endTimeStamp, LocatrackLocation.EVENT_START);
            if (startTimeStamp > 0) {
                return TripDetail.createTrip(startTimeStamp, endTimeStamp, distance);
            }
        }
        return null;
    }

    public static TripDetail insertTrip(long fromTimeStamp, float distance, boolean isEndTimeStamp) {

        TripDetail trip = getTrip(fromTimeStamp, isEndTimeStamp, distance);

        while (trip != null && trip.pointNumber == 0) {
            trip = getTrip(trip.startTimeStamp, false, distance);
        }

        if (trip != null) {

            if (trip.pointNumber < 5) {
                if (Logger.DEBUG) {
                    Logger.debug(TAG, "Not enough points in trip:" + trip.pointNumber);
                }
                return null;
            }

            long id = insertTrip(trip.startTimeStamp, trip.endTimeStamp, distance);
            trip.setId(String.valueOf(id));
        }

        return trip;
    }

    public static TripDetail getTripbyId(String id) {
        Helper helper = Helper.getInstance();

        if("last".equals(id)) {
            Trip[] trips = getTrips(1);
            if(trips.length == 1) {
                id = trips[0].id;
            }
        }

        tripColumns[0] = "startLocation";
        tripColumns[1] = "endLocation";
        tripColumns[2] = "distance";

        tripSelection[0] = id;

        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, tripColumns,
                "id = ?", tripSelection, null, null, null);

        try {
            if (cursor.moveToFirst()) {
                long start = cursor.getLong(0);
                long stop = cursor.getLong(1);
                float distance = cursor.getFloat(2);
                TripDetail trip = TripDetail.createTrip(start, stop, distance);
                trip.setId(id);
                return trip;
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    public static Trip[] getTrips(int limit) {

        String limitStr = null;
        if(limit == 0) {
            limit = 10;
        }

        if(limit > 0) {
            limitStr = String.valueOf(limit);

        }

        Helper helper = Helper.getInstance();
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor cursor = database.query(VIEW_NAME, new String[]
                        {"id", "startAddress", "endAddress", "endTimestamp", "distance", "endLat", "endLong"},
                null, null, null,
                null, null, limitStr);

        Trip[] result = new Trip[cursor.getCount()];
        int i = 0;
        while (cursor.moveToNext()) {
            Trip trip  =  Trip.loadFromCursor(cursor);
            result[i++] = trip;
        }

        cursor.close();

        return result;
    }
}
