package com.hmsoft.locationlogger.data;

import android.location.Location;
import android.os.Bundle;

import java.io.IOException;

public abstract class LocationStorer {

    public static final String EXTRA_TOTAL_ITEMS = "TOTAL_ITEMS";
    public static final String EXTRA_TOTAL_SUCCESS_ITEMS = "TOTAL_SUCCESS_ITEMS";
    public static final String EXTRA_TOTAL_FAIL_ITEMS = "TOTAL_FAIL_ITEMS";

    protected Exception mException = null;
    protected OnCloseCallback mOnCloseCallback = null;
    protected Bundle mOnCloseCallbackExtras = null;
    protected int mTotalItems = 0;
    protected int mTotalSuccess = 0;
    protected int mTotalFail = 0;

	public abstract boolean storeLocation(LocatrackLocation location);
	public abstract LocationStorer configure();

    public void setUploadDateToday(Location location) {

    }

    public interface OnCloseCallback {
        void onClose(Bundle extras, Exception error);
    }

    public void setOnCloseCallback(OnCloseCallback mOnCloseCallback) {
        this.mOnCloseCallback = mOnCloseCallback;
    }

    public void open()  throws IOException {
        mTotalFail = 0;
        mTotalItems = 0;
        mTotalSuccess = 0;
    }

    public void close() {
        if(mOnCloseCallback != null) {
            if (mOnCloseCallbackExtras == null) {
                mOnCloseCallbackExtras = new Bundle();
            }
            mOnCloseCallbackExtras.putInt(EXTRA_TOTAL_ITEMS, mTotalItems);
            mOnCloseCallbackExtras.putInt(EXTRA_TOTAL_SUCCESS_ITEMS, mTotalSuccess);
            mOnCloseCallbackExtras.putInt(EXTRA_TOTAL_FAIL_ITEMS, mTotalFail);

            mOnCloseCallback.onClose(mOnCloseCallbackExtras, mException);
        }
    }

    public void setException(Exception e) {
        mException = e;
    }

}
