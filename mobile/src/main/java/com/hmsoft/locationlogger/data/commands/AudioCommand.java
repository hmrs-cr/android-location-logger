package com.hmsoft.locationlogger.data.commands;

import android.media.MediaRecorder;

import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.Utils;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

        long len = getLong(getSubParams(params), 0, 20);
        long times = getLong(getSubParams(params), 1, 1);

        if (len > 120) {
            len = 120;
        }

        if (times > 10) {
            times = 10;
        }

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US);
        MediaRecorder recorder = new MediaRecorder();
        try {
            int count = 0;
            while (count++ < times) {
                try {
                    File cacheDir = context.androidContext.getCacheDir();
                    File audioFile = File.createTempFile("audio-", ".MP4A", cacheDir);
                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    recorder.setOutputFile(audioFile.getPath());

                    recorder.setAudioSamplingRate(44100);
                    recorder.setAudioEncodingBitRate(96000);
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

                    String caption = dateFormat.format(new Date());
                    recorder.prepare();
                    recorder.start();

                    TaskExecutor.sleep((int) len);

                    recorder.stop();
                    recorder.reset();

                    String performer = "Audio";
                    if (times > 1) {
                        performer = "Audio " + count + "/" + times;
                    }

                    TelegramHelper.sendTelegramAudio(
                            context.botKey,
                            context.fromId,
                            context.messageId,
                            audioFile,
                            caption,
                            performer,
                            len);

                } catch (Exception e) {
                    context.sendTelegramReply(e.getMessage());
                }
            }
        } finally {
            recorder.release();
        }
    }
}
