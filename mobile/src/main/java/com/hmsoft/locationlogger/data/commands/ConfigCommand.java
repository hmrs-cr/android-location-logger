package com.hmsoft.locationlogger.data.commands;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;

class ConfigCommand extends Command {

    static final String COMMAND_NAME = "Config";

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    private void telegram(String botKey, String chatId) {

        String botName = TelegramHelper.getBotName(botKey);
        if(TextUtils.isEmpty(botName)) {
            sendReply(context, "Invalid bot key.");
            return;
        }

        long result =  TelegramHelper.sendTelegramMessage(botKey, chatId, null, "Hello my Master, I am " + botName + " at your service.");
        if(result == 0) {
            sendReply(context, "Invalid chat id.");
            return;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.androidContext);
        prefs.edit()
                .putString(context.androidContext.getString(R.string.pref_telegram_botkey_key), botKey)
                .putString(context.androidContext.getString(R.string.pref_telegram_chatid_key), chatId)
                .commit();

        sendReply(context, "Telegram botKey updated! Bot:" + botName);
    }

    @Override
    public void execute(String[] params) {
        String[] subParams =  getSubParams(params);
        telegram(getString(subParams, 0), getString(subParams,1));
    }
}
