//License: GPLv2 or later
//Copyright 2007 by Raphael Mack and others

package org.openstreetmap.josm.data.gpx;

import java.util.Date;
import java.awt.Color;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.CachedLatLon;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.tools.DateUtils;

public class WayPoint extends WithAttributes implements Comparable<WayPoint>
{
    public double time;
    public Color customColoring;
    public boolean drawLine;
    public int dir;

    private CachedLatLon coor;

    public final LatLon getCoor() {
        return coor;
    }

    public final EastNorth getEastNorth() {
        return coor.getEastNorth();
    }

    public WayPoint(LatLon ll) {
        coor = new CachedLatLon(ll);
    }

    @Override
    public String toString() {
        return "WayPoint (" + (attr.containsKey("name") ? attr.get("name") + ", " :"") + coor.toString() + ", " + attr + ")";
    }

    /**
     * Convert the time stamp of the waypoint into seconds from the epoch
     */
    public void setTime() {
        for(String key : new String[]{"time", "cmt", "desc"})
        {
            if(attr.containsKey("time"))
            {
                double t = DateUtils.fromString(attr.get("time").toString()).getTime();
                if(t != 0.0)
                {
                    time = t / 1000.0; /* ms => seconds */
                    break;
                }
            }
        }
    }

    public int compareTo(WayPoint w)
    {
        return Double.compare(time, w.time);
    }
}
