package com.hmsoft.locationlogger.data.commands;

import android.content.Context;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TelegramHelper;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public abstract class Command {

    private static final String TAG = "Command";

    private static Map<String, Class> commandClasses;
    private static Map<String, Command> commandInstances;

    public static final int SOURCE_SMS = 1;
    public static final int SOURCE_TELEGRAM = 2;

    public static void cleanupAll() {
        if(commandInstances != null) {
            for(Command command : commandInstances.values()) {
                command.cleanup();
            }
            commandInstances.clear();
        }
    }

    static String[] getSubParams(String[] params) {
        if(params != null && params.length == 2) {
            return params[1].split(" ");
        }
        return new String[0];
    }
    static boolean contains(String[] params, String value) {
        for(String val : params) {
            if(val.equals(value)) {
                return true;
            }
        }
        return false;
    }

    static long getLong(String[] params, int index, long defVal) {
        if(params != null && params.length > index) {
            try {
                return Long.valueOf(params[index]);
            } catch (NumberFormatException e) {

            }
        }
        return defVal;
    }

    static String getString(String[] params, int index, String defVal) {
        if (params != null && params.length > index) {
            return params[index];
        }
        return defVal;
    }

    protected Set<String> getAllCommandNames() {
        if(commandClasses != null) {
            return  commandClasses.keySet();
        }
        return Collections.emptySet();
    }

    public void cleanup() {
        context = null;
    }

    public static class CommandContext {
        public final int source;
        public final String botKey;
        public final String fromId;
        public final String messageId;
        public final Context androidContext;

        public CommandContext(Context context, int source, String botKey, String fromId, String messageId) {
            this.source = source;
            this.botKey = botKey;
            this.fromId = fromId;
            this.messageId = messageId;
            this.androidContext = context;
        }
    }

    protected CommandContext context;

    public void setContext(CommandContext context) {
        this.context = context;
    }

    public void setContext(Context context, int source, String botKey, String fromId, String messageId) {
        setContext(new CommandContext(context, source, botKey, fromId, messageId));
    }

    protected void sendTelegramReply(String message) {
        TelegramHelper.sendTelegramMessage(context.botKey, context.fromId, context.messageId, message);
    }

    public static Command getCommand(String command) {
        if (commandInstances == null) {
            commandInstances = new HashMap<>();
        }

        command = command.toLowerCase();
        Command commandInstance = commandInstances.get(command);
        if (commandInstance != null) {
            return commandInstance;
        }

        if (commandClasses == null) {
            return null;
        }

        Class commandClass = commandClasses.get(command);
        if (commandClass == null) {
            return null;
        }

        try {
            commandInstance = (Command) commandClass.newInstance();
            commandInstances.put(commandInstance.getName().toLowerCase(), commandInstance);
            return commandInstance;
        } catch (Exception e) {
            Logger.error(TAG, e.getMessage());
            return null;
        }
    }

    public static void registerCommandClass(String commandName, Class commandClass) {
        if(commandClasses == null) {
            commandClasses = new HashMap<>();
        }
        commandClasses.put(commandName.toLowerCase(), commandClass);
    }

    public static void registerCommand(Command command) {
        if (commandInstances == null) {
            commandInstances = new HashMap<>();
        }
        commandInstances.put(command.getName().toLowerCase(), command);
    }

    public String getSummary() {
        return "";
    }

    public abstract String getName();
    public abstract void execute(String[] params);

    public static void registerCommands() {
        registerCommandClass(ClearLogsCommand.COMMAND_NAME, ClearLogsCommand.class);
        registerCommandClass(LogsCommand.COMMAND_NAME, LogsCommand.class);
        registerCommandClass(SmsCommand.COMMAND_NAME, SmsCommand.class);
        registerCommandClass(FuelCommand.COMMAND_NAME, FuelCommand.class);
        registerCommandClass(FuelLogsCommand.COMMAND_NAME, FuelLogsCommand.class);
        registerCommandClass(AvgFuelCommand.COMMAND_NAME, AvgFuelCommand.class);
        registerCommandClass(DocumentCommand.COMMAND_NAME, DocumentCommand.class);
        registerCommandClass(LocationCommand.COMMAND_NAME, LocationCommand.class);
        registerCommandClass(InfoCommand.COMMAND_NAME, InfoCommand.class);
        registerCommandClass(BalanceCommand.COMMAND_NAME, BalanceCommand.class);
        registerCommandClass(GetDBCommand.COMMAND_NAME, GetDBCommand.class);
        registerCommandClass(HelpCommand.COMMAND_NAME, HelpCommand.class);
    }
}