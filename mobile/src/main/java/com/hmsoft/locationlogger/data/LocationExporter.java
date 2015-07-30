/*
 * Copyright (C) 2013 Jorrit "Chainfire" Jongma
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modified by HMRS.
 */

package com.hmsoft.locationlogger.data;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.location.Location;
import android.os.AsyncTask;
import android.text.Html;

import com.hmsoft.locationlogger.R;
import com.hmsoft.locationlogger.common.Constants;
import com.hmsoft.locationlogger.common.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class LocationExporter {

	private static final String TAG = "LocationExporter";

    private final Context mContext;
    private LocationStorer mStorer;
    private int mSuccessTextId = R.string.export_complete;

    private interface OnExportProgressListener {
		public void OnExportProgress(int cur, int total);
	}

	public LocationExporter(Context context) {
		mContext = context;
	}

	public boolean export(LocationSet locationSet) {
        if (mContext instanceof Activity) {
            (new ExportAsync((Activity) mContext)).execute(locationSet);
            return true;
        } else {
            return performExport(null, locationSet);
        }
	}

    public void setSuccessTextId(int mSuccessTextId) {
        this.mSuccessTextId = mSuccessTextId;
    }

    private boolean performExport(OnExportProgressListener callback, LocationSet locations) {
        try {
            mStorer.open();
            int count = locations.getCount();
            int index = 0;
            for (Location loc : locations) {
                if (loc != null) {
                    mStorer.storeLocation(loc);
                    if (callback != null) callback.OnExportProgress(++index, count);
                }
            }
            return true;
        } catch (IOException e) {
            mStorer.setException(e);
            Logger.warning(TAG, "performExport", e);
            return false;
        } finally {
            mStorer.close();
        }
    }

    public void setStorer(LocationStorer mStorer) {
        this.mStorer = mStorer;
    }

	private class ExportAsync extends AsyncTask<LocationSet, Integer, Boolean> {
		private ProgressDialog mDialog = null;
        private Activity mActivity;

        public ExportAsync(Activity activity) {
            mActivity = activity;
        }

        @Override
		protected void onPreExecute() {

            //mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

			mDialog = new ProgressDialog(mActivity);
			mDialog.setTitle(R.string.export_exporting);
			mDialog.setIndeterminate(false);
			mDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
			mDialog.setCancelable(false);
			mDialog.setProgress(0);
			mDialog.setMax(1);
			mDialog.show();
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			mDialog.setMax(values[1]);
			mDialog.setProgress(values[0]);
		}

		@Override
		protected Boolean doInBackground(LocationSet... params) {

            OnExportProgressListener progressListener = new OnExportProgressListener() {
                @Override
                public void OnExportProgress(int cur, int total) {
                    publishProgress(cur, total);
                }
            };

            return performExport(progressListener, params[0]);
        }
		
		@Override
		protected void onPostExecute(Boolean result) {
            mDialog.dismiss();
            int strId = result ? mSuccessTextId : R.string.export_failed;
            if(strId != 0) {
                (new AlertDialog.Builder(mActivity)).
                        setTitle(R.string.export_export).
                        setMessage(Html.fromHtml(mActivity.getString(strId))).
                        setPositiveButton(R.string.generic_ok, null).
                        show();
            }
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		}
	}	

    public static class FileStorer extends LocationStorer {
        public static final String EXPORTED_DIR = "exported";

        public static enum Format { GPX, KML }

        private final Context mContext;
        private FormatWriter mWriter = null;
        private FileOutputStream mFos;
        private Format mFormat = Format.KML;
        private long mTrackMergeGap = 0;
        private long mTrackMinPoints = 0;
        private long mTrackMinTime = 0;
        private long mTrackMinDistance = 0;

        private int mInSegment = 0;
        private boolean mIsSegmentStart = false;
        private long mLastTime = -1;
        private long mLastTrackStartBytes = 0;
        private long mLastTrackStartTime = 0;
        private double mTrackLatMin = 0;
        private double mTrackLatMax = 0;
        private double mTrackLongMin = 0;
        private double mTrackLongMax = 0;

        public FileStorer(Context contex) {
            mContext = contex;
        }

        @Override
        public boolean storeLocation(Location loc)  {
            mTotalItems++;

            if (mLastTrackStartTime == 0) {
                mLastTrackStartTime = loc.getTime();
                mTrackLatMin = loc.getLatitude();
                mTrackLatMax = loc.getLatitude();
                mTrackLongMin = loc.getLongitude();
                mTrackLongMax = loc.getLongitude();
            }

            if (mIsSegmentStart) {
                if (loc.getTime() - mLastTime < mTrackMergeGap * 1000) {
                    mIsSegmentStart = false;
                }
            }
            try {
                if (mIsSegmentStart) {
                    if (mInSegment > 0) {
                        if (shouldCancel()) {
                            mFos.getChannel().position(mLastTrackStartBytes);
                            mFos.getChannel().truncate(mLastTrackStartBytes);
                        } else {
                            mWriter.endSegment();
                        }
                        mLastTrackStartBytes = mFos.getChannel().position();
                        mWriter.startSegment();

                        mLastTrackStartTime = loc.getTime();
                        mTrackLatMin = loc.getLatitude();
                        mTrackLatMax = loc.getLatitude();
                        mTrackLongMin = loc.getLongitude();
                        mTrackLongMax = loc.getLongitude();

                        mInSegment = 0;
                    }
                    mIsSegmentStart = false;
                }

                // if !ok we don't know location is correct, so we only do this here
                mTrackLatMin = Math.min(mTrackLatMin, loc.getLatitude());
                mTrackLatMax = Math.max(mTrackLatMax, loc.getLatitude());
                mTrackLongMin = Math.min(mTrackLongMin, loc.getLongitude());
                mTrackLongMax = Math.max(mTrackLongMax, loc.getLongitude());

                mWriter.point(loc);

                mTotalSuccess++;
            } catch (IOException e) {
                mTotalFail++;
                Logger.warning(TAG, "storeLocation failed", e);
                return false;
            }

            mInSegment++;

            mLastTime = loc.getTime(); // take into account even if we don't store point, because we know time is correct
            return true;
        }

        public Format getFormat() {
            return mFormat;
        }

        public void setFormat(Format mFormat) {
            this.mFormat = mFormat;
        }

        @Override
        public void configure() {

        }

        @Override
        public void open() throws  IOException {
            super.open();

            File filename = null;
            String dateText = (new SimpleDateFormat("yyyyMMdd")).format(new Date());

            switch (mFormat) {
                case GPX:
                    filename = new File(mContext.getExternalFilesDir(EXPORTED_DIR),
                            String.format("geolog-%s.gpx", dateText));
                    break;
                case KML:
                    filename = new File(mContext.getExternalFilesDir(EXPORTED_DIR),
                            String.format("geolog-%s.kml", dateText));
                    break;
            }

            //noinspection ResultOfMethodCallIgnored
            filename.delete();

            mFos = new FileOutputStream(filename, false);
            mWriter = null;
            switch (mFormat) {
                case GPX:
                    mWriter = new GPXWriter(mFos);
                    break;
                case KML:
                    mWriter = new KMLWriter(mFos);
                    break;
            }

            mWriter.header("NMEALogger " + Constants.VERSION_STRING);

            mLastTrackStartBytes = mFos.getChannel().position();
            mWriter.startSegment();
        }


        @Override
        public void close() {
            try {
                try {
                    if ((mInSegment == 0) || shouldCancel()) {
                        mFos.getChannel().position(mLastTrackStartBytes);
                        mFos.getChannel().truncate(mLastTrackStartBytes);
                    } else {
                        mWriter.endSegment();
                    }

                    mWriter.footer();
                } finally {
                    mFos.close();
                }
            } catch(IOException e) {
                Logger.warning(TAG, "close error", e);
            }
            super.close();
        }

        private double gps2m(double lat_a, double lng_a, double lat_b, double lng_b) {
            double pk = 180/Math.PI;

            double a1 = lat_a / pk;
            double a2 = lng_a / pk;
            double b1 = lat_b / pk;
            double b2 = lng_b / pk;

            double t1 = Math.cos(a1)*Math.cos(a2)*Math.cos(b1)*Math.cos(b2);
            double t2 = Math.cos(a1)*Math.sin(a2)*Math.cos(b1)*Math.sin(b2);
            double t3 = Math.sin(a1)*Math.sin(b1);
            double tt = Math.acos(t1 + t2 + t3);

            return 6366000*tt;
        }

        private boolean shouldCancel() {
            boolean cancel = false;

            if ((mTrackMinPoints > 0) && (mInSegment < mTrackMinPoints)) {
                cancel = true;
            }

            if ((mTrackMinTime > 0) && (mLastTime - mLastTrackStartTime < mTrackMinTime * 1000)) {
                cancel = true;
            }

            if (mTrackMinDistance > 0) {
                double m = gps2m(mTrackLatMin, mTrackLongMin, mTrackLatMax, mTrackLongMax);
                if (m < mTrackMinDistance) {
                    cancel = true;
                }
            }

            return cancel;
        }

        private abstract class FormatWriter {
            protected OutputStream mOs = null;

            public FormatWriter(OutputStream os) {
                this.mOs = os;
            }

            protected void write(String string) throws IOException {
                mOs.write(string.getBytes("US-ASCII"));
            }

            public abstract void header(String exporter) throws IOException;
            public abstract void startSegment() throws IOException;
            public abstract void point(Location loc) throws IOException;
            public abstract void endSegment() throws IOException;
            public abstract void footer() throws IOException;
        }

        private class GPXWriter extends FormatWriter {
            protected SimpleDateFormat mSimpleDateFormat = null;

            public GPXWriter(OutputStream os) {
                super(os);
                mSimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH);
                mSimpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            }

            @Override
            public void header(String exporter) throws IOException {
                write(
                        "<?xml version=\"1.0\"?>\r\n" +
                                "<gpx version=\"1.0\" creator=\"" + exporter + "\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"http://www.topografix.com/GPX/1/0\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/0 http://www.topografix.com/GPX/1/0/gpx.xsd\">\r\n" +
                                "  <trk>\r\n"
                );
            }

            @Override
            public void startSegment() throws IOException {
                write(
                        "    <trkseg>\r\n"
                );
            }

            @Override
            public void point(Location loc) throws IOException {
                write(String.format(Locale.ENGLISH, "      <trkpt lat=\"%.5f\" lon=\"%.5f\"><time>%s</time></trkpt>\r\n", loc.getLatitude(), loc.getLongitude(), mSimpleDateFormat.format(new Date(loc.getTime()))));
            }

            @Override
            public void endSegment() throws IOException {
                write(
                        "    </trkseg>\r\n"
                );
            }

            @Override
            public void footer() throws IOException {
                write(
                        "  </trk>\r\n" +
                                "</gpx>\r\n"
                );
            }
        }

        private class KMLWriter extends FormatWriter {
            protected int mTrackIndex = 1;
            protected String mLast = "";

            public KMLWriter(OutputStream os) {
                super(os);
            }

            @Override
            public void header(String exporter)	throws IOException {
                write(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                                "<kml xmlns=\"http://www.opengis.net/kml/2.2\"  xmlns:gx=\"http://www.google.com/kml/ext/2.2\" xmlns:kml=\"http://www.opengis.net/kml/2.2\"\r\n" +
                                "  xmlns:atom=\"http://www.w3.org/2005/Atom\">\r\n" +
                                "  <Document>\r\n" +
                                "    <name>Exported by " + exporter + "</name>\r\n" +
                                "    <description></description>\r\n" +
                                "    <visibility>1</visibility>\r\n" +
                                "    <open>1</open>\r\n" +
                                "    <Style id=\"red\"><LineStyle><color>C81400FF</color><width>4</width></LineStyle></Style>\r\n" +
                                "    <Folder>\r\n" +
                                "      <name>Tracks</name>\r\n" +
                                "      <description></description>\r\n" +
                                "      <visibility>1</visibility>\r\n" +
                                "      <open>1</open>\r\n"
                );
            }

            @Override
            public void startSegment() throws IOException {
                write(
                        "      <Placemark>\r\n" +
                                "        <visibility>1</visibility>\r\n" +
                                "        <open>1</open>\r\n" +
                                "        <styleUrl>#red</styleUrl>\r\n" +
                                "        <name>Track " + String.valueOf(mTrackIndex) + "</name>\r\n" +
                                "        <description></description>\r\n" +
                                "        <LineString>\r\n" +
                                "          <extrude>true</extrude>\r\n" +
                                "          <tessellate>true</tessellate>\r\n" +
                                "          <altitudeMode>clampToGround</altitudeMode>\r\n" +
                                "            <coordinates>\r\n"
                );
                mTrackIndex++;
            }

            @Override
            public void point(Location loc) throws IOException {
                String now = String.format(Locale.ENGLISH, "            %.5f,%.5f\r\n", loc.getLongitude(), loc.getLatitude());
                if (!now.equals(mLast)) {
                    write(now);
                    mLast = now;
                }
            }

            @Override
            public void endSegment() throws IOException {
                write(
                        "          </coordinates>\r\n" +
                                "        </LineString>\r\n" +
                                "      </Placemark>\r\n"
                );
            }

            @Override
            public void footer() throws IOException {
                write(
                        "    </Folder>\r\n" +
                                "  </Document>\r\n" +
                                "</kml>\r\n"
                );
            }
        }
    }

}