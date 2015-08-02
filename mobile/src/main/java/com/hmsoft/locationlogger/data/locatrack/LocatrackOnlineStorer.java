package com.hmsoft.locationlogger.data.locatrack;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.hmsoft.locationlogger.BuildConfig;
import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.service.SyncAuthenticatorService;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class LocatrackOnlineStorer extends LocationStorer {

    private static final String TAG = "LocatrackOnlineStorer";
    private static final boolean DEBUG = BuildConfig.DEBUG;

    private static final String USER_AGENT = "Locatrack-";

    // Settings
    private int mMinimumDistance;
    private String mMyLatitudeUrl;
    private String mMyLatitudeKey;
    private String mDeviceId;

    private String mLastNetworkType;
    private Context mContext;
    private ConnectivityManager mConnectivityManager;
    private LocatrackLocation mLastUploadedLocation;

    public int retryCount;
    public int retryDelaySeconds;

    public static class MissingConfigurationException extends IllegalArgumentException {
        public MissingConfigurationException(String configKeyName) {
            super(configKeyName + " not configured.");
        }
    }

    public LocatrackOnlineStorer(Context context) {
        mContext = context;
        mConnectivityManager = (ConnectivityManager) context.getSystemService(Activity.CONNECTIVITY_SERVICE);
    }

    private boolean internalUploadLocation(LocatrackLocation location) {
        boolean update = false;
        long updateId = 0;

        boolean hasNotifyEvent = !TextUtils.isEmpty(location.event) ||
                (mLastUploadedLocation != null && !TextUtils.isEmpty(mLastUploadedLocation.event));

        if (!hasNotifyEvent) {
            synchronized (this) {
                if (mLastUploadedLocation != null) {
                    float distanceTo = mLastUploadedLocation.distanceTo(location);
                    update = distanceTo < mMinimumDistance;

                    if (DEBUG) {
                        if (update) {
                            if (Logger.DEBUG)
                                Logger.debug(TAG, "internalUploadLocation UPDATE: distanceTo LUL: %f", distanceTo);
                        } else if (mLastUploadedLocation != null) {
                            if (Logger.DEBUG)
                                Logger.debug(TAG, "internalUploadLocation: distanceTo LUL: %f", distanceTo);
                        } else {
                            if (Logger.DEBUG) Logger.debug(TAG, "lastUploadedLocation == null");
                        }
                    }

                    if (update) {
                        updateId = mLastUploadedLocation.getTime();
                    }
                }
            }

            if (update && (location.getTime() - updateId <= 0)) {
                if (Logger.DEBUG)
                    Logger.debug(TAG, "Location to update is older than last location");
                return true;
            }
        }

        boolean uploadOk = internalUploadLocation(location, updateId);
        if (uploadOk && !update) {
            synchronized (this) {
                mLastUploadedLocation = new LocatrackLocation(location);
            }
        }

        return uploadOk;
    }

    private boolean internalUploadLocation(LocatrackLocation location, long updateId) {

        if (Logger.DEBUG) Logger.debug(TAG, "internalUploadLocation: %s", location);

        boolean result;

        try {
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(mMyLatitudeUrl);

            // add header
            if (mLastNetworkType == null) mLastNetworkType = "n/a";
            post.setHeader("User-Agent", USER_AGENT + mDeviceId + " (" + mLastNetworkType + ")");
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");

            if (DEBUG) {
                if (location.event != null) {
                    post.setHeader("User-Agent", USER_AGENT + mDeviceId + " " + location.event);
                }
            }

            List<NameValuePair> urlParameters = new ArrayList<>();
            urlParameters.add(new BasicNameValuePair("key", mMyLatitudeKey));
            urlParameters.add(new BasicNameValuePair("deviceid", mDeviceId));
            urlParameters.add(new BasicNameValuePair("battery", String.valueOf(location.batteryLevel)));
            urlParameters.add(new BasicNameValuePair("latitude", String.valueOf(location.getLatitude())));
            urlParameters.add(new BasicNameValuePair("longitude", String.valueOf(location.getLongitude())));
            urlParameters.add(new BasicNameValuePair("altitude", String.valueOf(location.getAltitude())));
            urlParameters.add(new BasicNameValuePair("accuracy", String.valueOf(location.getAccuracy())));
            urlParameters.add(new BasicNameValuePair("speed", String.valueOf(location.getSpeed())));
            urlParameters.add(new BasicNameValuePair("utc_timestamp", String.valueOf(location.getTime())));
            if (!TextUtils.isDigitsOnly(location.event)) {
                urlParameters.add(new BasicNameValuePair("notify", location.event));
            }
            if (updateId > 0) {
                urlParameters.add(new BasicNameValuePair("update_id", String.valueOf(updateId)));
            }
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(urlParameters);
            post.setEntity(entity);

            HttpResponse response = client.execute(post);

            int status = response.getStatusLine().getStatusCode();
            result = (status == 200 || status == 201);

            if (DEBUG) {
                BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
                String line;
                StringBuilder serverResponse = new StringBuilder();
                while ((line = rd.readLine()) != null) {
                    serverResponse.append(line).append("\n");
                }
                rd.close();

                if (result) {
                    if (Logger.DEBUG)
                        Logger.debug(TAG, "internalUploadLocation: Location saved to server. Response: %s", serverResponse);
                } else {
                    Logger.warning(TAG, "internalUploadLocation: Error saving location to server: Status: %d", status);
                }
            }

            if (!DEBUG && !result) {
                Logger.warning(TAG, "internalUploadLocation: Error saving location to server: Status: %d", status);
            }
        } catch (IOException e) {
            Logger.warning(TAG, "internalUploadLocation exception:", e);
            result = false;
        }
        return result;
    }

    @Override
    public void configure() {
        if (Logger.DEBUG) Logger.debug(TAG, "configure");
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);

        mMinimumDistance = Integer.valueOf(preferences.getString(mContext.getString(R.string.pref_minimun_distance_key), String.valueOf(mMinimumDistance)));
        mMyLatitudeUrl = preferences.getString(mContext.getString(R.string.pref_locatrack_uri_key), mMyLatitudeUrl);
        mMyLatitudeKey = preferences.getString(mContext.getString(R.string.pref_locatrack_key_key), mMyLatitudeKey);
        mDeviceId = preferences.getString(mContext.getString(R.string.pref_locatrack_deviceid_key), SyncAuthenticatorService.getGoogleAccount(mContext));
    }

    @Override
    public boolean storeLocation(LocatrackLocation location) {
        mTotalItems++;

        if (TextUtils.isEmpty(mMyLatitudeUrl)) {
            throw new MissingConfigurationException("myLatitudeUrl");
        }

        if (TextUtils.isEmpty(mMyLatitudeKey)) {
            throw new MissingConfigurationException("myLatitudeKey");
        }

        Logger.info(TAG, "Uploading location...");
        int count = retryCount;
        boolean uploadOk = false;
        while (!uploadOk) {
            NetworkInfo activeNetwork = mConnectivityManager.getActiveNetworkInfo();
            boolean isConnected = activeNetwork != null && activeNetwork.isConnected();

            if (isConnected) {
                mLastNetworkType = activeNetwork.getTypeName();
            }

            if (DEBUG) {
                if (!isConnected)
                    if (Logger.DEBUG) Logger.debug(TAG, "Device disconnected");
            }

            uploadOk = isConnected && internalUploadLocation(location);
            if (--count < 0) {
                Logger.warning(TAG, "Too many retries");
                break;
            }
            if (!uploadOk) {
                Logger.warning(TAG, "Upload failed retry %d of %d. Sleeping %d seconds", count + 1,
                        retryCount, retryDelaySeconds);
                TaskExecutor.sleep(retryDelaySeconds);
            }
        }


        if (uploadOk) {
            mTotalSuccess++;
        } else {
            mTotalFail++;
        }

        return uploadOk;
    }
}
