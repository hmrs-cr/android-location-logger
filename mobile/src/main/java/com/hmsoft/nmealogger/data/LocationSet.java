package com.hmsoft.nmealogger.data;

import android.location.Location;

public interface LocationSet extends Iterable<Location> {
    public int getCount();
    public long getDateStart();
    public void setDateStart(long dateStart);
    public long getDateEnd();
    public void setDateEnd(long dateEnd);
    public Location[] toArray();
}
