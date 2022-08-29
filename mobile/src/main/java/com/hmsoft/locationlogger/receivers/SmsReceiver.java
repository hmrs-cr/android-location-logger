package com.hmsoft.locationlogger.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.widget.Toast;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.service.CoreService;

public class SmsReceiver extends BroadcastReceiver {

    private static SmsReceiver sInstance = null;

    private static final String TAG = "SmsReceiver";

    public static void register(Context context){
        if (sInstance == null) {
            sInstance = new SmsReceiver();
            IntentFilter filter = new IntentFilter();
            filter.addAction(Telephony.Sms.Intents.SMS_RECEIVED_ACTION);
            context.registerReceiver(sInstance, filter);

            if(Logger.DEBUG) {
                Logger.debug(TAG, "Registered");
            }
        }
    }

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

            if (Logger.DEBUG) Logger.debug(TAG, "SMS received:" + address + ", Body: " + smsBody);

            CoreService.handleSms(context, address, smsBody);
        }
    }
}