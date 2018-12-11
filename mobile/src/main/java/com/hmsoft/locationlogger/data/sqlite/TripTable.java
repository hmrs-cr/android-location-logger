package com.hmsoft.locationlogger.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.hmsoft.locationlogger.data.LocatrackLocation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class TripTable {
    public static final String TABLE_NAME = "trip";

    public static final String SQL_CREATE_TABLE  = "CREATE TABLE " + TABLE_NAME +
                                                   " (id INTEGER PRIMARY KEY, startLocation INTEGER, endLocation INTEGER)";

    private static final String[] selection = new String[2];
    private static final String[] columns = new String[1];

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

        public static Trip createTrip(long startTimeStamp, long endTimeStamp) {
            Trip trip = new Trip(startTimeStamp, endTimeStamp);
            return trip;
        }

        private Trip(long startTimeStamp, long endTimeStamp) {
            this.startTimeStamp = startTimeStamp;
            this.endTimeStamp = endTimeStamp;

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
            return "*Duration:* " + this.duration + "\n" +
                    "*Max Speed:* " + this.maxSpeed + "km/h\n" +
                    "*Avg Speed:* " +  this.avgSpeed + "km/h\n" +
                    "*Max Altitude:* " + this.maxAltitude + "msnm\n" +
                    "*Min Altitude:* " + this.minAltitude + "msnm\n" +
                    "*Points:* " + this.pointNumber;
        }
    }

    private static long getLocationTimeStamp(long fromTimeStamp, String event) {
        Helper helper = Helper.getInstance();


        selection[0] = event;
        selection[1] = String.valueOf(fromTimeStamp);
        columns[0] = LocationTable.COLUMN_NAME_TIMESTAMP;

        SQLiteDatabase database = helper.getReadableDatabase();
        Cursor cursor = database.query(LocationTable.TABLE_NAME, columns,
                LocationTable.COLUMN_NAME_EVENT + " = ? AND " + LocationTable.COLUMN_NAME_TIMESTAMP + "< ?",
                selection, null, null, LocationTable.COLUMN_NAME_TIMESTAMP + " DESC", "1");

        long result = 0;
        if(cursor.moveToFirst()) {
            result = cursor.getLong(0);
        }

        cursor.close();

        return result;
    }

    public static long insertTrip(long startLocation, long endLocation) {
        Helper helper = Helper.getInstance();
        ContentValues values = new ContentValues();
        values.put("startLocation", startLocation);
        values.put("endLocation", endLocation);
        return helper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null,  values,
                SQLiteDatabase.CONFLICT_IGNORE);
    }
  
    public static Trip getTrip(long fromTimeStamp, boolean isEndTimeStamp) {
        if(fromTimeStamp == 0) {
            fromTimeStamp = Long.MAX_VALUE;
        }

        long endTimeStamp = isEndTimeStamp ? fromTimeStamp : getLocationTimeStamp(fromTimeStamp, LocatrackLocation.EVENT_STOP);
        if(endTimeStamp > 0) {
            long startTimeStamp = getLocationTimeStamp(endTimeStamp, LocatrackLocation.EVENT_START);
            if(startTimeStamp > 0) {
                return Trip.createTrip(startTimeStamp, endTimeStamp);
            }
        }
        return null;
    }

    public static Trip insertTrip(long fromTimeStamp, boolean isEndTimeStamp) {

        Trip trip = getTrip(fromTimeStamp, isEndTimeStamp);

        while(trip != null && trip.pointNumber == 0) {
            trip = getTrip(trip.startTimeStamp, false);
        }

        if(trip != null) {
            insertTrip(trip.startTimeStamp, trip.endTimeStamp);
        }

        return trip;
    }
}
