package com.hmsoft.locationlogger.data.commands;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;
import com.hmsoft.locationlogger.data.preferences.PreferenceProfile;
import com.hmsoft.locationlogger.data.sqlite.Helper;

class GetDBCommand extends Command {
    static final String COMMAND_NAME = "GetDB";

    @Override
    public String getSummary() {
        return "Get internal database.";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params, CommandContext context) {
        ConnectivityManager connectivityManager;
        NetworkInfo networkInfo;

        boolean unlimitedData = PreferenceProfile.get(context.androidContext).getBoolean(R.string.pref_unlimited_data_key, false);

        if(!unlimitedData &&
                ((connectivityManager = (ConnectivityManager) context.androidContext.getSystemService(Context.CONNECTIVITY_SERVICE)) == null ||
                (networkInfo = connectivityManager.getActiveNetworkInfo()) == null ||
                networkInfo.getType() != ConnectivityManager.TYPE_WIFI)) {

            context.sendTelegramReply("WiFi connection required.");
            return;
        }

        TelegramHelper.sendTelegramDocument(context.botKey, context.fromId, context.messageId,
                Helper.getInstance().getPathFile());
    }
}
