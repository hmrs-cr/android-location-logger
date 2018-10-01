package com.hmsoft.locationlogger.data.locatrack;

import android.content.Context;

import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;

public class LocatrackSimNotifierStorer extends LocationStorer  {

    private LocatrackOnlineStorer mOnlineStorer;
    private LocatrackTelegramStorer mTelegramStorer;

    public LocatrackSimNotifierStorer(Context context) {
        super();

        mOnlineStorer = new LocatrackOnlineStorer(context);
        mTelegramStorer = new LocatrackTelegramStorer(context);

    }

    @Override
    public boolean storeLocation(LocatrackLocation location) {
        mTelegramStorer.storeLocation(location);
        mOnlineStorer.storeLocation(location);
        return true;
    }

    @Override
    public void configure() {
        mTelegramStorer.configure();
        mOnlineStorer.configure();
        mOnlineStorer.retryCount = 11;
        mOnlineStorer.retryDelaySeconds = 5;
    }
}
