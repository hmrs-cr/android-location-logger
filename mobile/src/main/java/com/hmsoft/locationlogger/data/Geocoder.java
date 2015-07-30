package com.hmsoft.locationlogger.data;

import android.content.Context;
import android.location.Address;
import android.location.Location;
import android.text.TextUtils;

import com.hmsoft.locationlogger.common.Logger;
import com.hmsoft.locationlogger.data.sqlite.GeocoderTable;
import com.hmsoft.locationlogger.data.sqlite.Helper;

import java.io.IOException;
import java.util.List;

public class Geocoder {

    private static final String TAG = "NmeaGeocoder";

    static final double ROUND = 1000.0;

    static class GeoCoderMemCache {
        private static final int MAX_ENTRIES = 6;
        private static final double[] LAT_ENTRIES = new double[MAX_ENTRIES];
        private static final double[] LON_ENTRIES = new double[MAX_ENTRIES];
        private static final String[] ADD_ENTRIES = new String[MAX_ENTRIES];

        private static int sIndex = -1;

        public static synchronized void addAddress(double lat, double lon, String address) {
            if (++sIndex == MAX_ENTRIES) {
                sIndex = 0;
            }
            LAT_ENTRIES[sIndex] = lat;
            LON_ENTRIES[sIndex] = lon;
            ADD_ENTRIES[sIndex] = address;
        }

        public static synchronized String getAddress(double lat, double lon) {
            for (int c = 0; c < MAX_ENTRIES; c++) {
                double la = LAT_ENTRIES[c];
                double lo = LON_ENTRIES[c];

                if (la == 0 || lo == 0) {
                    return null;
                }

                if (la == lat && lo == lon) {
                    return ADD_ENTRIES[c];
                }
            }
            return null;
        }
    }

    public static String getFromCache(Location location) {
        double lat = Math.round(location.getLatitude() * ROUND) / ROUND;
        double lon = Math.round(location.getLongitude() * ROUND) / ROUND;

        String address = GeoCoderMemCache.getAddress(lat, lon);
        
        if(TextUtils.isEmpty(address)) {
           address = GeocoderTable.getAddress(Helper.getInstance(), lat, lon);
            if(!TextUtils.isEmpty(address)) {
                // add to mem cache
                GeoCoderMemCache.addAddress(lat, lon, address);
                if(Logger.DEBUG) {
                    if(Logger.DEBUG) Logger.debug(TAG, "Address '%s' retrieved from SQL cache.", address);
                }
            }
		} else {
			if(Logger.DEBUG) {
                if(Logger.DEBUG) Logger.debug(TAG, "Address '%s' retrieved from MEM cache.", address);
			}
        }        
        return address;
    }

    public static void addToCache(Location location, String address) {
        double lat = Math.round(location.getLatitude() * ROUND) / ROUND;
        double lon = Math.round(location.getLongitude() * ROUND) / ROUND;

        GeoCoderMemCache.addAddress(lat, lon, address);
		GeocoderTable.saveAddress(Helper.getInstance(), location.getTime(), lat, lon, address);
        if(Logger.DEBUG) Logger.debug(TAG, "Address '%s' added to MEM/SQL cache.", address);
    }

    public static String getFromRemote(Context context, Location location) {
        android.location.Geocoder geocoder = new android.location.Geocoder(context);

        // Create a list to contain the result address
        List<Address> addresses;
        try {
                /*
                 * Return 1 address.
                 */
            addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
        } catch (IOException e1) {
            Logger.warning(TAG, "Exception in getFromLocation", e1);
            return "";
        }
        // If the reverse geocode returned an address
        if (addresses != null && addresses.size() > 0) {
            // Get the first address
            Address address = addresses.get(0);
            Logger.info(TAG, "Got address from geocoder: %s", address);

                /*
                 * Format the first line of address (if available),
                 * city, and country name.
                 */
            // Return the text
            String addressLine = address.getMaxAddressLineIndex() > 0 ?
                    address.getAddressLine(0) : "";
            String locality = address.getLocality();
            String country = address.getCountryName();

            if(TextUtils.isEmpty(locality)) {
                locality = address.getAdminArea();
            }

            String addressText;

            if (TextUtils.isEmpty(locality) || TextUtils.isEmpty(addressLine) ||
                    addressLine.equals(locality)) {
                addressText = String.format("%s, %s", addressLine, country);
            } else {
                addressText = String.format("%s, %s, %s",
                        // If there's a street address, add it
                        addressLine,
                        // Locality is usually a city
                        locality,
                        // The country of the address
                        country);
            }

            return addressText;
        }
        return "";
    }

}
