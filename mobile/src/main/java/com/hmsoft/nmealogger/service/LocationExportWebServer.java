package com.hmsoft.nmealogger.service;

import android.location.Location;

import com.hmsoft.nmealogger.data.LocationSet;
import com.hmsoft.nmealogger.data.locatrack.LocatrackDb;

import java.io.IOException;
import java.io.InputStream;

import fi.iki.elonen.NanoHTTPD;

public class LocationExportWebServer extends NanoHTTPD {
        
    public interface Callbacks {
        void onStart(long startFrom);
        void onLocationServed(Location location);
        void onFinish();
    }
    
    private Callbacks mCallbacks;
    
    public LocationExportWebServer(int port) {
        super(port);
    }
    
    public void setCallbacks(Callbacks callbacks) {
        mCallbacks = callbacks;        
    }

    @Override
    public Response serve(IHTTPSession session) {
        if(session.getMethod() == Method.GET && session.getUri().equals("/getLocations")) {
            long fromdatestamp = 0;
            try {
                fromdatestamp = Long.parseLong(session.getParms().get("fromdate"));
            } catch (NumberFormatException e) {
                // Ignore    
            }
               
            if(mCallbacks != null) {
                mCallbacks.onStart(fromdatestamp);
            }
            
            LocationSet ls = LocatrackDb.getAllFromDate(fromdatestamp);
            InputStream is = new LocationSetInputStream(ls, mCallbacks);
            return new Response(Response.Status.OK, MIME_PLAINTEXT, is);
        }
        return new Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found.");
    }

    private static final class LocationSetInputStream extends InputStream {

        private static final String SEPARATOR = ";";
        
        private Callbacks mCallbacks;
        private LocationSet mLocations;
        private StringBuilder mStringBuilder;
        
        public LocationSetInputStream(LocationSet locations, Callbacks callbacks) {
            mLocations = locations;           
            mStringBuilder = new StringBuilder();
            mCallbacks = callbacks;
        }
        
        @Override
        public int read() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int available() throws IOException {
            return mLocations.getCount() * 256;
        }

        @Override
        public int read(byte[] buffer, int byteOffset, int byteCount) throws IOException {
            int readed = -1;
            if(mLocations.iterator().hasNext()) {
                Location loc = mLocations.iterator().next();
                mStringBuilder.setLength(0);
                byte[] result = mStringBuilder
                        .append(loc.getTime())
                        .append(SEPARATOR)
                        .append(loc.getLatitude())
                        .append(SEPARATOR)
                        .append(loc.getLongitude())
                        .append(SEPARATOR)
                        .append(loc.getAltitude())
                        .append("\n")
                        .toString()
                        .getBytes("UTF-8");

                readed = result.length;
                if(readed > byteCount) readed = byteCount;
                System.arraycopy(result, 0, buffer, byteOffset, readed);
                
                if(mCallbacks != null) {
                    mCallbacks.onLocationServed(loc);
                }
            }
            return readed;
        }

        @Override
        public void close() throws IOException {
            if(mCallbacks != null) {
                mCallbacks.onFinish();
            }
            super.close();
        }
    }
}
