package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;

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
    public void execute(String[] params, CommandContext context) {
        File[] logs = Logger.getLogFiles();
        if (logs != null && logs.length > 0) {
            String chatId = context.source == SOURCE_SMS ? context.channelId : context.fromId;
            TelegramHelper.sendTelegramDocuments(context.botKey, chatId, context.messageId, logs);
        } else {
            context.sendTelegramReply("No logs.");
        }
    }
}