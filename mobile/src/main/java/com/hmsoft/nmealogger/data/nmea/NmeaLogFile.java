package com.hmsoft.nmealogger.data.nmea;

import android.location.Location;
import android.text.TextUtils;

import com.hmsoft.nmealogger.common.Logger;
import com.hmsoft.nmealogger.data.LocationSet;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;

public class NmeaLogFile implements LocationSet, Iterator<Location> {

    private static final String TAG = "NmeaLogFile";

    private int mFileIndex;
    private ArrayList<File> mFiles;
    private BufferedReader mReader = null;

    private int mCount = 0;
    private boolean mHasNext = true;
    private SimpleDateFormat mParseDateTime = new SimpleDateFormat("ddMMyyHHmmss'.000' z");
    private TextUtils.SimpleStringSplitter mSplitter = new TextUtils.SimpleStringSplitter(',');

    public int mTotalEntries;

    private long mDateStart;
    private long mDateEnd;

    public NmeaLogFile(File file) {

        if (file.isDirectory()) {
            mFiles = NmeaCommon.getLogFilePathList(file);
        } else {
            mFiles = new ArrayList<File>(1);
            mFiles.add(file);
        }

        mFileIndex = mFiles.size() - 1;

        updateSizeValues();

    }

    public void deleteFiles() {
        if(mFiles != null) {
           for (File file : mFiles) {
                file.delete();
            }
        }
    }
    
    public Location getLastLocation() {
        if(mFiles == null || mFiles.size() <= 0) {
            return null;
        }
        File file = mFiles.get(0);
        Location last = null;
        try {
            FileInputStream is = new FileInputStream(file);
            long lastByte = file.length();
            int bytesToRead = NmeaCommon.NMEA_ENTRY_SIZE * 2 + (NmeaCommon.NMEA_ENTRY_SIZE);
            long startPos = lastByte - bytesToRead;
            if(startPos > 0) {
                //noinspection ResultOfMethodCallIgnored
                is.skip(startPos);
            }
            byte[] buffer = new byte[bytesToRead];
            int bytesReaded = is.read(buffer, 0, bytesToRead);

            BufferedReader reader = new BufferedReader(new StringReader(new String(buffer, 0, bytesReaded)));
            while(true) {
                String ggaSentence = reader.readLine();
                if(ggaSentence == null) {
                    break;
                }
                if(!ggaSentence.startsWith(NmeaCommon.GPGGA_SENTENCE)) {
                    continue;
                }

                String rmcSentence = reader.readLine();
                if(rmcSentence == null) {
                    break;
                }

                Location loc = createLocation(ggaSentence, rmcSentence);
                if(loc != null) {
                    last = loc;
                }
            }
            reader.close();
            return last;
        } catch (IOException e) {
            Logger.warning(TAG, "getLastLocation failed", e);
            return last;
        }
    }

    private void updateSizeValues() {
        long totalLength = 0;
        for(int c = mFileIndex; c >= 0; c--) {
            totalLength += mFiles.get(c).length();
        }
        this.mTotalEntries = Math.round(totalLength / (NmeaCommon.NMEA_ENTRY_SIZE * 2));
        this.mHasNext = totalLength > (NmeaCommon.NMEA_ENTRY_SIZE + (NmeaCommon.NMEA_ENTRY_SIZE / 2));
    }

    @Override
    public boolean hasNext() {
        return mHasNext;
    }

    @Override
    public Location next() {
        Location location = null;
        try {
            if (mReader == null && mFileIndex > -1) {
                mCount = 0;
                mReader = new BufferedReader(new FileReader(mFiles.get(mFileIndex--)));
            }

            if (mReader != null) {
                if (mCount < NmeaCommon.GPS_LOGFILE_MAX_ENTRIES) {
                    while(true) {

                        String ggaSentence = mReader.readLine();
                        if(ggaSentence == null || !ggaSentence.startsWith(NmeaCommon.GPGGA_SENTENCE)) {
                            break;
                        }

                        String rmcSentence = mReader.readLine();
                        if(rmcSentence == null) {
                            break;
                        }

                        location = createLocation(ggaSentence, rmcSentence);
                        if(location != null) {
                            break;
                        }
                    }
                    mCount++;
                } else {
                    Logger.warning(TAG, "File contains more than %d entries.", NmeaCommon.GPS_LOGFILE_MAX_ENTRIES);
                }
            }

            mHasNext = location != null || mFileIndex > -1;
            if (location == null) {
                if (mReader != null) {
                    mReader.close();
                    mReader = null;
                }
                if(mHasNext) {
                    return next();
                }
            }
        } catch (IOException e) {
            Logger.warning(TAG, "Error on next", e);
        }
        return location;
    }

