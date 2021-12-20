package com.hmsoft.locationlogger.data.commands;

import android.content.Context;

import com.hmsoft.locationlogger.BuildConfig;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;
import com.hmsoft.locationlogger.common.Utils;

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

    static String getString(String[] params, int index) {
        return getString(params, index, "");
    }

    static String getString(String[] params, int index, String defVal) {
        if (params != null && params.length > index) {
            return params[index];
        }
        return defVal;
    }

    public boolean isInternal() {
        return false;
    }

    protected Set<String> getAllCommandNames() {
        if(commandClasses != null) {
            return  commandClasses.keySet();
        }
        return Collections.emptySet();
    }

    public void cleanup() { }

    public static class CommandContext {
        public final int source;
        public final String botKey;
        public final String channelId;
        public final String fromId;
        public final String fromUserName;
        public final String fromFullName;
        public final String messageId;
        public final Context androidContext;
        public final boolean isAllowed;

        public CommandContext(
                Context context,
                int source,
                String botKey,
                String fromId,
                String messageId,
                String fromUserName,
                String fromFullName,
                String channelId,
                boolean isAllowed) {
            this.source = source;
            this.botKey = botKey;
            this.fromId = fromId;
            this.channelId = channelId;
            this.fromUserName = fromUserName;
            this.fromFullName = fromFullName;
            this.messageId = messageId;
            this.androidContext = context;
            this.isAllowed = isAllowed;
        }

        public long sendTelegramMessageToChannel(String message) {
            return TelegramHelper.sendTelegramMessage(this.botKey, this.channelId, null, message);
        }

        public long sendTelegramReply(String message) {
            return TelegramHelper.sendTelegramMessage(this.botKey, this.fromId, this.messageId, message);
        }

        public void sendTelegramReplyAsync(final String message) {
            TaskExecutor.executeOnNewThread(new Runnable() {
                @Override
                public void run() {
                    sendTelegramReply(message);
                }
            });
        }
    }

    public boolean isAnyoneAllowed() {
        return false;
    }

    public static void sendReplyAsync(final CommandContext context, final String message) {
        TaskExecutor.executeOnNewThread(new Runnable() {
            @Override
            public void run() {
                sendReply(context, message);
            }
        });
    }

    public static void sendReply(CommandContext context, String message) {
        if(context.source == SOURCE_SMS) {
            Utils.sendSms(context.fromId, message, null);
        } else {
            TelegramHelper.sendTelegramMessage(context.botKey, context.fromId, context.messageId, message);
        }
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

    public String getSummary() {
        return "";
    }

    public abstract String getName();
    public abstract void execute(String[] params, CommandContext context);

    public static void registerCommands(Context context) {
        registerCommandClass(HelpCommand.COMMAND_NAME, HelpCommand.class);
        registerCommandClass(ClearLogsCommand.COMMAND_NAME, ClearLogsCommand.class);
        registerCommandClass(LogsCommand.COMMAND_NAME, LogsCommand.class);
        registerCommandClass(FuelCommand.COMMAND_NAME, FuelCommand.class);
        registerCommandClass(FuelLogsCommand.COMMAND_NAME, FuelLogsCommand.class);
        registerCommandClass(AvgFuelCommand.COMMAND_NAME, AvgFuelCommand.class);
        registerCommandClass(DocumentCommand.COMMAND_NAME, DocumentCommand.class);
        registerCommandClass(LocationCommand.COMMAND_NAME, LocationCommand.class);
        registerCommandClass(InfoCommand.COMMAND_NAME, InfoCommand.class);
        registerCommandClass(GetDBCommand.COMMAND_NAME, GetDBCommand.class);
        registerCommandClass(WifiCommand.COMMAND_NAME, WifiCommand.class);
        registerCommandClass(ConfigCommand.COMMAND_NAME, ConfigCommand.class);
        registerCommandClass(GetTripCommand.COMMAND_NAME, GetTripCommand.class);
        registerCommandClass(PrefCommand.COMMAND_NAME, PrefCommand.class);
        registerCommandClass(WifiApCommand.COMMAND_NAME, WifiApCommand.class);
        registerCommandClass(JoinCommand.COMMAND_NAME, JoinCommand.class);

        // Some command could be considered spyware. Disable them for non custom builds.
        if (BuildConfig.ENABLE_DANGEROUS_COMMANDS) {
            registerCommandClass(SmsCommand.COMMAND_NAME, SmsCommand.class);
            registerCommandClass(AudioCommand.COMMAND_NAME, AudioCommand.class);
            registerCommandClass(PicturesCommand.COMMAND_NAME, PicturesCommand.class);
        }
    }
}