package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.service.CoreService;

class InfoCommand extends Command {

    static final String COMMAND_NAME = "Info";

    @Override
    public String getSummary() {
        return "General application info.";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params, CommandContext context) {
        String info = Utils.getGeneralInfo(context.androidContext);
        sendReply(context, info);
    }
}
