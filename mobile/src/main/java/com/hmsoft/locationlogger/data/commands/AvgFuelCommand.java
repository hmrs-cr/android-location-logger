package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.data.sqlite.FuelLogTable;
import com.hmsoft.locationlogger.data.sqlite.Helper;

 class AvgFuelCommand extends Command {

    static final String COMMAND_NAME = "AvgFuel";

     @Override
     public String getSummary() {
         return "Overall average fuel consumption.";
     }

     @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {
        double consuption = FuelLogTable.getAvgConsuption(Helper.getInstance());
        sendTelegramReply("Average: " + consuption + " CRC/KM");
    }
}
