package com.hmsoft.nmealogger.data;


import android.location.Location;

import java.io.IOException;

public class MultiLocationStorer extends LocationStorer {
    
    private LocationStorer[] mStorers;
    
    public MultiLocationStorer(LocationStorer... storers) {
        mStorers = storers;
    }

    @Override
    public void open() throws IOException {
        super.open();
        for(LocationStorer storer : mStorers) {
            storer.open();
        }        
    }

    @Override
    public void close() {
        super.close();
        for(LocationStorer storer : mStorers) {
            storer.close();
        }
    }

    @Override
    public boolean storeLocation(Location location) {
        boolean success = true;
        for(LocationStorer storer : mStorers) {
            success = success && storer.storeLocation(location);
        }
        return success;
    }

    @Override
    public void configure() {
        for(LocationStorer storer : mStorers) {
            storer.configure();
        }
    }
}
