package com.hmsoft.nmealogger.data.nmea;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.text.format.Time;

import com.hmsoft.nmealogger.R;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

public final class NmeaCommon {

    public static final String NMEA_PROVIDER = "nmea";
    public static final String GPGGA_SENTENCE = "$GPGGA";
    public static final String GPRMC_SENTENCE = "$GPRMC";

    public static final String DIRECTION_NORTH = "N";
    public static final String DIRECTION_SOUTH = "S";
    public static final String DIRECTION_EAST = "E";
    public static final String DIRECTION_WEST = "W";

    public static final String LOG_FILE_EXT = ".LOG";
    public static final String LOG_FILE_REGEX = "[0-9]{9}" + LOG_FILE_EXT;
    public static final String LOG_FILE_FORMAT = "%09d" + LOG_FILE_EXT;

    public static final int GPS_LOGFILE_MAX_ENTRIES = 10000;
    public static final int GPS_LOGFILE_MAXCOUNT = 300;
    public static final int NMEA_ENTRY_SIZE = 66;

    public static final Time sUtcTime = new Time("UTC");

    public static final String FORMAT_GPGGA_SENTENCE = GPGGA_SENTENCE + ",%02d%02d%02d.000,%s,%s,%s,%s,1,6,0,%s,M,0,M,,";
    public static final String FORMAT_GPRMC_SENTENCE = GPRMC_SENTENCE + ",%02d%02d%02d.000,A,%s,%s,%s,%s,%s,%s,%02d%02d%02d,0,A";

    private NmeaCommon() {
    }

    private static FilenameFilter mLogFileFilter = new FilenameFilter() {

        public boolean accept(File file, String s) {
            return s.matches(LOG_FILE_REGEX);
        }
    };

    private static Comparator<File> mFileSorter = new Comparator<File>() {
        public int compare(File file, File file1) {
            String name1 = file.getAbsolutePath();
            String name2 = file1.getAbsolutePath();
            return name2.compareTo(name1);
        }
    };

    public static File getCanonCWGeoLogPath() {
        return new File(Environment.getExternalStorageDirectory(), "Canon CW/.geolog/");
    }

	public static File getNmeaPathFromConfig(Context context) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        String configPath = preferences.getString(context.getString(R.string.pref_nmealog_directory_key), "");
        final File nmeaPath;
        if (!TextUtils.isEmpty(configPath)) {
            nmeaPath = new File(configPath);
        } else {
            nmeaPath = NmeaCommon.getCanonCWGeoLogPath();
        }
		return nmeaPath;
	}

    public static File getExternalNmeaPath(Context context) {
        File nmeaPath = getNmeaPathFromConfig(context);
        return new File(nmeaPath.getParent(), "geolog_import");
    }

    public static ArrayList<File> getLogFilePathList(File nmeaPath) {
        File[] files = nmeaPath.listFiles(mLogFileFilter);
        ArrayList<File> fileList;
        if (files != null) {
            fileList = new ArrayList<File>(files.length);
            for (File file : files) {
                fileList.add(file);
            }
            Collections.sort(fileList, mFileSorter);
        } else {
            fileList = new ArrayList<File>();
        }

        return fileList;
    }

    private static String setNMEAformat(double latlong) {
		/*
        double latlongInt = Math.floor(latlong);
        double latLonDecimal = 60.0D * (latlong - latlongInt);

        String latLonDecimalStr = String.format(Locale.US, "%7.5f", latLonDecimal);
        if (latLonDecimalStr.indexOf(".") < 2)
            latLonDecimalStr = "0" + latLonDecimalStr;

        return String.format(Locale.US, "%1.0f", latlongInt) + latLonDecimalStr;
		*/
		int latlongInt = (int)latlong;
        double latLonDecimal = 60.0D * (latlong % 1);

        String latLonDecimalStr = String.format(Locale.US, "%7.5f", latLonDecimal);
        if (latLonDecimalStr.indexOf(".") < 2)
            latLonDecimalStr = "0" + latLonDecimalStr;

        return String.format(Locale.US, "%d", latlongInt) + latLonDecimalStr;
    }

    public static double parseNmeaFormat(String degrees, String direction) {

        double latlong = 0.0;
        double temp1 = Double.parseDouble(degrees);
        double temp2 = Math.floor(temp1 / 100);
        double temp3 = (temp1 / 100 - temp2) / 0.6;

        if (DIRECTION_SOUTH.equals(direction) || DIRECTION_WEST.equals(direction)) {
            latlong = -(temp2 + temp3);
        } else if (DIRECTION_NORTH.equals(direction) || DIRECTION_EAST.equals(direction)) {
            latlong = (temp2 + temp3);
        }

        return latlong;
    }

//    public static int nmeaChecksum(String nmea) {
//        return  nmeaChecksum(nmea, 0);
//    }

    public static int nmeaChecksum(CharSequence nmea, int startIndex) {
        int length = nmea.length();
        return  nmeaChecksum(nmea, startIndex, length);
    }

    public static int nmeaChecksum(CharSequence nmea, int startIndex, int endIndex) {
        int checkSum = 0;

        for (int c = startIndex; c < endIndex; c++) {
            checkSum ^= (int) nmea.charAt(c);
        }
        return checkSum;
    }

    // 1
    public static String getNmeaString(Location location) {
        double latitude = location.getLatitude();
        String latDirection = DIRECTION_NORTH;
        if (latitude < 0.0D) {
            latDirection = DIRECTION_SOUTH;
            latitude = -1.0D * latitude;
        }

        String latiValStr = setNMEAformat(latitude);

        double longitude = location.getLongitude();
        String longDirection = DIRECTION_EAST;
        if (longitude < 0.0D) {
            longDirection = DIRECTION_WEST;
            longitude = -1.0D * longitude;
        }

        String longiValStr = setNMEAformat(longitude);

        String altitudeStr = "0";
        if (location.hasAltitude()) {
            altitudeStr = String.format(Locale.US, "%3.1f", location.getAltitude());
        }

        String speedStr = "";
        if (location.hasSpeed()) {
            speedStr = String.format(Locale.US, "%3.1f", location.getSpeed());
        }

        String bearingStr = "";
        if (location.hasBearing()) {
            bearingStr = String.format(Locale.US, "%3.1f", location.getBearing());
        }

        sUtcTime.set(location.getTime());

        int hours = sUtcTime.hour;
        int minutes = sUtcTime.minute;
        int seconds = sUtcTime.second;

        int year = sUtcTime.year % 100;
        int month = 1 + sUtcTime.month;
        int day = sUtcTime.monthDay;

        String ggaStr = String.format(FORMAT_GPGGA_SENTENCE,
                hours, minutes, seconds, latiValStr, latDirection, longiValStr, longDirection, altitudeStr);

        String rmcStr = String.format(FORMAT_GPRMC_SENTENCE,
                hours, minutes, seconds, latiValStr, latDirection, longiValStr, longDirection, speedStr, bearingStr, day, month, year);

        int ggaStrChecksum = nmeaChecksum(ggaStr, 1);
        int rmcStrChecksum = nmeaChecksum(rmcStr, 1);

        return String.format("%s*%02X\r\n%s*%02X\r\n", ggaStr, ggaStrChecksum, rmcStr, rmcStrChecksum);
    }
}