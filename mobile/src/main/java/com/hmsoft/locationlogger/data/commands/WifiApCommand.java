package com.hmsoft.locationlogger.data.commands;

import android.net.wifi.WifiConfiguration;

import com.hmsoft.locationlogger.common.WifiApManager;

class WifiApCommand extends Command {

    static final String COMMAND_NAME = "WifiAp";

    @Override
    public String getSummary() {
        return "Gets or sets the Wifi Access Point status. WifiAp [on|off]";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {

        if(params.length == 2) {
           String[] newStatus = params[1].split(" ", 1);
            if (newStatus.length == 1) {
                String status = newStatus[0];
                WifiApManager.configApState(context.androidContext, "on".equals(status));
            }
        }

        String  apName = "";
        boolean enabled = WifiApManager.isApOn(context.androidContext);
        if(enabled) {
            WifiConfiguration configuration = WifiApManager.getWifiApConfiguration(context.androidContext);
            if(configuration != null) {
                apName = ", Name: " + configuration.SSID;
            }
        }

        sendReply(context, "Wifi Access Point enabled: " + enabled + apName);
    }
}
