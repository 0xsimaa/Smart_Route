package com.cybersec.smartroute.util;

import com.cybersec.smartroute.model.LatLng;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class GpxExporter {

    private GpxExporter() {
    }

    public static String build(String trackName, List<LatLng> points,
                               long startEpochMs, int spacingSeconds) {
        SimpleDateFormat fmt = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        b.append("<gpx version=\"1.1\" creator=\"Smart Route\" ")
                .append("xmlns=\"http://www.topografix.com/GPX/1/1\">\n");
        b.append("  <metadata>\n");
        b.append("    <name>").append(escape(trackName)).append("</name>\n");
        b.append("    <time>").append(fmt.format(new Date(startEpochMs))).append("</time>\n");
        b.append("  </metadata>\n");
        b.append("  <trk>\n");
        b.append("    <name>").append(escape(trackName)).append("</name>\n");
        b.append("    <trkseg>\n");
        long t = startEpochMs;
        for (LatLng p : points) {
            b.append("      <trkpt lat=\"").append(p.latitude)
                    .append("\" lon=\"").append(p.longitude).append("\">")
                    .append("<time>").append(fmt.format(new Date(t))).append("</time>")
                    .append("</trkpt>\n");
            t += spacingSeconds * 1000L;
        }
        b.append("    </trkseg>\n");
        b.append("  </trk>\n");
        b.append("</gpx>\n");
        return b.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
