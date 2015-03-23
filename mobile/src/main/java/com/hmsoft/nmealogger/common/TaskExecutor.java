package com.hmsoft.nmealogger.common;

import android.os.Handler;
import android.os.Looper;

public final class TaskExecutor 
{
	//private static final String TAG = "TaskExecutor";
	
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
	
	public synchronized static void executeOnUIThread(Runnable runnable, int seconds) {
		sExecuteOnUIHandler.postDelayed(runnable, seconds * 1000);
	}
}