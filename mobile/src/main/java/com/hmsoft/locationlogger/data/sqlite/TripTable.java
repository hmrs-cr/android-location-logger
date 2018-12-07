package com.hmsoft.locationlogger.data.sqlite;

public class TripTable {
    public static final String TABLE_NAME = "trip";

    public static final String SQL_CREATE_TABLE  = "CREATE TABLE " + TABLE_NAME +
                                                   " (id INTEGER PRIMARY KEY, startLocation INTEGER, endLocation INTEGER)";

    public static void createLastTrip(long fromDate) {

    }
}
