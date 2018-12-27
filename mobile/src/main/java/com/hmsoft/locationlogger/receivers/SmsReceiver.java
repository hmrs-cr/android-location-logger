package com.hmsoft.locationlogger.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.widget.Toast;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.service.CoreService;

public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        if (Logger.DEBUG) {
            Logger.debug(TAG, "onReceive:%s", intent);
            Toast.makeText(context, "" + intent, Toast.LENGTH_LONG).show();
        }

        SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (messages == null) {
            return;
        }

        for (SmsMessage smsMessage : messages) {

            String smsBody = smsMessage.getMessageBody();
            String address = smsMessage.getOriginatingAddress();

            CoreService.handleSms(context, address, smsBody);
        }
    }
}