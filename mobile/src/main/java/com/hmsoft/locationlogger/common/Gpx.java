package com.hmsoft.locationlogger.common;

import android.text.TextUtils;

import com.hmsoft.locationlogger.data.LocationSet;
import com.hmsoft.locationlogger.data.LocatrackLocation;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public final class Gpx {
    private static final String TAG = "Gpx";


    public static final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    public static StringBuilder createGpxStringBuilder() {
        return  new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>\n<gpx xmlns=\"http://www.topografix.com/GPX/1/1\" creator=\"HM Software\" version=\"1.1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"  xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n");
    }

    public static StringBuilder addWayPoint(StringBuilder sb, double lat, double lon, String name, String description) {
        return sb.append("<wpt lat=\"").append(lat).append("\" lon=\"").append(lon).append("\">\n")
                 .append("    <name>").append(name).append("</name>\n")
                 .append("    <desc>").append(description).append("</desc>\n")
                 .append("</wpt>\n");
    }

    public static StringBuilder closeGpx(StringBuilder gpxStringBuilder) {
        return gpxStringBuilder.append("</gpx>");
    }

    public static String createGpx(LocationSet points, String name, String description) {

        StringBuilder sb = createGpxStringBuilder();

        sb.append("<trk>");
        sb.append("<name>");sb.append(name);sb.append("</name>");
        sb.append("<desc>");sb.append(description);sb.append("</desc>");
        sb.append("<trkseg>");


        for (LocatrackLocation l : points) {
            if(l.getAccuracy() < 10) {
                sb.append("<trkpt lat=\"");sb.append(l.getLatitude());sb.append("\" lon=\"");sb.append(l.getLongitude());sb.append("\">");
                sb.append("<time>");sb.append(df.format(new Date(l.getTime())));sb.append("</time>");
                sb.append("<ele>");sb.append(l.getAltitude());sb.append("</ele>");
                sb.append("<speed>");sb.append(l.getSpeed());sb.append("</speed>");
                if(!TextUtils.isEmpty(l.event)) {
                    sb.append("<name>");sb.append(l.event);sb.append("</name>");
                }
                sb.append("</trkpt>");
            }
        }

        sb.append("</trkseg></trk>");
        closeGpx(sb);

        return sb.toString();
        /*try {
            FileWriter writer = new FileWriter(file, false);
            writer.append(header);
            writer.append(name);
            writer.append(segments);
            writer.append(footer);
            writer.flush();
            writer.close();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            Logger.error(TAG, "Error Writting Path",e);
        }*/
    }
}