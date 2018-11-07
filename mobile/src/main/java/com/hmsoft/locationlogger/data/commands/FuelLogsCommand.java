package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.data.sqlite.FuelLogTable;
import com.hmsoft.locationlogger.data.sqlite.Helper;

class FuelLogsCommand extends Command {

    static final String COMMAND_NAME = "FuelLogs";

    @Override
    public String getSummary() {
        return "Fuel consumption logs. _fuelLogs [MAX]_";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {

        String[] subParams = getSubParams(params);

        switch (getString(subParams, 0, "")) {
            case "delete":
                FuelLogTable.delete(Helper.getInstance(), getLong(subParams, 1, -1));
                return;

            case "update":
                return;
        }


        boolean printIds = contains(subParams, "ids");
        long limit = getLong(subParams, 0, 0);

        FuelLogTable.FuelLog[] logs;
        if(limit < 436687200000L) {
            logs = FuelLogTable.getLogs(Helper.getInstance(), (int)limit);
        } else {
            logs = new FuelLogTable.FuelLog[] { FuelLogTable.getById(Helper.getInstance(), limit) };
        }
        String message = "*Date                    ODO       Spent         Price       Litres*\n";
        for (FuelLogTable.FuelLog log : logs) {
            String id = printIds ?  log.date.getTime() + " " : "";
            message = message + id + log + "\n";
        }
        sendTelegramReply(message);
    }
}

