package com.hmsoft.locationlogger.data.commands;

import com.hmsoft.locationlogger.common.Utils;

class SmsCommand extends Command {

    static final String COMMAND_NAME = "Sms";

    @Override
    public String getSummary() {
        return "Sent SMS message. _sms TO_NUMBER MESSAGE_TEXT_";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {

        if(params.length == 2) {
           String[] smsData = params[1].split(" ", 2);
            if (smsData.length == 2) {
                String number = smsData[0];
                String smsText = smsData[1];
                Utils.sendSms(number, smsText, null);
            }
        }
    }
}
