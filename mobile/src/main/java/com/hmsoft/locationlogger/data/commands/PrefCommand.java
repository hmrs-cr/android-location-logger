package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;

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
            String[] subParams = params[1].split(" ");
            PreferenceProfile prefs = PreferenceProfile.get(context.androidContext);
            if(subParams[0].equals("get")) {
                String prefKey = null;
                if(subParams.length == 2) {
                    prefKey = subParams[1];
                }
                if(prefKey == null) {
                    Map<String, ?> allPrefs = prefs.getPreferences().getAll();
                    String result = "";
                    for (String key : allPrefs.keySet()) {
                        String value = allPrefs.get(key).toString();
                        result += "*" + key + ":* " + value + "\n";
                    }
                    sendReply(context, result);
                } else {

                }
            } else if(subParams[0].equals("")) {

            }
        }
    }
}
