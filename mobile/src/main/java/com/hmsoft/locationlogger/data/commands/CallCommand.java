package com.hmsoft.locationlogger.data.commands;

import android.content.Intent;
import android.net.Uri;

import com.hmsoft.locationlogger.common.Utils;

public class CallCommand extends Command  {

    static final String COMMAND_NAME = "Call";

    @Override
    public String getSummary() {
        return "Calls the specified number. _call NUMBER_";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params, CommandContext context) {
        if (params.length == 2) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            String number = params[1];
            if ("me".equals(number) && context.source == SOURCE_SMS) {
                number = context.fromId;
            }
            callIntent.setData(Uri.parse("tel:" + number));
            callIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.androidContext.getApplicationContext().startActivity(callIntent);
        }
    }
}
