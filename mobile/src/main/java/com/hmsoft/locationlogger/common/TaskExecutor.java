package com.hmsoft.locationlogger.common;

import android.os.Handler;
import android.os.Looper;

public final class TaskExecutor 
{
	private static final String TAG = "TaskExecutor";
	
	private static Handler sExecuteOnUIHandler = null;
	
	private TaskExecutor() {
		
	}
	
	public static void init() {
		if(sExecuteOnUIHandler == null && isMainUIThread())
			sExecuteOnUIHandler = new Handler();
	}
	
	public static boolean isMainUIThread() {
        return Looper.getMainLooper().getThread().equals(Thread.currentThread());
	}
	
	public static Thread executeOnNewThread(Runnable runable){
		Thread thread = new Thread(runable);
		thread.start();
		return thread;
	}
	
	public synchronized static void executeOnUIThread(Runnable runnable) {
		sExecuteOnUIHandler.post(runnable);
	}
	
	public synchronized static Runnable executeOnUIThread(Runnable runnable, int seconds) {
		sExecuteOnUIHandler.postDelayed(runnable, seconds * 1000);
		return  runnable;
	}

	public synchronized static void removeFromUIThread(Runnable runnable) {
		sExecuteOnUIHandler.removeCallbacks(runnable);
	}

	public static void sleep(int seconds) {
		if(seconds < 1) return;
		try {
            if(Logger.DEBUG) Logger.debug(TAG, "Sleeping %d seconds", seconds);
			Thread.sleep(seconds * 1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
}