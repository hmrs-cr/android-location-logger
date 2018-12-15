package com.hmsoft.locationlogger.data.sqlite;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.hmsoft.locationlogger.LocationLoggerApp;
import com.hmsoft.locationlogger.common.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;


public class Helper extends SQLiteOpenHelper {

    private static final String TAG = "Helper";

    public static final String TYPE_INTEGER = "  INTEGER";
    public static final String TYPE_TEXT = "  TEXT";
    public static final String TYPE_REAL = "  REAL";
    public static final String TYPE_PRIMARY_KEY = " PRIMARY KEY";
    public static final String COMMA_SEP = ",";

    public static final int DATABASE_VERSION = 17;
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

    public void importDB(String inFileName) {

        final File outFile = getPathFile();
        try {

            File dbFile = new File(inFileName);
            FileInputStream fis = new FileInputStream(dbFile);

            // Open the empty db as the output stream
            OutputStream output = new FileOutputStream(outFile);

            // Transfer bytes from the input file to the output file
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }

            // Close the streams
            output.flush();
            output.close();
            fis.close();

        } catch (Exception e) {
            Logger.warning(TAG, "importDB", e);
        }
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
        db.execSQL(TripTable.SQL_CREATE_DETAIL_VIEW);

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
        if(newVersion == 17) {
            db.execSQL("DROP VIEW " + TripTable.DETAIL_VIEW_NAME);
            db.execSQL("DROP VIEW " + TripTable.VIEW_NAME);
            db.execSQL(TripTable.SQL_CREATE_DETAIL_VIEW);
            db.execSQL(TripTable.SQL_CREATE_VIEW);
        }
    }
}