    private Location createLocation(String ggaSentence, String rmcSentence) {

        mSplitter.setString(rmcSentence);
        Iterator<String> iterator = mSplitter.iterator();

        String sentence = iterator.next();
        if(!NmeaCommon.GPRMC_SENTENCE.equals(sentence)) {
            Logger.warning(TAG, "No valid RMC sentence found");
            return null;
        }

        String time = iterator.next();
        /*String notUsed = */ iterator.next();
        String latDegrees = iterator.next();
        String latDirection = iterator.next();
        String longDegrees = iterator.next();
        String longDirection = iterator.next();
        String speed = iterator.next();
        String bearing = iterator.next();
        String date = iterator.next();

        Date dateTime;
        try {
            dateTime = mParseDateTime.parse(date + time + " GMT");
        } catch (ParseException e) {
           Logger.warning(TAG, "Parse time error", e);
            return null;
        }

        long timeMillis = dateTime.getTime();

        if((mDateStart > 0 && mDateEnd > 0) && !(timeMillis >= mDateStart && timeMillis <= mDateEnd)) {
            if(Logger.DEBUG) {
                if(Logger.DEBUG) Logger.debug(TAG, "Location time is not in range. %s %s %s", new Date(mDateStart), new Date(mDateEnd), dateTime);
            }
            if(timeMillis > mDateEnd) {
                mFileIndex = -1;
            }
            return null;
        } else {
            if(Logger.DEBUG) {
                if(Logger.DEBUG) Logger.debug(TAG, "Location is IN range %s", dateTime);
            }
        }

        Location location = new Location(NmeaCommon.NMEA_PROVIDER);

        location.setTime(timeMillis);
        location.setLatitude(NmeaCommon.parseNmeaFormat(latDegrees, latDirection));
        location.setLongitude(NmeaCommon.parseNmeaFormat(longDegrees, longDirection));

        if (!TextUtils.isEmpty(speed)) {
            location.setSpeed(Float.parseFloat(speed));
        }

        if (!TextUtils.isEmpty(bearing)) {
            location.setBearing(Float.parseFloat(bearing));
        }

        int count = 0;
        int startIndex = -1;
        while ((startIndex = ggaSentence.indexOf(',', startIndex + 1)) > -1) {
            if (++count == 9) {
                break;
            }
        }

        if (startIndex > -1) {
            int endIndex = ggaSentence.indexOf(',', startIndex + 1);
            if (endIndex > startIndex) {
                String altitude = ggaSentence.substring(startIndex + 1, endIndex);
                if (!TextUtils.isEmpty(altitude)) {
                    location.setAltitude(Float.parseFloat(altitude));
                }
            }
        }

        return location;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterator<Location> iterator() {
        return this;
    }

    @Override
    public int getCount() {
        return mTotalEntries;
    }

    @Override
    public long getDateStart() {
        return mDateStart;
    }

    @Override
    public void setDateStart(long mDateStart) {
        if(this.mDateStart != mDateStart) {
            this.mDateStart = mDateStart;

            if(mDateStart > 0) {
                mFileIndex = mFiles.size() - 1;
                while (mFileIndex >= 0) {
                    File file = mFiles.get(mFileIndex);
                    if (file.lastModified() > mDateStart) {
                        if(Logger.DEBUG) Logger.debug(TAG, "Starting at file, %s", file);
                        updateSizeValues();
                        break;
                    }
                    mFileIndex--;
                }
            }
        }
    }

    @Override
    public long getDateEnd() {
        return mDateEnd;
    }

    @Override
    public void setDateEnd(long dateEnd) {
        this.mDateEnd = dateEnd;
    }

    @Override
    public Location[] toArray() {
        return new Location[0];
    }
}
