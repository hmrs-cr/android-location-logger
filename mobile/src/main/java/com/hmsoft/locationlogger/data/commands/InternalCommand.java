package com.hmsoft.locationlogger.data.commands;

public abstract class InternalCommand extends Command {

    @Override
    public boolean isInternal() {
        return true;
    }

}
