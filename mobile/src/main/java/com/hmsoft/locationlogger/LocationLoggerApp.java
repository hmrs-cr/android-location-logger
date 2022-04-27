package com.hmsoft.locationlogger;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.data.commands.Command;
import com.hmsoft.locationlogger.service.CoreService;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;

public class LocationLoggerApp extends Application {
	
	private static final String TAG = "NmeaLoggerApp";
	
	private static Context sContext;
	
	public static Context getContext() {
		return sContext;
	}

	private void createNotificationChannel() {
		String name = this.getString(R.string.download_notification_channel_name);
		String description = this.getString(R.string.download_notification_channel_desc);
		int importance = NotificationManager.IMPORTANCE_MIN;
		NotificationChannel channel = new NotificationChannel(CoreService.CHANNEL_ID, name, importance);
		channel.setDescription(description);
		channel.setShowBadge(false);
		channel.setSound(null,null);
		channel.enableLights(false);
		channel.enableVibration(false);

		// Register the channel with the system; you can't change the importance
		// or other notification behaviors after this
		NotificationManager notificationManager = this.getSystemService(NotificationManager.class);
		notificationManager.createNotificationChannel(channel);
	}
	
	@Override 
	public void onCreate() {
		super.onCreate();
		sContext = this;
		
		TaskExecutor.init();
        CrashCatcher.init();

		Command.registerCommands(this);

		this.createNotificationChannel();

		if(Logger.DEBUG)  Logger.debug(TAG, "onCreate");
	}
}
