package com.hmsoft.locationlogger.data.commands;

import android.text.TextUtils;

import com.hmsoft.locationlogger.common.telegram.TelegramHelper;
import com.hmsoft.locationlogger.data.sqlite.TripTable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class GetTripCommand extends Command {
    static final String COMMAND_NAME = "GetTrip";
    private String[] supParams;
    private static final SimpleDateFormat TripDateFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
    private static final SimpleDateFormat TripDateTimeFormat = new SimpleDateFormat("yyyyMMddHHmm", Locale.US);

    @Override
    public String getSummary() {
        return "Get a list of the latest trips. _getTrip [tripid]_ or _getTrip gpx startDate endDate_";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params, CommandContext context) {
        if (params.length == 2) {
            String[] supParams = params[1].split(" ", 3);

            String id = supParams[0];
            if(id.endsWith("all")) {
                handleTripList(true, context);
            } else if(supParams.length == 1) {
                handleSingle(id, context);
            } else if(supParams.length == 2 && supParams[1].equals("gpx")) {
                handleGpx(id, context);
            } else if(supParams.length == 3 && supParams[0].equals("gpx")) {
                 handleGpx(supParams[1], supParams[2], context);
            }
        } else {
            handleTripList(false, context);
        }
    }

    private static Date parseDateString(String dateStr, int offSet) throws ParseException {
        try {
            return TripDateTimeFormat.parse(dateStr);
        } catch (ParseException e) {
            Date date  = TripDateFormat.parse(dateStr);
            if(offSet > 0) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                cal.add(Calendar.HOUR_OF_DAY, offSet);
                date = cal.getTime();
            }
            return date;
        }
    }

    private void handleGpx(String startDateStr, String endDateStr, CommandContext context) {
        try {
            Date startDate = parseDateString(startDateStr, 0);
            Date endDate = parseDateString(endDateStr, 24);

            TripTable.TripDetail trip = TripTable.TripDetail.createTrip(startDate.getTime(), endDate.getTime(), 0f);
            handleGpx(trip, startDateStr + "-" + endDateStr, context);
        } catch (ParseException e) {
            sendReply(context, "Wrong date format.");
            e.printStackTrace();
        }
    }

    private void handleSingle(String id, CommandContext context) {

        TripTable.TripDetail trip = TripTable.getTripbyId(id);
        if (trip != null) {
            Date date = new Date(trip.endTimeStamp);
            String tripString = date + "\n" + trip;
            sendReply(context, tripString);
        } else {
            sendReply(context, "Trip not found.");
        }
    }

    private void handleTripList(boolean all, CommandContext context) {
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

    private void handleGpx(String id, CommandContext context) {

        TripTable.TripDetail trip = null;
        if("last".equals(id)) {
            trip = TripTable.getTripbyId(id);
            if (trip != null) {
                id = trip.id;
            }
        }

        handleGpx(trip, id, context);
    }

    private void handleGpx(TripTable.TripDetail trip, String id, CommandContext context) {


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
