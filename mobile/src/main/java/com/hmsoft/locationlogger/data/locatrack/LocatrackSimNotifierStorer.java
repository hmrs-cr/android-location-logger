package com.hmsoft.locationlogger.data.locatrack;

import android.content.Context;

import com.hmsoft.locationlogger.data.LocationStorer;
import com.hmsoft.locationlogger.data.LocatrackLocation;

public class LocatrackSimNotifierStorer extends LocationStorer  {

    private Context mContext;
    private LocatrackOnlineStorer mOnlineStorer;

    public LocatrackSimNotifierStorer(Context context) {
        super();
        mContext = context;

        mOnlineStorer = new LocatrackOnlineStorer(context);

    }

    @Override
    public boolean storeLocation(LocatrackLocation location) {
        mOnlineStorer.storeLocation(location);
    }

    @Override
    public void configure() {
        mOnlineStorer.configure();
        mOnlineStorer.retryCount = 11;
        mOnlineStorer.retryDelaySeconds = 5;
    }
}
