package com.hmsoft.locationlogger.common;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;

import java.lang.reflect.Method;

import static android.content.ContentValues.TAG;

public class WifiApManager {

    public static Boolean wasWifiEnabled = null;

    public static boolean isApOn(Context context) {
        WifiManager wifimanager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
        try {
            Method method = wifimanager.getClass().getDeclaredMethod("isWifiApEnabled");
            method.setAccessible(true);
            return (Boolean) method.invoke(wifimanager);
        } catch (Throwable e) {
            Logger.error(TAG, "Error:isApOn", e);
        }
        return false;
    }

    public static WifiConfiguration getWifiApConfiguration(Context context) {
        WifiManager wifimanager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
        try {
            Method method = wifimanager.getClass().getDeclaredMethod("getWifiApConfiguration");
            method.setAccessible(true);
            return (WifiConfiguration) method.invoke(wifimanager);
        } catch (Throwable e) {
            Logger.error(TAG, "Error:getWifiApConfiguration", e);
        }
        return null;
    }

    // toggle wifi hotspot on or off
    public static boolean configApState(Context context, boolean enable) {
        WifiManager wifimanager = (WifiManager) context.getSystemService(context.WIFI_SERVICE);
        WifiConfiguration wificonfiguration = null;
        try {
            // if WiFi is on, turn it off
            if (enable) {
                wasWifiEnabled = wifimanager.isWifiEnabled();
                wifimanager.setWifiEnabled(false);
            }
            Method method = wifimanager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
            method.invoke(wifimanager, wificonfiguration, enable);

            if(!enable){
                wifimanager.setWifiEnabled(true);
            }

            return true;
        } catch (Exception e) {
            Logger.error(TAG, "Error:configApState", (Throwable)e);
        }
        return false;
    }
} // end of class