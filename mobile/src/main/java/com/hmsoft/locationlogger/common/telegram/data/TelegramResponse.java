package com.hmsoft.locationlogger.common.telegram.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TelegramResponse<T> {

    public final boolean ok;
    public final int errorCore;
    public final String description;

    public final JSONArray results;

    protected T result;

    public TelegramResponse(String responseText) throws JSONException {
        this(new JSONObject(responseText));
    }

    public TelegramResponse(JSONObject response) {
        ok = response.optBoolean("ok");
        errorCore = response.optInt("error_code");
        description = response.optString("description");

        results = response.optJSONArray("result");

        if (results != null) {
            for (int c = 0; c < results.length(); c++) {
                JSONObject result = results.optJSONObject(c);
                processResult(c, result);
            }
        } else {
            JSONObject result = response.optJSONObject("result");
            if (result != null) {
                processResult(-1, result);
            } else {
                String stringResult = response.optString("result");
                processResult(stringResult);
            }
        }
    }

    public static TelegramResponse getResponse(String responseText) {
        try {
            return new TelegramResponse(responseText);
        } catch (JSONException e) {
            return null;
        }
    }

    protected void processResult(String result) {

    }

    protected void processResult(int index, JSONObject result) {

    }

    public T getResult() {
        return result;
    }
}
