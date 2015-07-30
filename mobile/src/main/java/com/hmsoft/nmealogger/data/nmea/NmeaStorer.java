package com.hmsoft.nmealogger.data.nmea;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.hmsoft.nmealogger.NmeaLoggerApp;
import com.hmsoft.nmealogger.R;
import com.hmsoft.nmealogger.data.LocationStorer;
import com.hmsoft.nmealogger.common.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Locale;

public class NmeaStorer extends LocationStorer {

    private static final String TAG = "NmeaLogHelper";

    // Config
    private File mNmeaPath;

    private Context mContext;
    private Writer mWriter = null;

    public NmeaStorer(Context context) {
        mContext = context;
    }

    private void checkLogFile() {
        ArrayList<File> fileList = NmeaCommon.getLogFilePathList(getNmeaPath());
        if (fileList.size() > NmeaCommon.GPS_LOGFILE_MAXCOUNT) {
            File file = fileList.get(fileList.size() - 1);

            boolean deleted = file.delete();
            if (deleted) {
                if(Logger.DEBUG) Logger.debug(TAG, "Log file deleted: %s", file);
            }
        }
    }

    private Writer getWriter() {
        if (this.mWriter == null) {
            try {
                ArrayList<File> files = NmeaCommon.getLogFilePathList(getNmeaPath());
                int i = 0;
                if (files.size() > 0) {
                    i = Integer.parseInt((files.get(0)).getName().replace(NmeaCommon.LOG_FILE_EXT, ""));
                }

                String fileName = String.format(Locale.ENGLISH, NmeaCommon.LOG_FILE_FORMAT, i + 1);

                FileOutputStream os = new FileOutputStream(new File(getNmeaPath(), fileName), true);
                this.mWriter = new OutputStreamWriter(os);

                if(Logger.DEBUG) Logger.debug(TAG, "writer created: %s", fileName);

            } catch (FileNotFoundException e) {
                Logger.warning(TAG, "getWriter", e);
                return null;
            }
        }
        return this.mWriter;
    }

    private void closeWriter() {
        if (this.mWriter != null) {
            try {
                this.mWriter.flush();
                this.mWriter.close();
                this.mWriter = null;
                checkLogFile();

                if(Logger.DEBUG) Logger.debug(TAG, "writer closed");

            } catch (IOException e) {
                Logger.warning(TAG, "closeWriter", e);
            }
        }
    }

//    public void close() {
//    	closeWriter();
//    }

    public static boolean writeNmeaFormat(Location location, Writer writer) {
        try {
            String nmeaEntry = NmeaCommon.getNmeaString(location);
            writer.write(nmeaEntry);
            writer.flush();

            if(Logger.DEBUG) {
                if(Logger.DEBUG) Logger.debug(TAG, "Nmea: %s", nmeaEntry);
            }

            return true;
        } catch (IOException e) {
            Logger.warning(TAG, "writeNmeaFormat error:", e);
            return false;
        }
    }

    public File getNmeaPath() {
        if (mNmeaPath == null) {
            mNmeaPath = NmeaCommon.getCanonCWGeoLogPath();
        }
        if (!mNmeaPath.exists()) {
            //noinspection ResultOfMethodCallIgnored
            mNmeaPath.mkdirs();
        }
        return mNmeaPath;
    }

    @Override
    public void configure() {
        if(Logger.DEBUG) Logger.debug(TAG, "configure");
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext.getApplicationContext());

        String configPath = preferences.getString(mContext.getString(R.string.pref_nmealog_directory_key), "");
        if (!TextUtils.isEmpty(configPath)) {
            File path = getNmeaPath();
            if (!configPath.equals(path.getAbsolutePath())) {
                closeWriter();
                this.mNmeaPath = new File(configPath);
            }
        }
    }

    @Override
    public synchronized boolean storeLocation(Location location) {
        if (this.mTotalItems > NmeaCommon.GPS_LOGFILE_MAX_ENTRIES) {

            if(Logger.DEBUG) {
                if(Logger.DEBUG) Logger.debug(TAG, "entryCount: %d  - MAX_FILE_ENTRIES: %d", this.mTotalItems,
                        NmeaCommon.GPS_LOGFILE_MAX_ENTRIES);
            }

            closeWriter();
            this.mTotalItems = 0;
        }

        Writer writer = this.getWriter();
        if (writer != null) {
            this.mTotalItems++;
            writeNmeaFormat(location, writer);
        }
        return true;
    }
}
