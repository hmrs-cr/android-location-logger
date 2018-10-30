package com.hmsoft.locationlogger.common.telegram.data;

import org.json.JSONException;
import org.json.JSONObject;

public class MessageResponse extends TelegramResponse<TelegramMessage> {

    public MessageResponse(String responseText) throws JSONException {
        super(responseText);
    }

    public MessageResponse(JSONObject response) {
        super(response);
    }

    @Override
    protected void processResult(int index, JSONObject jsonResult) {
        result = new TelegramMessage(jsonResult);
    }
}
