package com.hmsoft.nmealogger.ui;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.WindowManager;
import android.widget.TextView;

import com.hmsoft.nmealogger.R;
import com.hmsoft.nmealogger.common.Logger;
import com.hmsoft.nmealogger.service.LocationExportWebServer;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.Enumeration;

public class WebServerActivity extends ActionBarActivity
    implements LocationExportWebServer.Callbacks {

    private static final String TAG = "WebServerActivity";

    static final int PORT = 8383;
    
    TextView mLabelUrl;
    TextView mServerLog;
    
    int mServedCount;
    String mWifiIpAddress;
    private LocationExportWebServer mWebServer;

    public static void start(Context context) {
        Intent intent = new Intent(context, WebServerActivity.class);
        context.startActivity(intent);
    }

    private String getIPExp() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                if (intf.getName().contains("wlan")) {
                    for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                        InetAddress inetAddress = enumIpAddr.nextElement();
                        if (!inetAddress.isLoopbackAddress() && (inetAddress.getAddress().length == 4)) {
                            return inetAddress.getHostAddress();
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    protected String wifiIpAddress(Context context) {
        WifiManager wifiManager = (WifiManager) context.getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endianif needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Logger.error(TAG, "Unable to get host address.");
            ipAddressString = null;
        }

        if(ipAddressString == null) {
            ipAddressString = getIPExp();
        }

        return ipAddressString;
    }
    
    void clearLog() {
        mServerLog.setText("");
        mServerLog.append("Listening on: " + mWifiIpAddress + ":" + PORT + "\n----------------------------------------------------------------------\n\n");
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_server);
        mLabelUrl = (TextView) findViewById(R.id.labelUrl);
        mServerLog = (TextView) findViewById(R.id.serverLog);
        mServerLog.setVerticalScrollBarEnabled(true);
        mWifiIpAddress = wifiIpAddress(this);
        if(mWifiIpAddress == null) {
            mLabelUrl.setText(R.string.connect_wifi_label);
            return;
        }
        
        clearLog();
        try {            
            mWebServer = new LocationExportWebServer(PORT);
            mWebServer.start();
            mLabelUrl.setText("http://" + mWifiIpAddress + ":" + PORT);
            mWebServer.setCallbacks(this);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } catch (Exception e) {
            Logger.error(TAG, "Error starting server", e);
            mLabelUrl.setText(R.string.webserver_error_label);
        }
    }

    @Override
    protected void onDestroy() {
        if (mWebServer != null) mWebServer.stop();
        super.onDestroy();
    }

    @Override
    public void onStart(final long startFrom) {
        mServerLog.post(new Runnable() {
            @Override
            public void run() {
                clearLog();
                mServedCount = 0;
                String date;
                if(startFrom == 0) {
                    date = "the beginning of time";
                } else {
                    date = new Date(startFrom).toString();
                }
                mServerLog.append("Exporting locations since " + date + "\n");
            }
        });
    }

    @Override
    public void onLocationServed(final Location location) {
        mServerLog.post(new Runnable() {
            @Override
            public void run() {
                mServerLog.append("Location served: " + location.getTime() + "\n");
                mServerLog.scrollBy(0, mServerLog.getHeight());
                mServedCount++;
            }
        });
    }

    @Override
    public void onFinish() {
        mServerLog.post(new Runnable() {
            @Override
            public void run() {
                mServerLog.append("Done: " + mServedCount + " locations served!\n\n");
                mServerLog.scrollBy(0, mServerLog.getHeight());
            }
        });
    }
}