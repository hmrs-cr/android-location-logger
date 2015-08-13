package com.hmsoft.locationlogger.data.preferences;


import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.hmsoft.locationlogger.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

public class PreferenceProfile {
    public static final int PROFILE_MANUAL = 0;
    public static final int PROFILE_CAR = 1;
    public static final int PROFILE_BICYCLE = 2;
    public static final int PROFILE_HIKING = 3;
    public static final int PROFILE_COUNT = 4;

    private static PreferenceProfile sInstance;

    private final Context mContext;
    private final SharedPreferences mPreferences;
    private final HashMap<String, String> mDefaults;
    private int mBlockSize = 1;
    private int[] mIntervalValues = null;

    public final int activeProfile;

    public static PreferenceProfile get(Context context) {
        if(sInstance == null) sInstance = new PreferenceProfile(context);
        return  sInstance;
    }

    public static void reset() {
        sInstance = null;
    }

    private PreferenceProfile(Context context) {
        mContext = context.getApplicationContext();
        mPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        mDefaults = new HashMap<>();
        String defaultProfile = mContext.getString(R.string.pref_active_profile_default);
        activeProfile = getInt(R.string.pref_active_profile_key, defaultProfile);
        if(activeProfile != PROFILE_MANUAL) {
            createDefaults();
        } else {
            mIntervalValues = new int[1];
            mIntervalValues[0] = getInt(R.string.pref_update_interval_key, "300");
        }
    }

    private void createDefaults() {
        try {
            InputStream profileStream = mContext.getAssets().open("profiles/" + activeProfile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(profileStream));
            String line;
            TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter('=');
            while ((line = reader.readLine()) != null) {
                splitter.setString(line);
                String key = splitter.next();
                String value = splitter.next();

                if(key.equals("intervals")) {
                    String[] intervalStrings = value.split(",");
                    mIntervalValues = new int[intervalStrings.length];
                    for(int c = 0; c < mIntervalValues.length; c++) {
                        mIntervalValues[c] = Integer.parseInt(intervalStrings[c]);
                    }
                    int wholeBlock = mIntervalValues.length - 2;
                    if(wholeBlock > 1) {
                        mBlockSize = 100 / wholeBlock;
                    }
                } else {
                    mDefaults.put(key, value);
                }

            }
        } catch (IOException e) {
            mIntervalValues = new int[1];
            mIntervalValues[0] = getInt(R.string.pref_update_interval_key, "300");
        }
    }

    public int getInterval(int level) {
        if(activeProfile == PROFILE_MANUAL) {
            return  mIntervalValues[0];
        }

        int index = level / mBlockSize;
        if(index >= mIntervalValues.length) {
            index = mIntervalValues.length - 1;
        }
        return mIntervalValues[index];
    }

    public boolean hasProfileDefault(String preferenceKey) {
        return mDefaults.containsKey(preferenceKey);
    }

    public String getString(int keyId, String defValue) {
        String key = mContext.getString(keyId);

        Object value = mDefaults.get(key);
        if(value != null) return (String)value;

        return mPreferences.getString(key,defValue);
    }

    public int getInt(int keyId, String defValue) {
        String key = mContext.getString(keyId);

        Object value = mDefaults.get(key);
        if(value != null) return Integer.parseInt((String)value);

        return Integer.parseInt(mPreferences.getString(key, defValue));
    }

    public int getInt(int keyId, int defValue) {
        String key = mContext.getString(keyId);

        Object value = mDefaults.get(key);
        if(value != null) return Integer.parseInt((String)value);

        return mPreferences.getInt(key, defValue);
    }

    public long getLong(int keyId, long defValue) {
        String key = mContext.getString(keyId);

        Object value = mDefaults.get(key);
        if(value != null) return Long.parseLong((String) value);

        return mPreferences.getLong(key, defValue);
    }

    public float getFloat(int keyId, String defValue) {
        String key = mContext.getString(keyId);

        Object value = mDefaults.get(key);
        if(value != null) return Float.parseFloat((String) value);

        return Float.parseFloat(mPreferences.getString(key, defValue));
    }

    public float getFloat(int keyId, float defValue) {
        String key = mContext.getString(keyId);

        Object value = mDefaults.get(key);
        if(value != null) return Float.parseFloat((String)value);

        return mPreferences.getFloat(key, defValue);
    }

    public boolean getBoolean(int keyId, boolean defValue) {
        String key = mContext.getString(keyId);

        Object value = mDefaults.get(key);
        if(value != null) return "true".equals(value);

        return mPreferences.getBoolean(key, defValue);
    }
}
