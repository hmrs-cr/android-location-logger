package com.hmsoft.locationlogger.common.telegram.data;

import org.json.JSONObject;


public class Message {

    public final long date;
    public final long id;
    public final String text;

    Message(JSONObject result) {
        date = result.optLong("date");
        id = result.optLong("message_id");
        text = result.optString("text");
    }
}

