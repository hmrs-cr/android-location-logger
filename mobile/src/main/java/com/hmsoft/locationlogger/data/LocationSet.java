package com.hmsoft.locationlogger.data;

public interface LocationSet extends Iterable<LocatrackLocation> {
    int getCount();
    LocatrackLocation[] toArray();
}
