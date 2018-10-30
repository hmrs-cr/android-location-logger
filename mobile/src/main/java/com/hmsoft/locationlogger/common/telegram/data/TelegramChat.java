package com.hmsoft.locationlogger.common.telegram.data;

import org.json.JSONObject;

public class TelegramChat {
    public final long id;
    public final String title;
    public final String type;

    public final String firstName;
    public final String lastName;


    TelegramChat(JSONObject result) {
        id = result.optLong("id");
        type = result.optString("type");

        firstName = result.optString("first_name");
        lastName = result.optString("last_name");

        String title = result.optString("text");
        if(title == null && firstName != null && lastName != null) {
            title = firstName + " " + lastName;
        }

        this.title = title;
    }
}
