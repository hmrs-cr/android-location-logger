package com.hmsoft.locationlogger.data.commands;

import android.text.TextUtils;

import com.hmsoft.locationlogger.common.telegram.TelegramHelper;
import com.hmsoft.locationlogger.data.sqlite.TripTable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;

public class GetTrip extends Command {
    static final String COMMAND_NAME = "GetTrip";

    @Override
    public String getSummary() {
        return "Get a list of the latest trips. _getTrip [tripid]_";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {
        if (params.length == 2) {
            String[] supParams = params[1].split(" ", 2);

            String id = supParams[0];
            if(id.endsWith("all")) {
                handleTripList(true);
            } else if(supParams.length == 1) {
                HandleSingle(id);
            } else if(supParams.length == 2 && supParams[1].equals("gpx")) {
                handleGpx(id);
            }
        } else {
            handleTripList(false);
        }
    }

    private void HandleSingle(String id) {

        TripTable.TripDetail trip = TripTable.getTripbyId(id);
        if (trip != null) {
            Date date = new Date(trip.endTimeStamp);
            String tripString = date + "\n" + trip;
            sendReply(context, tripString);
        } else {
            sendReply(context, "Trip not found.");
        }
    }

    private void handleTripList(boolean all) {
        TripTable.Trip[] trips = TripTable.getTrips(all ? -1 : 0);
        String reply = "";
        for (TripTable.Trip trip : trips) {
            reply += trip + "\n";
        }
        if(TextUtils.isEmpty(reply)) {
            reply = "No trips found.";
        }
        sendReply(context, reply);
    }

    private void handleGpx(String id) {

        TripTable.TripDetail trip = null;
        if("last".equals(id)) {
            trip = TripTable.getTripbyId(id);
            if (trip != null) {
                id = trip.id;
            }
        }

        final File gpxFile = new File(context.androidContext.getCacheDir(), "Trip-" + id + ".gpx");
        if (!gpxFile.exists()) {
            if(trip == null) {
                trip = TripTable.getTripbyId(id);
                if(trip == null) {
                    sendReply(context, "Trip not found.");
                }
            }
            try {
                FileWriter writer = new FileWriter(gpxFile, false);
                writer.append(trip.toGpxString());
                writer.flush();
                writer.close();
            } catch (IOException ignored) {

            }

            if (!gpxFile.exists()) {
                sendReply(context, "Failed to create GPX for trip.");
                return;
            }
        }

        TelegramHelper.sendTelegramDocument(context.botKey, context.fromId, context.messageId, gpxFile);
    }
}
