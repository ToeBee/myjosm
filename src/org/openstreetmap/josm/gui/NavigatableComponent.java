// License: GPL. Copyright 2007 by Immanuel Scholz and others
package org.openstreetmap.josm.gui;

import java.awt.Point;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import javax.swing.JComponent;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.HelpAction.Helpful;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.projection.Projection;

/**
 * An component that can be navigated by a mapmover. Used as map view and for the
 * zoomer in the download dialog.
 *
 * @author imi
 */
public class NavigatableComponent extends JComponent implements Helpful {


	public static final EastNorth world = Main.proj.latlon2eastNorth(new LatLon(Projection.MAX_LAT, Projection.MAX_LON));

	/**
	 * The scale factor in x or y-units per pixel. This means, if scale = 10,
	 * every physical pixel on screen are 10 x or 10 y units in the
	 * northing/easting space of the projection.
	 */
	protected double scale;
	/**
	 * Center n/e coordinate of the desired screen center.
	 */
	protected EastNorth center;

	public NavigatableComponent() {
		setLayout(null);
    }

	/**
	 * Return the OSM-conform zoom factor (0 for whole world, 1 for half, 2 for quarter...)
	 */
	public int zoom() {
		double sizex = scale * getWidth();
		double sizey = scale * getHeight();
		for (int zoom = 0; zoom <= 32; zoom++, sizex *= 2, sizey *= 2)
			if (sizex > world.east() || sizey > world.north())
				return zoom;
		return 32;
	}

	/**
	 * Return the current scale value.
	 * @return The scale value currently used in display
	 */
	public double getScale() {
		return scale;
	}

	/**
	 * @return Returns the center point. A copy is returned, so users cannot
	 * 		change the center by accessing the return value. Use zoomTo instead.
	 */
	public EastNorth getCenter() {
		return center;
	}

	/**
	 * @param x X-Pixelposition to get coordinate from
	 * @param y Y-Pixelposition to get coordinate from
	 *
	 * @return Geographic coordinates from a specific pixel coordination
	 * 		on the screen.
	 */
	public EastNorth getEastNorth(int x, int y) {
		return new EastNorth(
				center.east() + (x - getWidth()/2.0)*scale,
				center.north() - (y - getHeight()/2.0)*scale);
	}

	/**
	 * @param x X-Pixelposition to get coordinate from
	 * @param y Y-Pixelposition to get coordinate from
	 *
	 * @return Geographic unprojected coordinates from a specific pixel coordination
	 * 		on the screen.
	 */
	public LatLon getLatLon(int x, int y) {
		EastNorth eastNorth = new EastNorth(
				center.east() + (x - getWidth()/2.0)*scale,
				center.north() - (y - getHeight()/2.0)*scale);
		return getProjection().eastNorth2latlon(eastNorth);
	}

	/**
	 * Return the point on the screen where this Coordinate would be.
	 * @param point The point, where this geopoint would be drawn.
	 * @return The point on screen where "point" would be drawn, relative
	 * 		to the own top/left.
	 */
	public Point getPoint(EastNorth p) {
		double x = (p.east()-center.east())/scale + getWidth()/2;
		double y = (center.north()-p.north())/scale + getHeight()/2;
		return new Point((int)x,(int)y);
	}

	/**
	 * Zoom to the given coordinate.
	 * @param centerX The center x-value (easting) to zoom to.
	 * @param centerY The center y-value (northing) to zoom to.
	 * @param scale The scale to use.
	 */
	public void zoomTo(EastNorth newCenter, double scale) {
		center = newCenter;
		getProjection().eastNorth2latlon(center);
		this.scale = scale;
		repaint();
	}

	/**
	 * Return the nearest point to the screen point given.
	 * If a node within 10 pixel is found, the nearest node is returned.
	 */
	public final Node getNearestNode(Point p) {
		double minDistanceSq = Double.MAX_VALUE;
		Node minPrimitive = null;
		for (Node n : Main.ds.nodes) {
			if (n.deleted || n.incomplete)
				continue;
			Point sp = getPoint(n.eastNorth);
			double dist = p.distanceSq(sp);
			if (minDistanceSq > dist && dist < 100) {
				minDistanceSq = p.distanceSq(sp);
				minPrimitive = n;
			}
		}
		return minPrimitive;
	}

	/**
	 * @return all way segments within 10px of p, sorted by their
	 * perpendicular distance.
	 * 
	 * @param p the point for which to search the nearest segment.
	 */
	public final List<WaySegment> getNearestWaySegments(Point p) {
		TreeMap<Double, List<WaySegment>> nearest = new TreeMap<Double, List<WaySegment>>();
		for (Way w : Main.ds.ways) {
			if (w.deleted || w.incomplete) continue;
			Node lastN = null;
			int i = -2;
			for (Node n : w.nodes) {
				i++;
				if (n.deleted || n.incomplete) continue;
				if (lastN == null) {
					lastN = n;
					continue;
				}

				Point A = getPoint(lastN.eastNorth);
				Point B = getPoint(n.eastNorth);
				double c = A.distanceSq(B);
				double a = p.distanceSq(B);
				double b = p.distanceSq(A);
				double perDist = a-(a-b+c)*(a-b+c)/4/c; // perpendicular distance squared
				if (perDist < 100 && a < c+100 && b < c+100) {
					List<WaySegment> l;
					if (nearest.containsKey(perDist)) {
						l = nearest.get(perDist);
					} else {
						l = new LinkedList<WaySegment>();
						nearest.put(perDist, l);
					}
					l.add(new WaySegment(w, i));
				}

				lastN = n;
			}
		}
		ArrayList<WaySegment> nearestList = new ArrayList<WaySegment>();
		for (List<WaySegment> wss : nearest.values()) {
			nearestList.addAll(wss);
		}
		return nearestList;
	}

