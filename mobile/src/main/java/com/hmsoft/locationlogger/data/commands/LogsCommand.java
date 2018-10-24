package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TelegramHelper;

import java.io.File;

class LogsCommand extends Command {

    static final String COMMAND_NAME = "Logs";

    @Override
    public String getSummary() {
        return "Get internal logs.";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {
        File[] logs = Logger.getLogFiles();
        if (logs != null) {
            TelegramHelper.sendTelegramDocuments(context.botKey, context.fromId, context.messageId, logs);
        } else {
            sendTelegramReply("No logs.");
        }
    }
}