package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.data.sqlite.FuelLogTable;
import com.hmsoft.locationlogger.data.sqlite.Helper;

class FuelLogsCommand extends Command {

    static final String COMMAND_NAME = "FuelLogs";

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {
        int limit = 0;
        if (params.length == 2) {
            try {
                limit = Integer.valueOf(params[1]);
            } catch (NumberFormatException e) {

            }
        }
        FuelLogTable.FuelLog[] logs = FuelLogTable.getLogs(Helper.getInstance(), limit);
        String message = "";
        for (FuelLogTable.FuelLog log : logs) {
            message = message + log + "\n";
        }
        sendTelegramReply(message);
    }
}

