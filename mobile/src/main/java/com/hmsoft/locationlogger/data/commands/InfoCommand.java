package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.service.LocationService;

class InfoCommand extends Command {

    static final String COMMAND_NAME = "Info";

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {
        String info = Utils.getGeneralInfo(context.androidContext);

        if(context.source == Command.SOURCE_SMS) {
            Utils.sendSms(context.fromId, info, null);
        } else {
            LocationService.updateLocation(context.androidContext, info);
        }
    }
}
