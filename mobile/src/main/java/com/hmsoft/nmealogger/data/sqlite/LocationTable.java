package com.hmsoft.nmealogger.data.sqlite;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Bundle;
import android.text.TextUtils;

import com.hmsoft.nmealogger.common.Constants;
import com.hmsoft.nmealogger.common.Logger;
import com.hmsoft.nmealogger.data.LocationSet;

import java.util.Iterator;

public class LocationTable {

    private static final String TAG = "Location";

    private static final Object sWriteLock = new Object();

    private static final String CACHED_LOCATION_PROVIDER = "database";
    public static final String TABLE_NAME = "location";

    public static final String COLUMN_NAME_TIMESTAMP = "timestamp";
    public static final String COLUMN_NAME_LATITUDE = "latitude";
    public static final String COLUMN_NAME_LONGITUD = "longitude";
    public static final String COLUMN_NAME_ALTITUDE = "altitude";
    public static final String COLUMN_NAME_ACCURACY = "accuracy";
    public static final String COLUMN_NAME_SPEED = "speed";
    public static final String COLUMN_NAME_UPLOAD_DATE = "uploadDate";
    public static final String COLUMN_NAME_BATTERY_LEVEL = "batteryLevel";
    public static final String COLUMN_NAME_EVENT = "event";

    public static final String SQL_DROP_TABLE = "DROP TABLE IF EXISTS " + TABLE_NAME;

    public static final String[] SQL_CREATE_INDICES = new String[]{};
    public static final String SQL_CREATE_TABLE =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    COLUMN_NAME_TIMESTAMP + Helper.TYPE_INTEGER + Helper.TYPE_PRIMARY_KEY + Helper.COMMA_SEP +
                    COLUMN_NAME_LATITUDE + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    COLUMN_NAME_LONGITUD + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    COLUMN_NAME_ALTITUDE + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    COLUMN_NAME_ACCURACY + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    COLUMN_NAME_SPEED + Helper.TYPE_REAL + Helper.COMMA_SEP +
                    COLUMN_NAME_UPLOAD_DATE + Helper.TYPE_INTEGER  + Helper.COMMA_SEP +
                    COLUMN_NAME_BATTERY_LEVEL + Helper.TYPE_INTEGER  + Helper.COMMA_SEP +
                    COLUMN_NAME_EVENT + Helper.TYPE_TEXT  +
                    ")";

    public static final String INSERT_SQL = "INSERT OR IGNORE INTO " + TABLE_NAME + " (" +
            COLUMN_NAME_TIMESTAMP + Helper.COMMA_SEP +
            COLUMN_NAME_LATITUDE + Helper.COMMA_SEP +
            COLUMN_NAME_LONGITUD + Helper.COMMA_SEP +
            COLUMN_NAME_ALTITUDE + Helper.COMMA_SEP +
            COLUMN_NAME_ACCURACY + Helper.COMMA_SEP +
            COLUMN_NAME_SPEED + Helper.COMMA_SEP +
            COLUMN_NAME_BATTERY_LEVEL + Helper.COMMA_SEP +
            COLUMN_NAME_EVENT + Helper.COMMA_SEP +
            COLUMN_NAME_UPLOAD_DATE  + ") VALUES (?,?,?,?,?,?,?,?,?)";

    public static final String UPDATE_SQL = "UPDATE " + TABLE_NAME + " SET " +
            COLUMN_NAME_TIMESTAMP + "=?" + Helper.COMMA_SEP +
            COLUMN_NAME_LATITUDE  + "=?" + Helper.COMMA_SEP +
            COLUMN_NAME_LONGITUD  + "=?" + Helper.COMMA_SEP +
            COLUMN_NAME_ALTITUDE  + "=?" + Helper.COMMA_SEP +
            COLUMN_NAME_ACCURACY  + "=?" + Helper.COMMA_SEP +
            COLUMN_NAME_UPLOAD_DATE + "=?" + Helper.COMMA_SEP +
            COLUMN_NAME_BATTERY_LEVEL + "=?" + Helper.COMMA_SEP +
            COLUMN_NAME_EVENT + "=?" + Helper.COMMA_SEP +
            COLUMN_NAME_SPEED     + "=? WHERE " +
            COLUMN_NAME_TIMESTAMP + "=?";

