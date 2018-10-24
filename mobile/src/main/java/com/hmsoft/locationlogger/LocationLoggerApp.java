package com.hmsoft.locationlogger;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.data.commands.Command;

import android.app.Application;
import android.content.Context;

public class LocationLoggerApp extends Application {
	
	private static final String TAG = "NmeaLoggerApp";
	
	private static Context sContext;
	
	public static Context getContext() {
		return sContext;
	}
	
	@Override 
	public void onCreate() {
		super.onCreate();
		sContext = this;
		
		TaskExecutor.init();
        CrashCatcher.init();

		Command.registerCommands();

		if(Logger.DEBUG)  Logger.debug(TAG, "onCreate");
	}
}
