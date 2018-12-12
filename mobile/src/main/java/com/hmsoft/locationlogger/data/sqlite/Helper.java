package com.hmsoft.locationlogger.data.sqlite;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.hmsoft.locationlogger.LocationLoggerApp;
import com.hmsoft.locationlogger.common.Logger;

import java.io.File;


public class Helper extends SQLiteOpenHelper {

    private static final String TAG = "Helper";

    public static final String TYPE_INTEGER = "  INTEGER";
    public static final String TYPE_TEXT = "  TEXT";
    public static final String TYPE_REAL = "  REAL";
    public static final String TYPE_PRIMARY_KEY = " PRIMARY KEY";
    public static final String COMMA_SEP = ",";

    public static final int DATABASE_VERSION = 11;
    public static final String DATABASE_NAME = "locatrack.db";

    private static Helper instance;

    private Helper(Context context, String name, SQLiteDatabase.CursorFactory factory,
                   int version) {
        super(context, name, factory, version);
    }

    private Helper() {
        this(LocationLoggerApp.getContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized Helper getInstance() {
        if (instance == null) {
            instance = new Helper();
        }
        return instance;
    }

    public double getDoubleScalar(String query) {
        Cursor cursor = this.getReadableDatabase().rawQuery(query, null);
        if(cursor != null) {
            try	{
                if(cursor.moveToFirst()) {
                    return cursor.getDouble(0);
                }
            }
            finally	{
                cursor.close();
            }
        }
        return 0;
    }

    public File getPathFile() {
        return  new File(this.getReadableDatabase().getPath());
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if(Logger.DEBUG) Logger.debug(TAG, "onCreate");

        db.execSQL(LocationTable.SQL_CREATE_TABLE);
        db.execSQL(GeocoderTable.SQL_CREATE_TABLE);
        db.execSQL(FuelLogTable.SQL_CREATE_TABLE);
        db.execSQL(FuelLogTable.SQL_CREATE_VIEW);
        db.execSQL(TripTable.SQL_CREATE_TABLE);
        db.execSQL(TripTable.SQL_CREATE_VIEW);

        for (String index : LocationTable.SQL_CREATE_INDICES) {
            db.execSQL(index);
        }

        for (String index : GeocoderTable.SQL_CREATE_INDICES) {
            db.execSQL(index);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(Logger.DEBUG) Logger.debug(TAG, "onUpgrade");
    }
}