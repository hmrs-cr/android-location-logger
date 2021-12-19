package com.hmsoft.locationlogger.data.commands;

import android.media.MediaRecorder;

import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;

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
    public void execute(String[] params, CommandContext context) {


        try {
            File cacheDir = context.androidContext.getCacheDir();
            File audioFile = File.createTempFile("audio-", ".MP4A", cacheDir);
            mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFile(audioFile.getPath());

            mRecorder.setAudioSamplingRate(44100);
            mRecorder.setAudioEncodingBitRate(96000);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);


            mRecorder.prepare();


            mRecorder.start();

            long len = getLong(getSubParams(params), 0, 15);
            TaskExecutor.sleep((int)len);

            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;

            TelegramHelper.sendTelegramDocument(context.botKey, context.fromId, context.messageId, audioFile);

        } catch (Exception e) {
            context.sendTelegramReply(e.getMessage());
        }
    }
}
