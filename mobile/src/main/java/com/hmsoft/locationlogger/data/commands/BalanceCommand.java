package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.service.CoreService;

class BalanceCommand extends Command {

    static final String COMMAND_NAME = "Saldo";

    @Override
    public String getSummary() {
        return "Mobile balance.";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {
        CoreService.sendBalamceSms(context.androidContext);
    }
}
