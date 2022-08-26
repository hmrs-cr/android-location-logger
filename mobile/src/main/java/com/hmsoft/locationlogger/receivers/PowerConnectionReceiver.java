package com.hmsoft.locationlogger.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.PowerManager;
import android.widget.Toast;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.service.CoreService;

public class PowerConnectionReceiver extends BroadcastReceiver {

    private static PowerConnectionReceiver sInstance = null;

    private static final IntentFilter powerConnectedIntentFilter = new IntentFilter(Intent.ACTION_POWER_CONNECTED);
    private static final IntentFilter powerDisconnectedIntentFilter = new IntentFilter(Intent.ACTION_POWER_DISCONNECTED);

    private static final String TAG = "PowerConnectionReceiver";

    private static final int DelayInSeconds = 6; // TODO: Read this value from config.

    private static Runnable currentPowerEvent = null;

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

        if (currentPowerEvent != null) {
            TaskExecutor.removeFromUIThread(currentPowerEvent);
            currentPowerEvent = null;
        }

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "locatrack:wakelock:powerconnection.event");
        wakeLock.acquire(Math.round(DelayInSeconds * 2.5) * 1000);
        currentPowerEvent = TaskExecutor.executeOnUIThread(new Runnable() {
            @Override
            public void run() {
                Intent batteryIntent = Utils.getBatteryChangedIntent();
                int pluggedTo = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                int batteryLevel = Utils.getBatteryLevel(batteryIntent);
                CoreService.powerConnectionChange(context, batteryLevel, pluggedTo);
                currentPowerEvent = null;
            }
        }, DelayInSeconds);
    }
}