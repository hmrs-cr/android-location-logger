package com.hmsoft.locationlogger.data.commands;

import android.media.MediaRecorder;

import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.TelegramHelper;

import java.io.File;

class AudioCommand extends Command {

    static final String COMMAND_NAME = "Audio";
    private MediaRecorder mRecorder;


    @Override
    public String getSummary() {
        return "Records audio. (Experimental)";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params) {


        try {
            File cacheDir = context.androidContext.getCacheDir();
            File audioFile = File.createTempFile("audio", "", cacheDir);
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mRecorder.setOutputFile(audioFile.getPath());
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);


            mRecorder.prepare();


            mRecorder.start();

            long len = getLong(getSubParams(params), 0, 15);
            TaskExecutor.sleep((int)len);

            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;

            TelegramHelper.sendTelegramDocument(context.botKey, context.fromId, context.messageId, audioFile);

        } catch (Exception e) {
            sendTelegramReply(e.getMessage());
        }
    }
}
