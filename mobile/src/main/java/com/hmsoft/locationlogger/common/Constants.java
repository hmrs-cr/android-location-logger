package com.hmsoft.locationlogger.common;

import com.hmsoft.locationlogger.BuildConfig;

public final class Constants {

    public static final String VERSION_STRING = "v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.BUILD_TYPE + ")";

    public static final String BASE_NAMESPACE = BuildConfig.APPLICATION_ID;
    public static final String ACTION_NAMESPACE = BASE_NAMESPACE + ".ACTION";
    public static final String EXTRA_NAMESPACE = BASE_NAMESPACE + ".EXTRA";

    public static final String ACTION_NOTIFICATION_UPDATE_LOCATION = ACTION_NAMESPACE + ".NOTIFICATION_UPDATE_LOCATION";
    public static final String ACTION_SYNC = ACTION_NAMESPACE + ".SYNC";
    public static final String ACTION_ALARM = ACTION_NAMESPACE + ".ALARM";
    public static final String ACTION_BALANCE_SMS = ACTION_NAMESPACE + ".BALANCE_SMS";
	
	public static final String ACTION_UPDATE_UI = ACTION_NAMESPACE + ".ACTION_UPDATE_UI";

    public static final String EXTRA_START_ALARM = EXTRA_NAMESPACE + ".START_ALARM";
    public static final String EXTRA_STOP_ALARM = EXTRA_NAMESPACE + ".STOP_ALARM";
    public static final String EXTRA_ALARM_CALLBACK = EXTRA_NAMESPACE + ".ALARM_CALLBACK";
    public static final String EXTRA_UPDATE_LOCATION = EXTRA_NAMESPACE + ".UPDATE_LOCATION";
    public static final String EXTRA_NOTIFY_INFO = EXTRA_NAMESPACE + ".NOTIFY_INFO";
    public static final String EXTRA_BALANCE_SMS = EXTRA_NAMESPACE + ".BALANCE_SMS";
    public static final String EXTRA_CONFIGURE = EXTRA_NAMESPACE + ".CONFIGURE";
    public static final String EXTRA_SYNC = EXTRA_NAMESPACE + ".SYNC";
    public static final String EXTRA_BATTERY_LEVEL = EXTRA_NAMESPACE + ".EXTRA_BATTERY_LEVEL";
    public static final String EXTRA_SMS_FROM_ADDR =  EXTRA_NAMESPACE + ".EXTRA_SMS_FROM_ADDR";
    public static final String EXTRA_SMS_BODY =  EXTRA_NAMESPACE + ".EXTRA_SMS_BODY";


    private Constants() {
    }
}
