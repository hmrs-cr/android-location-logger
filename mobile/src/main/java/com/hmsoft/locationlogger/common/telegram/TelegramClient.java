package com.hmsoft.locationlogger.common.telegram;

import com.hmsoft.locationlogger.common.telegram.data.MessageResponse;

public class TelegramClient {

    private String mBotKey;

    public TelegramClient(String botKey) {
        mBotKey = botKey;
    }

    public MessageResponse sendMessage(long chatId, String text) {
        return null;
    }

    public MessageResponse sendMessage(long replyToMessageId, long chatId, String text) {
        return null;
    }
}
