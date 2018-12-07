package com.hmsoft.locationlogger.data.sqlite;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class TripTable {
    public static final String TABLE_NAME = "trip";

    public static final String SQL_CREATE_TABLE  = "CREATE TABLE " + TABLE_NAME +
                                                   " (id INTEGER PRIMARY KEY, startLocation INTEGER, endLocation INTEGER)";

    private static final String[] selection = new String[2];
    private static final String[] columns = new String[1];

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

    public static long createTrip(long fromTimeStamp) {
        if(fromTimeStamp == 0) {
            fromTimeStamp = Long.MAX_VALUE;
        }

        long endTimeStamp = getLocationTimeStamp(fromTimeStamp, "stop");
        long startTimeStamp = getLocationTimeStamp(endTimeStamp, "start");

        // TODO: Insert in table

        return startTimeStamp;
    }
}
