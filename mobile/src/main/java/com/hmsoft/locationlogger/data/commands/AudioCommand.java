package com.hmsoft.locationlogger.data.commands;

import android.media.MediaRecorder;

import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;

import java.io.File;
import java.util.Date;

class AudioCommand extends Command {

    static final String COMMAND_NAME = "Audio";


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
            MediaRecorder mRecorder = new MediaRecorder();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFile(audioFile.getPath());

            mRecorder.setAudioSamplingRate(44100);
            mRecorder.setAudioEncodingBitRate(96000);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

            mRecorder.prepare();
            mRecorder.start();

            String caption = Utils.dateFormat.format(new Date());
            long len = getLong(getSubParams(params), 0, 20);
            TaskExecutor.sleep((int)len);

            mRecorder.stop();
            mRecorder.release();

            TelegramHelper.sendTelegramAudio(
                    context.botKey,
                    context.fromId,
                    context.messageId,
                    audioFile,
                    caption,
                    len);

        } catch (Exception e) {
            context.sendTelegramReply(e.getMessage());
        }
    }
}
