package com.hmsoft.locationlogger.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.service.CoreService;

public class PowerConnectionReceiver extends BroadcastReceiver {

    private static final String TAG = "PowerConnectionReceiver";

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