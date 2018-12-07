package com.hmsoft.locationlogger.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

public class GeocoderTable {
    public static final String TABLE_NAME = "geocoder";

    public static final String SQL_DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;


    public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
    public static final String COLUMN_NAME_LATITUDE = "latitude";
    public static final String COLUMN_NAME_LONGITUDE = "longitude";
    public static final String COLUMN_NAME_ADDRESS = "address";

    public static final String[] SQL_CREATE_INDICES = new String[]{
            "CREATE UNIQUE INDEX idx_latlong ON " + TABLE_NAME + " (" + COLUMN_NAME_LATITUDE +
                    Helper.COMMA_SEP + COLUMN_NAME_LONGITUDE + ")"
    };

    private static final String[] QUERY_COLUMNS = new String[] {
            COLUMN_NAME_ADDRESS
    };

    private static final String[] queryValues = new String[2];
    private static final ContentValues insertValues = new ContentValues(4);

    public static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    COLUMN_NAME_TIMESTAMP + Helper.TYPE_INTEGER + Helper.TYPE_PRIMARY_KEY + Helper.COMMA_SEP +
                    COLUMN_NAME_LATITUDE + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    COLUMN_NAME_LONGITUDE + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    COLUMN_NAME_ADDRESS + Helper.TYPE_TEXT +
                    ")";


    public static  void saveAddress(long ts, double latitude, double longitude,
                                    String address) {

        insertValues.put(COLUMN_NAME_TIMESTAMP, ts);
        insertValues.put(COLUMN_NAME_LATITUDE, latitude);
        insertValues.put(COLUMN_NAME_LONGITUDE, longitude);
        insertValues.put(COLUMN_NAME_ADDRESS, address);

        Helper helper = Helper.getInstance();
        helper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null, insertValues,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static String getAddress(double latitude, double longitude) {
        queryValues[0] = String.valueOf(latitude);
        queryValues[1] = String.valueOf(longitude);

        Helper helper = Helper.getInstance();
        Cursor cursor = helper.getReadableDatabase().query(TABLE_NAME, QUERY_COLUMNS,
                COLUMN_NAME_LATITUDE + " = ? AND " + COLUMN_NAME_LONGITUDE + " = ?",
                queryValues, null, null, null);

        if(cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            } finally {
                cursor.close();
            }
        }

        return null;
    }

}
