package com.hmsoft.locationlogger.common.telegram.data;

import org.json.JSONObject;

public class TelegramDocument {

    public final String id;
    public final String fileName;
    public final long fileSize;
    public final String mimeType;

    TelegramDocument(JSONObject result) {
        id = result.optString("file_id");
        fileName = result.optString("file_name");
        fileSize = result.optLong("file_size");
        mimeType = result.optString("mime_type");
    }
}