	/**
	 * @return the nearest way segment to the screen point given that is not 
	 * in ignore.
	 * 
	 * @param p the point for which to search the nearest segment.
	 * @param ignore a collection of segments which are not to be returned.
	 * May be null.
	 */
	public final WaySegment getNearestWaySegment(Point p, Collection<WaySegment> ignore) {
		List<WaySegment> nearest = getNearestWaySegments(p);
		if (ignore != null) nearest.removeAll(ignore);
		return nearest.isEmpty() ? null : nearest.get(0);
	}

	/**
	 * @return the nearest way segment to the screen point given.
	 */
	public final WaySegment getNearestWaySegment(Point p) {
		return getNearestWaySegment(p, null);
	}
	
	/**
	 * @return the nearest way to the screen point given.
	 */
	public final Way getNearestWay(Point p) {
		WaySegment nearestWaySeg = getNearestWaySegment(p);
		return nearestWaySeg == null ? null : nearestWaySeg.way;
    }

	/**
	 * Return the object, that is nearest to the given screen point.
	 *
	 * First, a node will be searched. If a node within 10 pixel is found, the
	 * nearest node is returned.
	 *
	 * If no node is found, search for near ways.
	 *
	 * If nothing is found, return <code>null</code>.
	 *
	 * @param p				 The point on screen.
	 * @return	The primitive, that is nearest to the point p.
	 */
	public OsmPrimitive getNearest(Point p) {
		OsmPrimitive osm = getNearestNode(p);
		if (osm == null)
			osm = getNearestWay(p);
		return osm;
	}

	/**
	 * Returns a singleton of the nearest object, or else an empty collection.
	 */
	public Collection<OsmPrimitive> getNearestCollection(Point p) {
		OsmPrimitive osm = getNearest(p);
		if (osm == null) 
			return Collections.emptySet();
		return Collections.singleton(osm);
	}

	@Deprecated
	public OsmPrimitive getNearest(Point p, boolean segmentInsteadWay) {
		return getNearest(p);
	}

	/**
	 * @return A list of all objects that are nearest to
	 * the mouse.  Does a simple sequential scan on all the data.
	 *
	 * @return A collection of all items or <code>null</code>
	 * 		if no item under or near the point. The returned
	 * 		list is never empty.
	 */
	public Collection<OsmPrimitive> getAllNearest(Point p) {
		Collection<OsmPrimitive> nearest = new HashSet<OsmPrimitive>();
			for (Way w : Main.ds.ways) {
			if (w.deleted || w.incomplete) continue;
			Node lastN = null;
			for (Node n : w.nodes) {
				if (n.deleted || n.incomplete) continue;
				if (lastN == null) {
					lastN = n;
					continue;
				}
				Point A = getPoint(lastN.eastNorth);
				Point B = getPoint(n.eastNorth);
				double c = A.distanceSq(B);
				double a = p.distanceSq(B);
				double b = p.distanceSq(A);
				double perDist = a-(a-b+c)*(a-b+c)/4/c; // perpendicular distance squared
				if (perDist < 100 && a < c+100 && b < c+100) {
					nearest.add(w);
						break;
					}
				lastN = n;
				}
			}
		for (Node n : Main.ds.nodes) {
			if (!n.deleted && !n.incomplete
					&& getPoint(n.eastNorth).distanceSq(p) < 100) {
				nearest.add(n);
			}
		}
		return nearest.isEmpty() ? null : nearest;
	}

	/**
	 * @return A list of all nodes that are nearest to
	 * the mouse.  Does a simple sequential scan on all the data.
	 *
	 * @return A collection of all nodes or <code>null</code>
	 * 		if no node under or near the point. The returned
	 * 		list is never empty.
	 */
	public Collection<Node> getNearestNodes(Point p) {
		Collection<Node> nearest = new HashSet<Node>();
		for (Node n : Main.ds.nodes) {
			if (!n.deleted && !n.incomplete
					&& getPoint(n.eastNorth).distanceSq(p) < 100) {
				nearest.add(n);
			}
		}
		return nearest.isEmpty() ? null : nearest;
	}

	/**
	 * @return the nearest nodes to the screen point given that is not 
	 * in ignore.
	 * 
	 * @param p the point for which to search the nearest segment.
	 * @param ignore a collection of nodes which are not to be returned.
	 * May be null.
	 */
	public final Collection<Node> getNearestNodes(Point p, Collection<Node> ignore) {
		Collection<Node> nearest = getNearestNodes(p);
                if (nearest == null) return null;
		if (ignore != null) nearest.removeAll(ignore);
		return nearest.isEmpty() ? null : nearest;
	}

	/**
	 * @return The projection to be used in calculating stuff.
	 */
	protected Projection getProjection() {
		return Main.proj;
	}

	public String helpTopic() {
	    String n = getClass().getName();
	    return n.substring(n.lastIndexOf('.')+1);
    }
}
