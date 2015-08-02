package com.hmsoft.locationlogger.data;

import android.location.Location;

import com.hmsoft.locationlogger.data.locatrack.LocatrackLocation;

public interface LocationSet extends Iterable<LocatrackLocation> {
    public int getCount();
    public long getDateStart();
    public void setDateStart(long dateStart);
    public long getDateEnd();
    public void setDateEnd(long dateEnd);
    public LocatrackLocation[] toArray();
}
