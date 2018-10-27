package com.hmsoft.locationlogger.data.commands;

public abstract class InternalCommand extends Command {

    @Override
    public String getSummary() {
        return "Internal usage only.";
    }

    @Override
    public boolean isInternal() {
        return true;
    }

}
