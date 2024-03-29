package com.hmsoft.locationlogger.data.commands;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;

import com.hmsoft.locationlogger.LocationLoggerApp;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;

import java.util.ArrayList;
import java.util.List;

public class WifiCommand extends Command {

    static final String COMMAND_NAME = "Wifi";
    private static final String TAG = "WifiCommand";

    private WifiScanReceiver wifiScanReceiver = new WifiScanReceiver();

    private class WifiScanReceiver extends  BroadcastReceiver {

        private final List<CommandContext> contexts = new ArrayList<CommandContext>();

        public synchronized void addContext(CommandContext context) { contexts.add(context); }
        public synchronized void clearContextList() { contexts.clear(); }

        @Override
        public void onReceive(Context c, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (success) {
                scanSuccess(contexts);
            } else {
                // scan failure handling
                scanFailure(contexts);
            }
        }
    };


    public boolean connectToWifi(final String ssid, String password) {

        int networkId = -1;
        int c;

        if (mWifiManager == null) {
            Logger.error(TAG, "No WiFi manager");
            return false;
        }

        List<WifiConfiguration> list;

        if (mWifiManager.isWifiEnabled()) {
            list = mWifiManager.getConfiguredNetworks();
        } else {
            if (!mWifiManager.setWifiEnabled(true)) {
                Logger.error(TAG, "Enable WiFi failed");
                return false;
            }
            c = 0;
            do {
                TaskExecutor.sleep(1);
                list = mWifiManager.getConfiguredNetworks();
            } while (list == null && ++c < 5);
        }

        if (list == null) {
            Logger.error(TAG, "Could not get WiFi network list");
            return false;
        }

        for (WifiConfiguration i : list) {
            if (i.SSID != null && i.SSID.equals("\"" + ssid + "\"")) {
                networkId = i.networkId;
                break;
            }
        }

        WifiInfo info;
        if (networkId < 0) {
            WifiConfiguration conf = new WifiConfiguration();
            conf.SSID = "\"" + ssid + "\"";
            if(!TextUtils.isEmpty(password)) {
                conf.preSharedKey = "\"" + password + "\"";
            } else {
                conf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            }
            networkId = mWifiManager.addNetwork(conf);
            if (networkId < 0) {
                Logger.error(TAG, "New WiFi config failed");
                return false;
            }
        } else {
            info = mWifiManager.getConnectionInfo();
            if (info != null) {
                if (info.getNetworkId() == networkId) {
                    if(Logger.DEBUG) Logger.debug(TAG, "Already connected to " + ssid);
                    return true;
                }
            }
        }

        if (!mWifiManager.disconnect()) {
            Logger.error(TAG, "WiFi disconnect failed");
            return false;
        }

        if (!mWifiManager.enableNetwork(networkId, true)) {
            Logger.error(TAG, "Could not enable WiFi.");
            return false;
        }

        if (!mWifiManager.reconnect()) {
            Logger.error(TAG, "WiFi reconnect failed");
            return false;
        }

        c = 0;
        do {
            info = mWifiManager.getConnectionInfo();
            if (info != null && info.getNetworkId() == networkId &&
                    info.getSupplicantState() == SupplicantState. COMPLETED &&  info.getIpAddress() != 0) {
                if(Logger.DEBUG) Logger.debug(TAG, "Successfully connected to %s %d", ssid, info.getIpAddress());
                return true;
            }
            TaskExecutor.sleep(1);
        } while (++c < 5);

        Logger.error(TAG, "Failed to connect to " + ssid);
        return false;
    }


    private void scanSuccess(List<CommandContext> contexts) {

        LocationLoggerApp.getContext().unregisterReceiver(wifiScanReceiver);

        List<ScanResult> results = mWifiManager.getScanResults();

        String currentWifi = null;
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        if(wifiInfo != null && wifiInfo.getSupplicantState() == SupplicantState.COMPLETED) {
            currentWifi = wifiInfo.getSSID().replace("\"", "");
        }

        String result = "";
        for(ScanResult scanResult : results) {
            int level = WifiManager.calculateSignalLevel(scanResult.level, 10);


            if(scanResult.SSID.equals(currentWifi)) {
                result += "*" + TelegramHelper.escapeMarkdown(scanResult.SSID) + "* - " + level + "\n";
            } else {
                result += TelegramHelper.escapeMarkdown(scanResult.SSID) +  " - " + level +  "\n";
            }
        }

        for (CommandContext context : contexts) {
            sendReplyAsync(context, result);
        }

        wifiScanReceiver.clearContextList();
    }


    private void scanFailure(List<CommandContext> contexts) {
        Logger.warning(TAG, "Wifi scan failed.");
        scanSuccess(contexts);
    }

    private WifiManager mWifiManager;
    private IntentFilter mIntentFilter;

    @Override
    public String getSummary() {
        return "Get Wifi list or connect to Wifi. _Wifi [SSID] [PASS]_";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void cleanup() {
        if(wifiScanReceiver != null) {
            LocationLoggerApp.getContext().unregisterReceiver(wifiScanReceiver);
        }
        wifiScanReceiver = null;
        mWifiManager = null;
        super.cleanup();
    }

    @Override
    public void execute(String[] params, CommandContext context) {


        String[] subParams = getSubParams(params);
        String ssid = getString(subParams, 0, "");
        String password = getString(subParams, 1, "");


        if (mWifiManager == null) {
            mWifiManager = (WifiManager) context.androidContext.getSystemService(Context.WIFI_SERVICE);

            mIntentFilter = new IntentFilter();
            mIntentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        }

        mWifiManager.setWifiEnabled(true);

        if(TextUtils.isEmpty(ssid)) {
            LocationLoggerApp.getContext().registerReceiver(wifiScanReceiver, mIntentFilter);
            wifiScanReceiver.addContext(context);
            boolean success = mWifiManager.startScan();
            if (!success) {
                List<CommandContext> contexts = new ArrayList<>();
                contexts.add(context);
                scanFailure(contexts);
            }
        } else {
            boolean connected = connectToWifi(ssid, password);
            sendReply(context, "Connected:" + connected);
        }
    }
}

