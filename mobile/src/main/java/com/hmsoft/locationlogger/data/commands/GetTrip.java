package com.hmsoft.locationlogger.data.commands;

import android.text.TextUtils;
import android.util.Pair;

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
            String id = params[1];
            TripTable.Trip trip = TripTable.getTripbyId(id);
            if (trip != null) {
                Date date = new Date(trip.endTimeStamp);
                String tripString = date + "\n" + trip;
                sendReply(context, tripString);
            } else {
                sendReply(context, "Trip not found.");
            }
        } else if (params.length == 3 && params[2].equals("gpx")) {
            String id = params[1];
            handleGpx(id);
        } else {
            Pair[] trips = TripTable.getTrips();
            String reply = "";
            for (Pair trip : trips) {
                reply += "#" + trip.first + ":  " + trip.second + "\n";
            }
            if(TextUtils.isEmpty(reply)) {
                reply = "No trips found.";
            }
            sendReply(context, reply);
        }
    }

    private void handleGpx(String id) {

        final File gpxFile = new File(context.androidContext.getCacheDir(), "Trip" + id + ".gpx");

        if(!gpxFile.exists()) {
            TripTable.Trip trip = TripTable.getTripbyId(id);
            if (trip == null) {
                sendReply(context, "Trip not found.");
                return;
            }

            try {
                FileWriter writer = new FileWriter(gpxFile, false);
                writer.append(trip.toGpxString());
                writer.flush();
                writer.close();
            } catch (IOException ignored) {

            }

            if(!gpxFile.exists()) {
                sendReply(context, "Failed to create GPX for trip.");
                return;
            }

            TelegramHelper.sendTelegramDocument(context.botKey, context.fromId, context.messageId, gpxFile);
        }
    }
}
