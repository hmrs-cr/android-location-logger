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
    public static final String COLUMN_NAME_PRICE_PER_LITRE = "pricePerLitre";


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
    private static final ContentValues insertValues = new ContentValues(5);

    public static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    COLUMN_NAME_TIMESTAMP + Helper.TYPE_INTEGER + Helper.TYPE_PRIMARY_KEY + Helper.COMMA_SEP +
                    COLUMN_NAME_LOCATION_ID + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    COLUMN_NAME_ODO_VALUE + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    COLUMN_NAME_SPEND_AMOUNT + Helper.TYPE_TEXT +
                    COLUMN_NAME_PRICE_PER_LITRE + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    ")";

    public static final String SQL_DROP_VIEW = "DROP VIEW " + VIEW_NAME;
    public static final String SQL_CREATE_VIEW = "CREATE VIEW IF NOT EXISTS " + VIEW_NAME + " AS " +
            "SELECT fl." + COLUMN_NAME_TIMESTAMP + ", fl." + COLUMN_NAME_ODO_VALUE + ", fl." + COLUMN_NAME_SPEND_AMOUNT +
            ", l." + LocationTable.COLUMN_NAME_LATITUDE + ", l." + LocationTable.COLUMN_NAME_LONGITUD + " FROM " + TABLE_NAME + " AS fl" +
            " LEFT JOIN " + LocationTable.TABLE_NAME + " AS l ON l." + LocationTable.COLUMN_NAME_TIMESTAMP + " = fl." + COLUMN_NAME_LOCATION_ID +
            " OR (l." + LocationTable.COLUMN_NAME_TIMESTAMP + " BETWEEN fl." + COLUMN_NAME_LOCATION_ID + "-10000 AND fl." + COLUMN_NAME_LOCATION_ID + "+60000 and l." + LocationTable.COLUMN_NAME_EVENT + "='start')" +
            " GROUP BY fl." + COLUMN_NAME_TIMESTAMP;

    public static final String ADD_PRICE_PER_LITRE_COLUMUN = "ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COLUMN_NAME_PRICE_PER_LITRE + Helper.TYPE_REAL;
    public static final String UPDATE_PRICE_PER_LITRE_COLUMUN = "UPDATE " + TABLE_NAME + " SET " + COLUMN_NAME_PRICE_PER_LITRE + "=586 WHERE " + COLUMN_NAME_TIMESTAMP + " < 1540353004898 AND " + COLUMN_NAME_PRICE_PER_LITRE + " IS NULL";

    public static class Statics {
        public final int km;
        public final double litres;
        public final double avg;

        public final Date startDate;
        public final Date endDate;

        Statics(int km, double litres, Date startDate, Date endDate) {
            this.km = km;
            this.litres = Math.round(litres * 100) / 100;
            this.avg = Math.round((km/litres) * 100) / 100;

            this.startDate = startDate;
            this.endDate = endDate;
        }
    }

    public static class FuelLog {
        public final Date date;
        public final int odoValue;
        public final int amountSpend;

        public final double lat;
        public final double lon;

        FuelLog(Cursor cursor) {
            long time = cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIMESTAMP));
            date = new Date(time);
            odoValue = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_ODO_VALUE));
            amountSpend = cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_SPEND_AMOUNT));

            lat = cursor.getDouble(cursor.getColumnIndex(LocationTable.COLUMN_NAME_LATITUDE));
            lon = cursor.getDouble(cursor.getColumnIndex(LocationTable.COLUMN_NAME_LONGITUD));
        }

        private static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss", Locale.US);
        @Override
        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();


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

    public static  long logFuel(Helper helper, Location location, int odoValue, double spendAmount, double pricePerLitre) {

        insertValues.put(COLUMN_NAME_TIMESTAMP, System.currentTimeMillis());
        insertValues.put(COLUMN_NAME_LOCATION_ID, location != null ? location.getTime() : System.currentTimeMillis());
        insertValues.put(COLUMN_NAME_ODO_VALUE, odoValue);
        insertValues.put(COLUMN_NAME_SPEND_AMOUNT, spendAmount);
        insertValues.put(COLUMN_NAME_PRICE_PER_LITRE, pricePerLitre);
        helper.getWritableDatabase().insertWithOnConflict(TABLE_NAME, null, insertValues,
                SQLiteDatabase.CONFLICT_REPLACE);

        return getCount(helper);
    }

    public static void delete(Helper helper, long id) {
        helper.getWritableDatabase().execSQL("DELETE FROM " + TABLE_NAME + " WHERE " + COLUMN_NAME_TIMESTAMP + " = " + id);
    }

    public static FuelLog getById(Helper helper, long id) {

        Cursor cursor = helper.getReadableDatabase().query(VIEW_NAME, QUERY_COLUMNS,
                COLUMN_NAME_TIMESTAMP + " = " + id, null, null, null, null, null);

        if(cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return new FuelLog(cursor);
                }
            } finally {
                cursor.close();
            }
        }
        return null;
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

    public static Statics getMostRecentStatics(Helper helper) {

        final String[] QUERY_COLUMNS = new String[] {
                COLUMN_NAME_ODO_VALUE,
                COLUMN_NAME_PRICE_PER_LITRE,
                COLUMN_NAME_SPEND_AMOUNT,
                COLUMN_NAME_TIMESTAMP
        };

        Cursor cursor = helper.getReadableDatabase().query(TABLE_NAME, QUERY_COLUMNS,
                null, null, null, null,
                COLUMN_NAME_TIMESTAMP + " DESC ", "2");

        if(cursor != null) {
            try	{
                int currentOdoValue = 0;
                int currentPricePerLitre = 0;
                int currentAmount = 0;
                int prevOdoValue = 0;

                long currentDate = 0;
                long prevDate = 0;

                if(cursor.moveToFirst()) {
                    currentOdoValue = cursor.getInt(0);
                    currentPricePerLitre = cursor.getInt(1);
                    currentAmount = cursor.getInt(2);
                    currentDate = cursor.getLong(3);
                }
                if(cursor.moveToNext()) {
                    prevOdoValue = cursor.getInt(0);
                    prevDate = cursor.getLong(3);
                }

                int km = currentOdoValue - prevOdoValue;
                double litres = currentAmount / currentPricePerLitre;

                return new Statics(km, litres, new Date(prevDate), new Date(currentDate));
            }
            finally	{
                cursor.close();
            }
        }

        return null;
    }

    public static double getAvgConsuption(Helper helper) {
        final String query = "SELECT SUM(" + COLUMN_NAME_SPEND_AMOUNT +") / (MAX(" +
                COLUMN_NAME_ODO_VALUE + ") - MIN(" + COLUMN_NAME_ODO_VALUE + ")) From " + TABLE_NAME;

        double average = helper.getDoubleScalar(query);
        double rounded = Math.round(average * 100);
        return rounded / 100;
    }

    public static long getCount(Helper helper) {
        final String ALL_COUNT_QUERY = "SELECT Count(*) FROM " + TABLE_NAME;
        return Math.round(helper.getDoubleScalar(ALL_COUNT_QUERY));
    }

}

