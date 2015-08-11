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
	
	public static final String ACTION_UPDATE_UI = ACTION_NAMESPACE + ".ACTION_UPDATE_UI";

    public static final String EXTRA_START_ALARM = EXTRA_NAMESPACE + ".START_ALARM";
    public static final String EXTRA_STOP_ALARM = EXTRA_NAMESPACE + ".STOP_ALARM";
    public static final String EXTRA_ALARM_CALLBACK = EXTRA_NAMESPACE + ".ALARM_CALLBACK";
    public static final String EXTRA_UPDATE_LOCATION = EXTRA_NAMESPACE + ".UPDATE_LOCATION";
    public static final String EXTRA_CONFIGURE = EXTRA_NAMESPACE + ".CONFIGURE";
    public static final String EXTRA_CONFIGURE_STORER = EXTRA_NAMESPACE + ".CONFIGURE_STORER";
    public static final String EXTRA_SYNC = EXTRA_NAMESPACE + ".SYNC";


    private Constants() {
    }
}