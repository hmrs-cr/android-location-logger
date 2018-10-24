package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.common.Logger;

class ClearLogsCommand extends Command {

    static final String COMMAND_NAME = "ClearLogs";

    @Override
    public String getSummary() {
        return "Remove all internal logs.";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {
        int count = Logger.clearLogs();
        sendTelegramReply(count + " logs removed.");
    }
}