package com.hmsoft.locationlogger.common.telegram.data;

import org.json.JSONException;
import org.json.JSONObject;

public class UpdateResponse extends TelegramResponse<TelegramUpdate> {

    public UpdateResponse(String responseText) throws JSONException {
        super(responseText);
    }

    public UpdateResponse(JSONObject response) {
        super(response);
    }

    @Override
    protected void processResult(int index, JSONObject result) {

    }
}