    private static final String TIMESTAMP_WHERE_CONDITION = COLUMN_NAME_TIMESTAMP + " = ?";

    private static ContentValues sUpdateValues = new ContentValues(1);
    private static String[] sUpdateValuesValues = new String[1];
    private static ContentValues sInsertValues = new ContentValues(7);
    private static SQLiteStatement sInsertStatement = null;
    private static SQLiteStatement sUpdateStatement = null;
    private static Location sLastInsertedLocation = null;

    public static Location loadFromCursor(Cursor cursor) {
        Location location = new Location(CACHED_LOCATION_PROVIDER);
        location.setTime(cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_TIMESTAMP)));
        location.setLatitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_NAME_LATITUDE)));
        location.setLongitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_NAME_LONGITUD)));
        location.setAltitude(cursor.getDouble(cursor.getColumnIndex(COLUMN_NAME_ALTITUDE)));
        location.setAccuracy(cursor.getFloat(cursor.getColumnIndex(COLUMN_NAME_ACCURACY)));
        location.setSpeed(cursor.getFloat(cursor.getColumnIndex(COLUMN_NAME_SPEED)));
        Bundle extras = new Bundle();
        extras.putLong(Constants.EXTRA_UPLOAD_TIME, cursor.getLong(cursor.getColumnIndex(COLUMN_NAME_UPLOAD_DATE)));
        extras.putInt(BatteryManager.EXTRA_LEVEL, cursor.getInt(cursor.getColumnIndex(COLUMN_NAME_BATTERY_LEVEL)));
        extras.putString(Constants.NOTIFY_EVENT, cursor.getString(cursor.getColumnIndex(COLUMN_NAME_EVENT)));
        location.setExtras(extras);
        return location;
    }

    public static LocationSet getAllNotUploaded(Helper helper) {
        Cursor cursor = helper.getReadableDatabase().query(TABLE_NAME, null,
                COLUMN_NAME_UPLOAD_DATE + " = 0", null, null, null, COLUMN_NAME_TIMESTAMP, null);
        return new DatabaseLocationSet(cursor);
    }

    public static LocationSet getAllFromDate(Helper helper, long date) {
        sUpdateValuesValues[0] = String.valueOf(date);
        Cursor cursor = helper.getReadableDatabase().query(TABLE_NAME, null,
                COLUMN_NAME_TIMESTAMP + " > ?", sUpdateValuesValues, null, null, COLUMN_NAME_TIMESTAMP, null);
        return new DatabaseLocationSet(cursor);
    }

    public static void setUploadDate(Helper helper, Location location) {
        SQLiteDatabase writable = helper.getWritableDatabase();
        sUpdateValues.put(COLUMN_NAME_UPLOAD_DATE, System.currentTimeMillis());
        sUpdateValuesValues[0] = String.valueOf(location.getTime());
        writable.update(TABLE_NAME, sUpdateValues, TIMESTAMP_WHERE_CONDITION, sUpdateValuesValues);
    }

    public static int getCount(Helper helper, boolean includeNotUploadedOnly) {
        final String NOT_UPLOADED_COUNT_QUERY = "SELECT Count(*) FROM " + TABLE_NAME +
                " WHERE " + COLUMN_NAME_UPLOAD_DATE + " = 0";
        final String ALL_COUNT_QUERY = "SELECT Count(*) FROM " + TABLE_NAME;

        String query;
        if(includeNotUploadedOnly) {
            query = NOT_UPLOADED_COUNT_QUERY;
        } else {
            query = ALL_COUNT_QUERY;
        }
        Cursor cursor = helper.getReadableDatabase().rawQuery(query, null);
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

    public static Location getLast(Helper helper) {
        Cursor cursor = helper.getReadableDatabase().query(TABLE_NAME, null, null, null, null,
                null, COLUMN_NAME_TIMESTAMP + " DESC", "1");
        if(cursor != null) {
            try	{
                if(cursor.moveToFirst()) {
                    return loadFromCursor(cursor);
                }
            }
            finally	{
                cursor.close();
            }
        }
        return null;
    }

    public static long getLastTime(Helper helper) {
        Cursor cursor = helper.getReadableDatabase().query(TABLE_NAME,
                new String[]{COLUMN_NAME_TIMESTAMP}, null, null, null,
                null, COLUMN_NAME_TIMESTAMP + " DESC", "1");

        if(cursor != null) {
            try	{
                if(cursor.moveToFirst()) {
                    return cursor.getLong(0);
                }
            }
            finally	{
                cursor.close();
            }
        }
        return 0;
    }

    public static synchronized long saveToDatabase(Helper helper, Location location, float minDistance) {

        if(sLastInsertedLocation == null) {
            sLastInsertedLocation = getLast(helper);
        }

        long c = 1;

        long uploadDate = 0;
        int batteryLevel = -1;
        String event = "";
        Bundle extras = location.getExtras();
        if(extras != null) {
            uploadDate = extras.getLong(Constants.EXTRA_UPLOAD_TIME, 0L);
            batteryLevel = extras.getInt(BatteryManager.EXTRA_LEVEL, -1);
            event = extras.getString(Constants.NOTIFY_EVENT);
            if(event == null) event = "";
        } else {
            location.setExtras(new Bundle());
        }

        boolean update = ("".equals(event)) && (sLastInsertedLocation != null) &&
                TextUtils.isEmpty(sLastInsertedLocation.getExtras().getString(Constants.NOTIFY_EVENT))
                && (sLastInsertedLocation.distanceTo(location) < minDistance);

        if(sInsertStatement != null) {
            SQLiteStatement statement;
            if(update) {
                sUpdateStatement.bindLong(1, location.getTime());
                sUpdateStatement.bindDouble(2, location.getLatitude());
                sUpdateStatement.bindDouble(3, location.getLongitude());
                sUpdateStatement.bindDouble(4, location.getAltitude());
                sUpdateStatement.bindDouble(5, location.getAccuracy());
                sUpdateStatement.bindDouble(6, uploadDate);
                sUpdateStatement.bindLong(7, batteryLevel);
                sUpdateStatement.bindString(8, event);
                sUpdateStatement.bindDouble(9, location.getSpeed());
                sUpdateStatement.bindLong(10, sLastInsertedLocation.getTime());
                sLastInsertedLocation.setTime(location.getTime());
                statement = sUpdateStatement;
            } else {
                sInsertStatement.bindLong(1, location.getTime());
                sInsertStatement.bindDouble(2, location.getLatitude());
                sInsertStatement.bindDouble(3, location.getLongitude());
                sInsertStatement.bindDouble(4, location.getAltitude());
                sInsertStatement.bindDouble(5, location.getAccuracy());
                sInsertStatement.bindDouble(6, location.getSpeed());
                sInsertStatement.bindLong(7, batteryLevel);
                sInsertStatement.bindString(8, event);
                sInsertStatement.bindLong(9, uploadDate);
                statement = sInsertStatement;
                sLastInsertedLocation = location;
            }

            if(Logger.DEBUG) {
                c = statement.executeInsert();
            } else {
                statement.execute();
            }
        } else {
            sInsertValues.put(COLUMN_NAME_TIMESTAMP, location.getTime());
            sInsertValues.put(COLUMN_NAME_LATITUDE, location.getLatitude());
            sInsertValues.put(COLUMN_NAME_LONGITUD, location.getLongitude());
            sInsertValues.put(COLUMN_NAME_ALTITUDE, location.getAltitude());
            sInsertValues.put(COLUMN_NAME_ACCURACY, location.getAccuracy());
            sInsertValues.put(COLUMN_NAME_SPEED, location.getSpeed());
            sInsertValues.put(COLUMN_NAME_UPLOAD_DATE, uploadDate);

            SQLiteDatabase db = helper.getWritableDatabase();

            if(update) {
                sUpdateValuesValues[0] = String.valueOf(sLastInsertedLocation.getTime());
                c = db.updateWithOnConflict(TABLE_NAME, sInsertValues, TIMESTAMP_WHERE_CONDITION,
                        sUpdateValuesValues, SQLiteDatabase.CONFLICT_IGNORE);
                sLastInsertedLocation.setTime(location.getTime());
            } else {
                c = db.insertWithOnConflict(TABLE_NAME, null, sInsertValues, SQLiteDatabase.CONFLICT_IGNORE);
                if(c > 0) {
                    sLastInsertedLocation = location;
                }
            }
        }

        if (Logger.DEBUG) {
            if(Logger.DEBUG) Logger.debug(TAG, "Location %s %d", update ? "updated" : "inserted", c);
        }

        return c;
    }

    public static synchronized void prepareDmlStatements(Helper helper) {
        SQLiteDatabase db = helper.getWritableDatabase();
        if(sInsertStatement == null) {
            sInsertStatement = db.compileStatement(INSERT_SQL);
        }
        if(sUpdateStatement == null) {
            sUpdateStatement = db.compileStatement(UPDATE_SQL);
        }
    }

    public static synchronized void startBulkInsert(Helper helper) {
        SQLiteDatabase db = helper.getWritableDatabase();
        if(sInsertStatement == null) {
            if(!db.inTransaction()) {
                db.beginTransaction();
            }
            sInsertStatement = db.compileStatement(INSERT_SQL);
            sUpdateStatement = db.compileStatement(UPDATE_SQL);
        }
    }

    public static synchronized void stopBulkInsert(Helper helper) {
        SQLiteDatabase db = helper.getWritableDatabase();
        if(sInsertStatement != null) {
            if(db.inTransaction()) {
                db.setTransactionSuccessful();
                db.endTransaction();
            }
            sInsertStatement = null;
            sUpdateStatement = null;
        }
    }

    public static Location getFromTimestamp(Helper helper, long timestamp, long timeRange) {
        if(timeRange == 0) timeRange = 1000 * 60 * 15;
        String select = String.format("SELECT * FROM %s WHERE %s <= %d AND %s >= %d ORDER BY ABS(%d - %s) LIMIT 1",
                TABLE_NAME, COLUMN_NAME_TIMESTAMP, timestamp + timeRange, COLUMN_NAME_TIMESTAMP,
                timestamp - timeRange, timestamp, COLUMN_NAME_TIMESTAMP);

        Cursor cursor = helper.getReadableDatabase().rawQuery(select, null);
        if(cursor != null) {
            try {
                if(cursor.moveToFirst()) {
                    return loadFromCursor(cursor);
                }
            } finally {
                cursor.close();
            }
        }
        return null;
    }

    private static class DatabaseLocationSet implements LocationSet, Iterator<Location> {

        private final Cursor cursor;
        private boolean hasNext;

        public DatabaseLocationSet(Cursor cursor) {
            this.cursor = cursor;
            this.hasNext = cursor != null && cursor.moveToFirst();
        }

        @Override
        public int getCount() {
            return cursor == null ? 0 : cursor.getCount();
        }

        @Override
        public long getDateStart() {
            return 0;
        }

        @Override
        public void setDateStart(long dateStart) {

        }

        @Override
        public long getDateEnd() {
            return 0;
        }

        @Override
        public void setDateEnd(long dateEnd) {

        }

        @Override
        public Location[] toArray() {
            Location[] locations = new Location[cursor.getCount()];
            int i = 0;
            if(hasNext) {
                while (true) {
                    locations[i++] = LocationTable.loadFromCursor(cursor);
                    if(!cursor.moveToNext()) {
                        break;
                    }
                }
            }

            hasNext = false;
            hasNext();

            return locations;
        }

        @Override
        public Iterator<Location> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            if (!hasNext && cursor != null) {
                cursor.close();
                if(Logger.DEBUG) Logger.debug(TAG, "Cursor closed.");
            }
            return hasNext;
        }

        @Override
        public Location next() {
            Location loc = null;
            if (hasNext) {
                loc = LocationTable.loadFromCursor(cursor);
                hasNext = cursor.moveToNext();
            }
            return loc;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}