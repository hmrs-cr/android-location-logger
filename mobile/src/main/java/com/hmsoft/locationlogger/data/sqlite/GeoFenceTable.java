package com.hmsoft.locationlogger.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.hmsoft.locationlogger.common.Gpx;

public class GeoFenceTable {
    public static final String TABLE_NAME = "geofence";

    public static final String SQL_DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;

    public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
    public static final String COLUMN_NAME_LATITUDE = "latitude";
    public static final String COLUMN_NAME_LONGITUDE = "longitude";
    public static final String COLUMN_NAME_LABEL = "label";
    public static final String COLUMN_NAME_RADIO = "radio";

    private static final String RADIO_FACTOR = "0.00001";

    public static final String[] SQL_CREATE_INDICES = new String[]{
            "CREATE UNIQUE INDEX idx_" + TABLE_NAME + "_latlong ON " + TABLE_NAME + " (" + COLUMN_NAME_LATITUDE +
                    Helper.COMMA_SEP + COLUMN_NAME_LONGITUDE + ")"
    };

    private static final String[] QUERY_COLUMNS = new String[] {
            COLUMN_NAME_LABEL
    };

    private static final String[] queryValues = new String[4];
    private static final ContentValues insertValues = new ContentValues(5);

    public static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    COLUMN_NAME_TIMESTAMP + Helper.TYPE_INTEGER + Helper.TYPE_PRIMARY_KEY + Helper.COMMA_SEP +
                    COLUMN_NAME_LATITUDE + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    COLUMN_NAME_LONGITUDE + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    COLUMN_NAME_RADIO + Helper.TYPE_INTEGER + Helper.COMMA_SEP +
                    COLUMN_NAME_LABEL + Helper.TYPE_TEXT +
                    ")";


    public static  long saveGeofence(double latitude, double longitude, int radio, String label) {

        insertValues.put(COLUMN_NAME_TIMESTAMP, System.currentTimeMillis());
        insertValues.put(COLUMN_NAME_LATITUDE, latitude);
        insertValues.put(COLUMN_NAME_LONGITUDE, longitude);
        insertValues.put(COLUMN_NAME_RADIO, radio);
        insertValues.put(COLUMN_NAME_LABEL, label);

        Helper helper = Helper.getInstance();
        return helper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null, insertValues,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public static String getLabel(double latitude, double longitude) {
        queryValues[0] = String.valueOf(latitude);
        queryValues[1] = queryValues[0];
        queryValues[2] = String.valueOf(longitude);
        queryValues[3] = queryValues[2];

        Helper helper = Helper.getInstance();
        Cursor cursor = helper.getReadableDatabase().query(TABLE_NAME, QUERY_COLUMNS,
                COLUMN_NAME_LATITUDE + " BETWEEN ? - (" + COLUMN_NAME_RADIO + " * " + RADIO_FACTOR + ") AND ? + (" + COLUMN_NAME_RADIO + " * "+ RADIO_FACTOR + ") AND " + COLUMN_NAME_LONGITUDE + " BETWEEN ? - (" + COLUMN_NAME_RADIO + " * " + RADIO_FACTOR + ") AND ? + (" + COLUMN_NAME_RADIO + " * " + RADIO_FACTOR + ")",
                queryValues, null, null, COLUMN_NAME_TIMESTAMP + " DESC", "1");

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

    public static String exportGpx() {
        StringBuilder sb = Gpx.createGpxStringBuilder();

        Helper helper = Helper.getInstance();
        Cursor cursor = helper.getReadableDatabase().query(TABLE_NAME, null,
                null,
                null, null, null, COLUMN_NAME_TIMESTAMP + " DESC");

        if(cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    boolean hasNext = true;
                    while (hasNext) {
                        double lat = cursor.getDouble(cursor.getColumnIndex(COLUMN_NAME_LATITUDE));
                        double lon = cursor.getDouble(cursor.getColumnIndex(COLUMN_NAME_LONGITUDE));
                        String name = cursor.getString(cursor.getColumnIndex(COLUMN_NAME_LABEL));
                        String description = "distance: " + cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_RADIO));
                        Gpx.addWayPoint(sb, lat, lon, name, description);
                        hasNext = cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }

        return Gpx.closeGpx(sb).toString();
    }
}
