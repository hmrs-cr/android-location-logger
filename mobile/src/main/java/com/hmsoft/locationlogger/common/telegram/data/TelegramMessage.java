package com.hmsoft.locationlogger.common.telegram.data;

import org.json.JSONObject;


public class TelegramMessage {

    public final long date;
    public final long id;
    public final String text;
    public final TelegramChat chat;
    public final TelegramDocument document;

    TelegramMessage(JSONObject result) {
        date = result.optLong("date");
        id = result.optLong("message_id");
        text = result.optString("text");

        JSONObject chatObj = result.optJSONObject("chat");
        chat =  chatObj != null ? new TelegramChat(chatObj) : null;

        JSONObject documentObj = result.optJSONObject("document");
        document =  documentObj != null ? new TelegramDocument(documentObj) : null;
    }
}

