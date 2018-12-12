package com.hmsoft.locationlogger.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Pair;

import com.hmsoft.locationlogger.data.LocatrackLocation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class TripTable {
    public static final String TABLE_NAME = "trip";
    public static final String VIEW_NAME = TABLE_NAME + "View";

    public static final String SQL_CREATE_TABLE  = "CREATE TABLE " + TABLE_NAME +
                                                   " (id INTEGER PRIMARY KEY, startLocation INTEGER, endLocation INTEGER)";

    public static final String SQL_CREATE_VIEW  = "CREATE VIEW " + VIEW_NAME + " AS SELECT t.id, " +
            "COALESCE(g.address, 'Trip #' || t.id) FROM trip AS t JOIN location AS l ON l.timestamp=t.endLocation " +
            "LEFT JOIN geocoder AS g ON g.latitude = ROUND(l.latitude, 3) AND g.longitude = ROUND(l.longitude, 3) " +
            "ORDER BY endLocation DESC LIMIT 16";

    private static final String[] locationSelection = new String[2];
    private static final String[] tripSelection = new String[1];
    private static final String[] locationColumns = new String[1];
    private static final String[] tripColumns = new String[2];

    public static class Trip {

        public final long startTimeStamp;
        public final long endTimeStamp;

        public final String duration; // HH:mm:ss
        public final int length; // Meters
        public final double maxSpeed; // km/h
        public final double avgSpeed; // km/h
        public final double maxAltitude;
        public final double minAltitude;
        public final int pointNumber;
        public final double  distance;

        static Trip createTrip(long startTimeStamp, long endTimeStamp, float distance) {
            Trip trip = new Trip(startTimeStamp, endTimeStamp, distance);
            return trip;
        }

        private Trip(long startTimeStamp, long endTimeStamp, float distance) {
            this.startTimeStamp = startTimeStamp;
            this.endTimeStamp = endTimeStamp;
            this.distance = distance;

            SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");

            Date date = new Date(endTimeStamp - startTimeStamp);
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            this.duration = format.format(date);
            this.length = 0;

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
            if(cursor != null) {
                try	{
                    if(cursor.moveToFirst()) {
                        count = cursor.getInt(4);
                        if(count > 0) {
                            maxSpeed = cursor.getDouble(0) * 3.6;
                            avgSpeed = cursor.getDouble(1) * 3.6;
                            maxAltitude = cursor.getDouble(2);
                            minAltitude = cursor.getDouble(3);
                        }
                    }
                }
                finally	{
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

            return "Duration: " + this.duration + "\n" +
                    "Distance: " + (Math.round((this.distance / 1000) * 100.0) / 100.0) + "\n" +
                    "Max Speed: " + (Math.round(this.maxSpeed * 100.0) / 100.0) + "\n" +
                    "Avg Speed: " +  (Math.round(this.avgSpeed * 100.0) / 100.0) + "\n" +
                    "Max Altitude: " + (Math.round(this.maxAltitude * 100.0) / 100.0) + "\n" +
                    "Min Altitude: " + (Math.round(this.minAltitude * 100.0) / 100.0) + "\n" +
                    "Points: " + this.pointNumber;
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
        if(cursor.moveToFirst()) {
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
        return helper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null,  values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }
  
    public static Trip getTrip(long fromTimeStamp, boolean isEndTimeStamp, float distance) {
        if(fromTimeStamp == 0) {
            fromTimeStamp = Long.MAX_VALUE;
        }

        long endTimeStamp = isEndTimeStamp ? fromTimeStamp : getLocationTimeStamp(fromTimeStamp, LocatrackLocation.EVENT_STOP);
        if(endTimeStamp > 0) {
            long startTimeStamp = getLocationTimeStamp(endTimeStamp, LocatrackLocation.EVENT_START);
            if(startTimeStamp > 0) {
                return Trip.createTrip(startTimeStamp, endTimeStamp, distance);
            }
        }
        return null;
    }

    public static Trip insertTrip(long fromTimeStamp, float distance, boolean isEndTimeStamp) {

        Trip trip = getTrip(fromTimeStamp, isEndTimeStamp, distance);

        while(trip != null && trip.pointNumber == 0) {
            trip = getTrip(trip.startTimeStamp, false, distance);
        }

        if(trip != null) {
            insertTrip(trip.startTimeStamp, trip.endTimeStamp, distance);
        }

        return trip;
    }

    public static Trip getTripbyId(String id) {
        Helper helper = Helper.getInstance();

        tripColumns[0] = "startLocation";
        tripColumns[1] = "endLocation";
        //tripColumns[2] = "distance";

        tripSelection[0] = id;

        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor cursor = database.query(TABLE_NAME, tripColumns,
                 "id = ?", tripSelection, null, null, null);

        if(cursor.moveToFirst()) {
            long start = cursor.getLong(0);
            long stop = cursor.getLong(1);
            float distance = 0;//cursor.getFloat(2);
            return Trip.createTrip(start, stop, distance);
        }

        cursor.close();

        return null;
    }

    public static Pair[] getTrips() {
        Helper helper = Helper.getInstance();
        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor cursor = database.query(VIEW_NAME, null,null, null,
                null, null, null);

        Pair[] result = new Pair[cursor.getCount()];
        int i = 0;
        while (cursor.moveToNext()) {
            Pair<String, String> record = Pair.create(cursor.getString(0), cursor.getString(1));
            result[i++] = record;
        }

        cursor.close();

        return result;
    }
}
