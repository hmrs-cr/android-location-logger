package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.service.LocationService;

class LocationCommand extends Command {

    static final String COMMAND_NAME = "Location";

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {
        String info = params.length > 1 ? params[1] : "Location";
        LocationService.updateLocation(context.androidContext, info);
    }
}
