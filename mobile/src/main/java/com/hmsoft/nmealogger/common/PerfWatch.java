package com.hmsoft.nmealogger.common;

public final class PerfWatch {
    //private static final String TAG = "PerfWhatch";
    private long mStarTime;

    private PerfWatch() {
        restart();
    }

    public void restart() {
        mStarTime = System.currentTimeMillis();
    }

    public long stop(String tag, String message) {

        long result = System.currentTimeMillis() - mStarTime;

        String message1 = String.format("%s - Time:%ds (%d)", message, result / 1000, result);
        Logger.info(tag, message1);

        return result;
    }

    public static PerfWatch start(String tag, String message) {
        Logger.debug(tag, message);
        return new PerfWatch();

    }
}