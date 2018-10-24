package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.LocationLoggerApp;
import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.data.sqlite.FuelLogTable;
import com.hmsoft.locationlogger.data.sqlite.Helper;

class FuelCommand extends Command {

    static final String COMMAND_NAME = "Fuel";

    @Override
    public String getSummary() {
        return "Log fuel consuptions: _fuel ODO AMOUNT_.";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {
        if (params.length == 2) {
            try {
                String[] fuelData = params[1].split(" ");
                if (fuelData.length == 2) {
                    int odo = Integer.parseInt(fuelData[0].toLowerCase().replace("km", "").trim());
                    double amount = Double.parseDouble(fuelData[1].trim());
                    FuelLogTable.logFuel(Helper.getInstance(), Utils.getBestLastLocation(LocationLoggerApp.getContext()), odo, amount);
                    double consuption = FuelLogTable.getAvgConsuption(Helper.getInstance());
                    sendTelegramReply("Average: " + consuption + " CRC/KM");
                }
            } catch (Exception e) {
                sendTelegramReply("Error: " + e.getMessage());
            }
        }
    }
}
