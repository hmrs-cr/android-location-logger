package com.hmsoft.locationlogger.data.commands;

import android.content.Intent;
import android.net.Uri;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;
import com.hmsoft.locationlogger.data.LocatrackLocation;
import com.hmsoft.locationlogger.data.locatrack.LocatrackDb;
import com.hmsoft.locationlogger.data.sqlite.GeoFenceTable;
import com.hmsoft.locationlogger.data.sqlite.TripTable;
import com.hmsoft.locationlogger.service.CoreService;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class GeofenceCommand extends Command  {

    static final String COMMAND_NAME = "Geofence";

    @Override
    public String getSummary() {
        return "Gets or adds a geofence label in the current localtion. _geofence loc:lat,lon radio:[radio],[label]_";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params, CommandContext context) {
        String info = LocatrackLocation.INFO_GET_GEOFENCE_LABEL;
        if (params.length == 2) {
            if ("gpx".equals(params[1])) {
                generateGpx(context);
                return;
            }
            if (params[1].startsWith("loc:")) {
                params[1] = params[1].replace("loc:", "");
                int i = params[1].indexOf(" ");
                if (i > 1) {
                    String latlon = params[1].substring(0, i);
                    params[1] = params[1].substring(i);
                    String[] latlonVals = latlon.split(",");
                    if (latlonVals.length == 2) {
                        try {
                            double lat = Double.parseDouble(latlonVals[0]);
                            double lon = Double.parseDouble(latlonVals[1]);
                            LocatrackDb.saveGeoFence(params[1].trim(), lat, lon);
                            sendReply(context, "Geofence updated: " + lat + "," + lon + ": " + params[1]);
                            return;
                        } catch (Exception e) {
                            sendReply(context, "Wrong format");
                            return;
                        }
                    } else {
                        sendReply(context, "Wrong format");
                        return;
                    }
                }
            }

            info = LocatrackLocation.INFO_SET_GEOFENCE_LABEL + params[1];
        }

        String chatId = context.source == SOURCE_SMS ? context.channelId : context.fromId;
        CoreService.updateLocation(context.androidContext, info, context.messageId, chatId);
    }

    private void generateGpx(CommandContext context) {
        final File gpxFile = new File(context.androidContext.getCacheDir(), "GeofenceWaypoints.gpx");

        try {
            FileWriter writer = new FileWriter(gpxFile, false);
            writer.append(GeoFenceTable.exportGpx());
            writer.flush();
            writer.close();
        } catch (IOException ignored) {

        }

        if (!gpxFile.exists()) {
            sendReply(context, "Failed to create GPX for geofence waypoints.");
            return;
        }

        String chatId = context.source == SOURCE_SMS ? context.channelId : context.fromId;
        TelegramHelper.sendTelegramDocument(context.botKey, chatId, context.messageId, gpxFile, null);
    }
}
