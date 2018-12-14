package com.hmsoft.locationlogger.data.sqlite;

import android.database.Cursor;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.data.LocationSet;
import com.hmsoft.locationlogger.data.LocatrackLocation;

import java.util.Iterator;

public class DatabaseLocationSet implements LocationSet, Iterator<LocatrackLocation> {

    private final String TAG = "DatabaseLocationSet";

    private final Cursor cursor;
    private boolean hasNext;
    private boolean autoClose = true;

    DatabaseLocationSet(Cursor cursor) {
        this.cursor = cursor;
        this.hasNext = cursor != null && cursor.moveToFirst();
    }

    public void setAutoClose(boolean autoClose) {
        this.autoClose = autoClose;
    }

    @Override
    public int getCount() {
        return cursor == null ? 0 : cursor.getCount();
    }

    @Override
    public LocatrackLocation[] toArray() {
        LocatrackLocation[] locations = new LocatrackLocation[cursor.getCount()];
        int i = 0;
        if(hasNext) {
            while (true) {
                locations[i++] = LocationTable.loadFromCursor(cursor);
                if(!cursor.moveToNext()) {
                    break;
                }
            }
        }

        hasNext = false;
        hasNext();

        return locations;
    }

    @Override
    public Iterator<LocatrackLocation> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        boolean localHasNext = hasNext;
        if (!hasNext && cursor != null) {
            if(autoClose) {
                cursor.close();
                if (Logger.DEBUG) Logger.debug(TAG, "Cursor closed.");
            } else {
                hasNext = cursor.moveToFirst();
            }
        }
        return localHasNext;
    }

    @Override
    public LocatrackLocation next() {
        LocatrackLocation loc = null;
        if (hasNext) {
            loc = LocationTable.loadFromCursor(cursor);
            hasNext = cursor.moveToNext();
        }
        return loc;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }
}
