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
    public static final String VIEW_NAME = TABLE_NAME + "View";

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
            COLUMN_NAME_SPEND_AMOUNT,
            LocationTable.COLUMN_NAME_LATITUDE,
            LocationTable.COLUMN_NAME_LONGITUD

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

    public static final String SQL_CREATE_VIEW = "CREATE VIEW " + VIEW_NAME + " AS " +
            "SELECT fl." + COLUMN_NAME_TIMESTAMP + ", fl." + COLUMN_NAME_ODO_VALUE + ", fl." + COLUMN_NAME_SPEND_AMOUNT +
            ", l." + LocationTable.COLUMN_NAME_LATITUDE + ", l." + LocationTable.COLUMN_NAME_LONGITUD + " FROM " + TABLE_NAME + " AS fl" +
            " LEFT JOIN " + LocationTable.TABLE_NAME + " AS l ON l." + LocationTable.COLUMN_NAME_TIMESTAMP + " = fl." + COLUMN_NAME_LOCATION_ID;

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

        public final double lat;
        public final double lon;

        public FuelLog(Cursor cursor) {
            long time = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIMESTAMP));
            date = new Date(time);
            odoValue = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_ODO_VALUE));
            amountSpend = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_SPEND_AMOUNT));

            lat = cursor.getDouble(cursor.getColumnIndex(LocationTable.COLUMN_NAME_LATITUDE));
            lon = cursor.getDouble(cursor.getColumnIndex(LocationTable.COLUMN_NAME_LONGITUD));
        }

        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd KK:mm:ss a", Locale.US);

            String datestr = dateFormat.format(date);

            if(lat != 0 && lon != 0) {
                datestr = "[" + datestr + "](https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon + ")";
            }

            stringBuilder.append(datestr).append("  ")
                    .append(odoValue).append("km  ")
                    .append(amountSpend).append(" CRC");


            return stringBuilder.toString();
        }
    }

    public static FuelLog[] getLogs(Helper helper, int limit) {
        String slimit = limit > 0 ? String.valueOf(limit) : null;
        Cursor cursor = helper.getReadableDatabase().query(VIEW_NAME, QUERY_COLUMNS,
                null, null, null, null,
                COLUMN_NAME_TIMESTAMP + " DESC ", slimit);

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

}
