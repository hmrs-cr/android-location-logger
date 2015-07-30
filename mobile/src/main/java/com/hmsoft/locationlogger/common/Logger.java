package com.hmsoft.locationlogger.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.hmsoft.locationlogger.BuildConfig;
import com.hmsoft.locationlogger.LocationLoggerApp;

import android.util.Log;

public final class Logger {
	
	private static final String TAG = "Logger";
    private static final String APP_TAG = "NMEALOG:";

	public static final boolean DEBUG = BuildConfig.DEBUG;
    public static final boolean WARNING = true;
    public static final boolean INFO = true;
    public static final boolean ERROR = true;

    public static  final String DEBUG_TAG = "DEBUG";
    public static  final String WARNING_TAG = "WARNING";
    public static  final String INFO_TAG = "INFO";
    public static  final String ERROR_TAG = "ERROR";

	private static final String LOG_FILE = "log-%s.log";
	private static final String LOGS_FOLDER = "logs";
	
	private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US);
	private static final SimpleDateFormat LOG_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd", Locale.US);

    private static File sLogsFolder = null;
	
	public static void log2file(String tag, String msg, String fileName, Throwable e) {
		
		try {
			if(sLogsFolder == null) {
				sLogsFolder = LocationLoggerApp.getContext().getExternalFilesDir(LOGS_FOLDER);
			}
			
			if(sLogsFolder == null) return;
			
			Date now = new Date();
			
			File file = new File(sLogsFolder, String.format(fileName, LOG_DATE_FORMAT.format(now)));
			
			FileOutputStream os = new FileOutputStream(file, true);
			OutputStreamWriter writer = new OutputStreamWriter(os);
			try {
				writer.append(SIMPLE_DATE_FORMAT.format(now));
				writer.append("\t");
				writer.append(tag);
				writer.append("\t");
				writer.append(msg);
				writer.append("\t");
				if(e != null)
					writer.append(e.toString());
				writer.append("\n");
				writer.flush();
			} finally {
				writer.close();
			}
		} catch (IOException ex) {
			Log.w(TAG, "log2file failed:", ex);
		}
	}
	
	public static void debug(String tag, String msg) {
		if(DEBUG) {
			Log.d(APP_TAG + tag, msg);
			log2file(tag + "\t" + DEBUG_TAG, msg, LOG_FILE, null);
		}
	}
	
	public static void debug(String tag, String msg, Throwable e) {
		if(DEBUG) {
			Log.d(APP_TAG + tag, msg, e);
			log2file(tag + "\t" + DEBUG_TAG, msg, LOG_FILE, e);
		}
	}
	
	public static void debug(String tag, String msg, Object... args) {
		if(DEBUG) {
			msg = String.format(msg,  args);
			Log.d(APP_TAG + tag, String.format(msg,  args));
			log2file(tag + "\t" + DEBUG_TAG, msg, LOG_FILE, null);
		}
	}
	
//	public static void error(String tag, String msg, Throwable e) {
//		if(ERROR) {
//			Log.e(tag, msg, e);
//			log2file(tag + "\t" + ERROR_TAG, msg, LOG_FILE, e);
//		}
//	}
//
	public static void error(String tag, String msg, Object... args) {
		if(ERROR) {
			msg = String.format(msg,  args);
			Log.e(APP_TAG + tag, String.format(msg,  args));
			log2file(tag + "\t" + ERROR_TAG, msg, LOG_FILE, null);
		}
	}

    public static void warning(String tag, String msg, Throwable e) {
        if(WARNING) {
            Log.w(APP_TAG + tag, msg, e);
            log2file(tag + "\t" + WARNING_TAG, msg, LOG_FILE, e);
        }
    }

    public static void warning(String tag, String msg, Object... args) {
        if(WARNING) {
            msg = String.format(msg,  args);
            Log.w(APP_TAG + tag, String.format(msg,  args));
            log2file(tag + "\t" + WARNING_TAG, msg, LOG_FILE, null);
        }
    }


    public static void info(String tag, String msg) {
        if(INFO) {
            Log.i(APP_TAG + tag, msg);
            log2file(tag + "\t" + INFO_TAG, msg, LOG_FILE, null);
        }
    }

    public static void info(String tag, String msg, Object... args) {
        if(INFO) {
            msg = String.format(msg,  args);
            Log.i(APP_TAG + tag, String.format(msg,  args));
            log2file(tag + "\t" + INFO_TAG, msg, LOG_FILE, null);
        }
    }
}
