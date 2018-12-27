package com.hmsoft.locationlogger.data.commands;

import android.content.SharedPreferences;

import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;
import com.hmsoft.locationlogger.service.CoreService;

import java.util.Map;

public class PrefCommand extends Command {

    static final String COMMAND_NAME = "Pref";

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {
        if(params.length == 2) {
            String[] subParams = params[1].split(" ", 3);
            if(subParams[0].equals("get")) {
                getPreference(subParams);

            } else if(subParams[0].equals("set")) {
                if(subParams.length > 2) {
                    setPreference(subParams);
                } else {
                    sendReply(context, "Missing parameters");
                }
            }
        }
    }

    private void setPreference(String[] subParams) {
        try {
            PreferenceProfile prefs = PreferenceProfile.get(context.androidContext);
            Map<String, ?> allPrefs = prefs.getPreferences().getAll();
            String prefKey = subParams[1].toLowerCase();
            String prefValue = subParams[2];
            SharedPreferences.Editor editor = prefs.getPreferences().edit();
            boolean found = false;
            for (String key : allPrefs.keySet()) {
                if (key.equals(prefKey)) {
                    found = true;
                    Object value = allPrefs.get(key);
                    if (value instanceof Long) {
                        editor.putLong(key, Long.valueOf(prefValue)).commit();
                    } else if (value instanceof Integer) {
                        editor.putInt(key, Integer.valueOf(prefValue)).commit();
                    } else if (value instanceof Float) {
                        editor.putFloat(key, Float.valueOf(prefValue)).commit();
                    } else if (value instanceof Boolean) {
                        editor.putBoolean(key, Boolean.valueOf(prefValue)).commit();
                    } else if (value instanceof String) {
                        editor.putString(key, prefValue).commit();
                    }
                    break;
                }
            }
            if(!found) {
                editor.putString(prefKey, prefValue).commit();
            }
            CoreService.configure(context.androidContext);
            sendReply(context, "Success!");
        } catch (Exception e) {
            sendReply(context, e.getMessage());
        }
    }

    private void getPreference(String[] subParams) {
        String prefKey = null;
        if(subParams.length == 2) {
            prefKey = subParams[1];
        }

        PreferenceProfile prefs = PreferenceProfile.get(context.androidContext);
        Map<String, ?> allPrefs = prefs.getPreferences().getAll();
        String result = "";
        for (String key : allPrefs.keySet()) {

            if(prefKey == null || key.equals(prefKey)) {
                Object value = allPrefs.get(key);
                result += "*" + key + ":* "+ value.toString() + "\n";
                if(key.equals(prefKey)) {
                    break;
                }
            }
        }
        if(result.length() == 0) {
            result = "No preference found.";
        }
        sendReply(context, result);
    }
}
