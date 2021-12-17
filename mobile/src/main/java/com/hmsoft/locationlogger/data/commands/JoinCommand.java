package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.service.CoreService;

public class JoinCommand extends  Command {

    static final String COMMAND_NAME = "Join";

    @Override
    public boolean isAnyoneAllowed() {
        return true;
    }

    @Override
    public String getSummary() {
        return "Request to talk with me in private.";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {
        if (context.isAllowed) {
            // TODO: Localize strings
            sendTelegramReply("Usted ya puede hablar conmigo en privado, no hace falta que lo pida de nuevo. Sopenco!");
        } else {
            sendTelegramMessageToChannel("Dice " + context.fromFullName + " que quiere hablar en privado conmigo. " + context.fromUserName + " (" + context.fromId + ")");
        }
    }
}
