package com.hmsoft.locationlogger.data.commands;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.hmsoft.locationlogger.common.TelegramHelper;
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
    public void execute(String[] params) {
        ConnectivityManager connectivityManager = (ConnectivityManager)context.androidContext.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if(networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            TelegramHelper.sendTelegramDocument(context.botKey, context.fromId, context.messageId,
                    Helper.getInstance().getPathFile());
        } else {
            sendTelegramReply("WiFi connection required.");
        }
    }
}
