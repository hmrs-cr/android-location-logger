package com.hmsoft.locationlogger.data.commands;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.PowerManager;

import com.hmsoft.locationlogger.LocationLoggerApp;
import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.common.TaskExecutor;
import com.hmsoft.locationlogger.common.telegram.TelegramHelper;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

class AudioCommand extends Command {

    static final String COMMAND_NAME = "Audio";
    private static final String TAG = "AudioCommand";


    @Override
    public String getSummary() {
        return "Records audio. (Experimental)";
    }

    @Override
    public String getName() {
        return COMMAND_NAME;
    }

    @Override
    public void execute(String[] params, final CommandContext context) {

        PowerManager powerManager = (PowerManager) context.androidContext.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "locatrack:wakelock:audiorecord");
        wakeLock.setReferenceCounted(false);

        long lenp = getLong(getSubParams(params), 0, 30);
        long times = getLong(getSubParams(params), 1, 1);
        final long len = lenp > 180 ? 180 : lenp;

        if (times > 20) {
            times = 20;
        }

        wakeLock.acquire((times * len * 1000) + 10000);

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US);
        MediaRecorder recorder = new MediaRecorder();
        try {
            int count = 0;
            while (count++ < times) {
                try {
                    final String caption = dateFormat.format(new Date());
                    File cacheDir = LocationLoggerApp.getContext().getExternalFilesDir("audio");
                    final File audioFile = File.createTempFile(caption.replace(' ', 'T') + "-", ".MP4A", cacheDir);

                    recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                    recorder.setOutputFile(audioFile.getPath());

                    recorder.setAudioSamplingRate(44100);
                    recorder.setAudioEncodingBitRate(96000);
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

                    recorder.prepare();
                    recorder.start();

                    TaskExecutor.sleep((int) len);

                    recorder.stop();
                    recorder.reset();

                    final String performer = times > 1 ? "Audio " + count + "/" + times: "Audio";
                    final String chatId = context.source == SOURCE_SMS ? context.channelId : context.fromId;
                    TaskExecutor.executeOnNewThread(new Runnable() {
                        @Override
                        public void run() {
                            TelegramHelper.sendTelegramAudio(
                                    context.botKey,
                                    chatId,
                                    context.messageId,
                                    audioFile,
                                    caption,
                                    performer,
                                    len);
                        }
                    });

                } catch (Exception e) {
                    context.sendTelegramReply(e.getMessage());
                    Logger.warning(TAG, "Error recording audio", e);
                }
            }
        } finally {
            recorder.release();
            wakeLock.release();
        }
    }
}
