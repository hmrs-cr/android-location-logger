package com.hmsoft.locationlogger.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Toast;

import com.hmsoft.locationlogger.common.Constants;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.service.CoreService;

public class PowerConnectionReceiver extends BroadcastReceiver {

    private static PowerConnectionReceiver sInstance = null;

    private static final IntentFilter powerConnectedIntentFilter = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
    private static final IntentFilter powerDisconnectedIntentFilter = new IntentFilter(Intent.ACTION_POWER_DISCONNECTED);

    private static final String TAG = "PowerConnectionReceiver";

    public static void register(Context context){
        if (sInstance == null) {
            sInstance = new PowerConnectionReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_POWER_CONNECTED);
            filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            context.registerReceiver(sInstance, filter);

            if(Logger.DEBUG) {
                Logger.debug(TAG, "Registered");
            }
        }
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        if(Logger.DEBUG) {
            Logger.debug(TAG, "onReceive:%s", intent);
            Toast.makeText(context, "" + intent, Toast.LENGTH_LONG).show();
        }

        int batteryLevel = Utils.getBatteryLevel(true);
        CoreService.powerConnectionChange(context, batteryLevel);
    }
}