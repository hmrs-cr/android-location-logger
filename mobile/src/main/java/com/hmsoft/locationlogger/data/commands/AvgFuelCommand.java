package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.data.sqlite.FuelLogTable;
import com.hmsoft.locationlogger.data.sqlite.Helper;

import java.text.SimpleDateFormat;
import java.util.Locale;

class AvgFuelCommand extends Command {

    static final String COMMAND_NAME = "FuelAvg";

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
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.US);

        FuelLogTable.Statics statics = FuelLogTable.getMostRecentStatics();
        double consuption = FuelLogTable.getAvgConsuption();
        sendTelegramReply("*Last: *" + statics.km + " km, " + statics.litres + "L, " + statics.avg + " km/L (" + dateFormat.format(statics.startDate) + " - " + dateFormat.format(statics.endDate) + ")" +
                "\n*Overall:* " + consuption + " km/L");
    }
}
