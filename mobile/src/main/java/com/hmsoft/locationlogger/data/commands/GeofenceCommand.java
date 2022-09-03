package com.hmsoft.locationlogger.data.commands;

import android.content.Intent;
import android.net.Uri;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.data.LocatrackLocation;
import com.hmsoft.locationlogger.service.CoreService;

public class GeofenceCommand extends Command  {

    static final String COMMAND_NAME = "Geofence";

    @Override
    public String getSummary() {
        return "Gets or adds a geofence label in the current localtion. _geofence radio:[radio],[label]_";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params, CommandContext context) {
        String info = LocatrackLocation.INFO_GET_GEOFENCE_LABEL;
        if (params.length == 2) {
            info = LocatrackLocation.INFO_SET_GEOFENCE_LABEL + params[1];
        }

        String chatId = context.source == SOURCE_SMS ? context.channelId : context.fromId;
        CoreService.updateLocation(context.androidContext, info, context.messageId, chatId);
    }
}
