package com.hmsoft.locationlogger.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.location.Location;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FuelLogTable {
    public static final String TABLE_NAME = "fuelLog";

    public static final String SQL_DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;


    public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
    public static final String COLUMN_NAME_LOCATION_ID = "locationId";
    public static final String COLUMN_NAME_ODO_VALUE = "odoVal";
    public static final String COLUMN_NAME_SPEND_AMOUNT = "spendAmount";

    /*public static final String[] SQL_CREATE_INDICES = new String[]{
            "CREATE UNIQUE INDEX idx_latlong ON " + TABLE_NAME + " (" + COLUMN_NAME_LATITUDE +
                    Helper.COMMA_SEP + COLUMN_NAME_LONGITUDE + ")"
    };*/

    private static final String[] QUERY_COLUMNS = new String[] {
            COLUMN_NAME_TIMESTAMP,
            COLUMN_NAME_ODO_VALUE,
            COLUMN_NAME_SPEND_AMOUNT
    };

    private static final String[] queryValues = new String[2];
    private static final ContentValues insertValues = new ContentValues(4);

    public static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    COLUMN_NAME_TIMESTAMP + Helper.TYPE_INTEGER + Helper.TYPE_PRIMARY_KEY + Helper.COMMA_SEP +
                    COLUMN_NAME_LOCATION_ID + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    COLUMN_NAME_ODO_VALUE + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    COLUMN_NAME_SPEND_AMOUNT + Helper.TYPE_TEXT +
                    ")";


    public static  int logFuel(Helper helper, Location location, int odoValue, double spendAmount) {

        insertValues.put(COLUMN_NAME_TIMESTAMP, System.currentTimeMillis());
        insertValues.put(COLUMN_NAME_LOCATION_ID, location != null ? location.getTime() : System.currentTimeMillis());
        insertValues.put(COLUMN_NAME_ODO_VALUE, odoValue);
        insertValues.put(COLUMN_NAME_SPEND_AMOUNT, spendAmount);
        helper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null, insertValues,
                SQLiteDatabase.CONFLICT_REPLACE);

        return getCount(helper);
    }

    public static class FuelLog {
        public final Date date;
        public final int odoValue;
        public final int amountSpend;

        public FuelLog(Cursor cursor) {
            long time = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIMESTAMP));
            date = new Date(time);
            odoValue = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_ODO_VALUE));
            amountSpend = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_SPEND_AMOUNT));
        }

        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd KK:mm:ss a", Locale.US);

            stringBuilder.append(dateFormat.format(date)).append("  ")
                    .append(odoValue).append("km  ")
                    .append(amountSpend).append(" CRC");


            return stringBuilder.toString();
        }
    }

    public static FuelLog[] getLogs(Helper helper) {
        Cursor cursor = helper.getReadableDatabase().query(TABLE_NAME, QUERY_COLUMNS,
                null, null, null, null, null);

        if(cursor != null) {
            try {
                int i = 0;
                FuelLog[] result = new FuelLog[cursor.getCount()];
                while (cursor.moveToNext()) {
                    result[i++] = new FuelLog(cursor);
                }
                return result;
            } finally {
                cursor.close();
            }
        }

        return new FuelLog[0];
    }

    public static int getCount(Helper helper) {
        final String ALL_COUNT_QUERY = "SELECT Count(*) FROM " + TABLE_NAME;

        Cursor cursor = helper.getReadableDatabase().rawQuery(ALL_COUNT_QUERY, null);
        if(cursor != null) {
            try	{
                if(cursor.moveToFirst()) {
                    return cursor.getInt(0);
                }
            }
            finally	{
                cursor.close();
            }
        }
        return 0;
    }

    public static String getAddress(Helper helper, double latitude, double longitude) {
        queryValues[0] = String.valueOf(latitude);
        queryValues[1] = String.valueOf(longitude);

       /* Cursor cursor = helper.getReadableDatabase().query(TABLE_NAME, QUERY_COLUMNS,
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
        }*/

        return null;
    }

}
