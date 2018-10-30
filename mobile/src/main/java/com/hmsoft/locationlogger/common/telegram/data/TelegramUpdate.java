package com.hmsoft.locationlogger.common.telegram.data;

import org.json.JSONObject;

public class TelegramUpdate {
    public final long id;
    public final TelegramMessage message;

    TelegramUpdate(JSONObject result) {
        id = result.optLong("update_id");

        JSONObject messageObj = result.optJSONObject("channel_post");
        if(messageObj == null) {
            messageObj = result.optJSONObject("message");
        }

        message = messageObj != null ? new TelegramMessage(messageObj) : null;

    }
}
