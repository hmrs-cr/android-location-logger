package com.hmsoft.locationlogger.receivers;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.service.CoreService;

public class StartServiceReceiver extends BroadcastReceiver {

    private static final String TAG = "StartServiceReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if(Logger.DEBUG) {
            Logger.debug(TAG, "onReceive:%s", intent);
            Toast.makeText(context, "" + intent, Toast.LENGTH_LONG).show();
        }

        CoreService.start(context);
    }

    public static void enable(Context context) {
        PackageManager pm = context.getPackageManager();

        // Service
        ComponentName cn = new ComponentName(context, CoreService.class);
        pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        // Boot receiver
        cn = new ComponentName(context, StartServiceReceiver.class);
        pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        CoreService.start(context);
    }

    public static void disable(Context context) {
        CoreService.stop(context);

        PackageManager pm = context.getPackageManager();

        // Service
        ComponentName cn = new ComponentName(context, CoreService.class);
        pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        // Boot receiver
        cn = new ComponentName(context, StartServiceReceiver.class);
        pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
    }
}