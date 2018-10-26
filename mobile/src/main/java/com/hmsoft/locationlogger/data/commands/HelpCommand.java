package com.hmsoft.locationlogger.data.commands;

class HelpCommand extends Command {
    static final String COMMAND_NAME = "Help";

    @Override
    public String getSummary() {
        return "Command help. _help [COMMAND]_";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {
        String helpText = "";
        if(params.length == 2) {
            Command command = getCommand(params[1]);
            if(command != null && !command.isInternal()) {
                helpText = "*" + command.getName() + "*  " + command.getSummary();
            } else {
                helpText = "Command _" + params[1] + "_ not found.";
            }
        } else {
            for (String commandName : getAllCommandNames()) {
                Command command = getCommand(commandName);
                if(!command.isInternal()) {
                    helpText += "*" + command.getName() + "*  " + command.getSummary() + "\n";
                }
            }
        }
        sendTelegramReply(helpText);
    }
}
