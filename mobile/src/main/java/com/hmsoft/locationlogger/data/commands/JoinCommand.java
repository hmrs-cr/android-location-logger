package com.hmsoft.locationlogger.data.commands;

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
    public void execute(String[] params, CommandContext context) {
        if (context.isAllowed) {
            // TODO: Localize strings
            context.sendTelegramReply("Usted ya puede hablar conmigo en privado, no hace falta que lo pida de nuevo. Sopenco!");
        } else {
            context.sendTelegramMessageToChannel("Dice " + context.fromFullName + " que quiere hablar en privado conmigo. " + context.fromUserName + " (" + context.fromId + ")");
        }
    }
}
