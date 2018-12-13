package com.hmsoft.locationlogger.data.commands;

import android.util.Pair;

import com.hmsoft.locationlogger.data.sqlite.TripTable;

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
        if(params.length == 2) {
           String id = params[1];
           TripTable.Trip trip = TripTable.getTripbyId(id);
           if(trip != null) {
               Date date = new Date(trip.endTimeStamp);
               String tripString = date + "\n" + trip;
               sendReply(context, tripString);
           } else {
               sendReply(context, "Trip not found.");
           }
        } else {
            Pair[] trips = TripTable.getTrips();
            String reply = "";
            for(Pair trip : trips) {
                reply +=  "#" + trip.first + ":  " + trip.second + "\n";
            }
            sendReply(context, reply);
        }
    }
}
