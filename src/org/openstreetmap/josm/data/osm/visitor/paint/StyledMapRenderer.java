// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.osm.visitor.paint;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import javax.swing.AbstractButton;
import javax.swing.FocusManager;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.Changeset;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.osm.visitor.Visitor;
import org.openstreetmap.josm.data.osm.visitor.paint.relations.Multipolygon;
import org.openstreetmap.josm.data.osm.visitor.paint.relations.Multipolygon.PolyData;
import org.openstreetmap.josm.data.osm.visitor.paint.relations.MultipolygonCache;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.mappaint.AreaElemStyle;
import org.openstreetmap.josm.gui.mappaint.BoxTextElemStyle;
import org.openstreetmap.josm.gui.mappaint.BoxTextElemStyle.HorizontalTextAlignment;
import org.openstreetmap.josm.gui.mappaint.BoxTextElemStyle.VerticalTextAlignment;
import org.openstreetmap.josm.gui.mappaint.ElemStyle;
import org.openstreetmap.josm.gui.mappaint.ElemStyles;
import org.openstreetmap.josm.gui.mappaint.MapImage;
import org.openstreetmap.josm.gui.mappaint.MapPaintStyles;
import org.openstreetmap.josm.gui.mappaint.NodeElemStyle;
import org.openstreetmap.josm.gui.mappaint.NodeElemStyle.Symbol;
import org.openstreetmap.josm.gui.mappaint.RepeatImageElemStyle.LineImageAlignment;
import org.openstreetmap.josm.gui.mappaint.StyleCache.StyleList;
import org.openstreetmap.josm.gui.mappaint.TextElement;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource;
import org.openstreetmap.josm.gui.mappaint.mapcss.Selector;
import org.openstreetmap.josm.tools.CompositeList;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Utils;

/**
 * A map renderer which renders a map according to style rules in a set of style sheets.
 * @since 486
 */
public class StyledMapRenderer extends AbstractMapRenderer {

    private static final Pair<Integer, ExecutorService> THREAD_POOL =
            Utils.newThreadPool("mappaint.StyledMapRenderer.style_creation.numberOfThreads");

    /**
     * Iterates over a list of Way Nodes and returns screen coordinates that
     * represent a line that is shifted by a certain offset perpendicular
     * to the way direction.
     *
     * There is no intention, to handle consecutive duplicate Nodes in a
     * perfect way, but it is should not throw an exception.
     */
    private class OffsetIterator implements Iterator<Point> {

        private List<Node> nodes;
        private float offset;
        private int idx;

        private Point prev = null;
        /* 'prev0' is a point that has distance 'offset' from 'prev' and the
         * line from 'prev' to 'prev0' is perpendicular to the way segment from
         * 'prev' to the next point.
         */
        private int x_prev0, y_prev0;

        public OffsetIterator(List<Node> nodes, float offset) {
            this.nodes = nodes;
            this.offset = offset;
            idx = 0;
        }

        @Override
        public boolean hasNext() {
            return idx < nodes.size();
        }

        @Override
        public Point next() {
            if (Math.abs(offset) < 0.1f) return nc.getPoint(nodes.get(idx++));

            Point current = nc.getPoint(nodes.get(idx));

            if (idx == nodes.size() - 1) {
                ++idx;
                if (prev != null) {
                    return new Point(x_prev0 + current.x - prev.x, y_prev0 + current.y - prev.y);
                } else {
                    return current;
                }
            }

            Point next = nc.getPoint(nodes.get(idx+1));

            int dx_next = next.x - current.x;
            int dy_next = next.y - current.y;
            double len_next = Math.sqrt(dx_next*dx_next + dy_next*dy_next);

            if (len_next == 0) {
                len_next = 1; // value does not matter, because dy_next and dx_next is 0
            }

            int x_current0 = current.x + (int) Math.round(offset * dy_next / len_next);
            int y_current0 = current.y - (int) Math.round(offset * dx_next / len_next);

            if (idx==0) {
                ++idx;
                prev = current;
                x_prev0 = x_current0;
                y_prev0 = y_current0;
                return new Point(x_current0, y_current0);
            } else {
                int dx_prev = current.x - prev.x;
                int dy_prev = current.y - prev.y;

                // determine intersection of the lines parallel to the two
                // segments
                int det = dx_next*dy_prev - dx_prev*dy_next;

                if (det == 0) {
                    ++idx;
                    prev = current;
                    x_prev0 = x_current0;
                    y_prev0 = y_current0;
                    return new Point(x_current0, y_current0);
                }

                int m = dx_next*(y_current0 - y_prev0) - dy_next*(x_current0 - x_prev0);

                int cx_ = x_prev0 + Math.round((float)m * dx_prev / det);
                int cy_ = y_prev0 + Math.round((float)m * dy_prev / det);
                ++idx;
                prev = current;
                x_prev0 = x_current0;
                y_prev0 = y_current0;
                return new Point(cx_, cy_);
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class StyleRecord implements Comparable<StyleRecord> {
        final ElemStyle style;
        final OsmPrimitive osm;
        final int flags;

        public StyleRecord(ElemStyle style, OsmPrimitive osm, int flags) {
            this.style = style;
            this.osm = osm;
            this.flags = flags;
        }

        @Override
        public int compareTo(StyleRecord other) {
            if ((this.flags & FLAG_DISABLED) != 0 && (other.flags & FLAG_DISABLED) == 0)
                return -1;
            if ((this.flags & FLAG_DISABLED) == 0 && (other.flags & FLAG_DISABLED) != 0)
                return 1;

            int d0 = Float.compare(this.style.major_z_index, other.style.major_z_index);
            if (d0 != 0)
                return d0;

            // selected on top of member of selected on top of unselected
            // FLAG_DISABLED bit is the same at this point
            if (this.flags > other.flags)
                return 1;
            if (this.flags < other.flags)
                return -1;

            int dz = Float.compare(this.style.z_index, other.style.z_index);
            if (dz != 0)
                return dz;

            // simple node on top of icons and shapes
            if (this.style == NodeElemStyle.SIMPLE_NODE_ELEMSTYLE && other.style != NodeElemStyle.SIMPLE_NODE_ELEMSTYLE)
                return 1;
            if (this.style != NodeElemStyle.SIMPLE_NODE_ELEMSTYLE && other.style == NodeElemStyle.SIMPLE_NODE_ELEMSTYLE)
                return -1;

            // newer primitives to the front
            long id = this.osm.getUniqueId() - other.osm.getUniqueId();
            if (id > 0)
                return 1;
            if (id < 0)
                return -1;

            return Float.compare(this.style.object_z_index, other.style.object_z_index);
        }
    }

    private static Boolean IS_GLYPH_VECTOR_DOUBLE_TRANSLATION_BUG = null;

    /**
     * Check, if this System has the GlyphVector double translation bug.
     *
     * With this bug, <code>gv.setGlyphTransform(i, trfm)</code> has a different
     * effect than on most other systems, namely the translation components
     * ("m02" &amp; "m12", {@link AffineTransform}) appear to be twice as large, as
     * they actually are. The rotation is unaffected (scale &amp; shear not tested
     * so far).
     *
     * This bug has only been observed on Mac OS X, see #7841.
     *
     * @return true, if the GlyphVector double translation bug is present on
     * this System
     */
    public static boolean isGlyphVectorDoubleTranslationBug() {
        if (IS_GLYPH_VECTOR_DOUBLE_TRANSLATION_BUG != null)
            return IS_GLYPH_VECTOR_DOUBLE_TRANSLATION_BUG;
        FontRenderContext frc = new FontRenderContext(null, false, false);
        Font font = new Font("Dialog", Font.PLAIN, 12);
        GlyphVector gv = font.createGlyphVector(frc, "x");
        gv.setGlyphTransform(0, AffineTransform.getTranslateInstance(1000, 1000));
        Shape shape = gv.getGlyphOutline(0);
        // x is about 1000 on normal stystems and about 2000 when the bug occurs
        int x = shape.getBounds().x;
        IS_GLYPH_VECTOR_DOUBLE_TRANSLATION_BUG = x > 1500;
        return IS_GLYPH_VECTOR_DOUBLE_TRANSLATION_BUG;
    }

    private double circum;

    private MapPaintSettings paintSettings;

    private Color relationSelectedColor;
    private Color highlightColorTransparent;

    private static final int FLAG_NORMAL = 0;
    private static final int FLAG_DISABLED = 1;
    private static final int FLAG_MEMBER_OF_SELECTED = 2;
    private static final int FLAG_SELECTED = 4;

    private static final double PHI = Math.toRadians(20);
    private static final double cosPHI = Math.cos(PHI);
    private static final double sinPHI = Math.sin(PHI);

    private Collection<WaySegment> highlightWaySegments;

    // highlight customization fields
    private int highlightLineWidth;
    private int highlightPointRadius;
    private int widerHighlight;
    private int highlightStep;

    //flag that activate wider highlight mode
    private boolean useWiderHighlight;

    private boolean useStrokes;
    private boolean showNames;
    private boolean showIcons;
    private boolean  isOutlineOnly;

    private Font orderFont;

    private boolean leftHandTraffic;

    /**
     * Constructs a new {@code StyledMapRenderer}.
     *
     * @param g the graphics context. Must not be null.
     * @param nc the map viewport. Must not be null.
     * @param isInactiveMode if true, the paint visitor shall render OSM objects such that they
     * look inactive. Example: rendering of data in an inactive layer using light gray as color only.
     * @throws IllegalArgumentException thrown if {@code g} is null
     * @throws IllegalArgumentException thrown if {@code nc} is null
     */
    public StyledMapRenderer(Graphics2D g, NavigatableComponent nc, boolean isInactiveMode) {
        super(g, nc, isInactiveMode);

        if (nc!=null) {
            Component focusOwner = FocusManager.getCurrentManager().getFocusOwner();
            useWiderHighlight = !(focusOwner instanceof AbstractButton || focusOwner == nc);
        }
    }

    private Polygon buildPolygon(Point center, int radius, int sides) {
        return buildPolygon(center, radius, sides, 0.0);
    }

    private Polygon buildPolygon(Point center, int radius, int sides, double rotation) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < sides; i++) {
            double angle = ((2 * Math.PI / sides) * i) - rotation;
            int x = (int) Math.round(center.x + radius * Math.cos(angle));
            int y = (int) Math.round(center.y + radius * Math.sin(angle));
            polygon.addPoint(x, y);
        }
        return polygon;
    }

    private void displaySegments(GeneralPath path, GeneralPath orientationArrows, GeneralPath onewayArrows, GeneralPath onewayArrowsCasing,
            Color color, BasicStroke line, BasicStroke dashes, Color dashedColor) {
        g.setColor(isInactiveMode ? inactiveColor : color);
        if (useStrokes) {
            g.setStroke(line);
        }
        g.draw(path);

        if(!isInactiveMode && useStrokes && dashes != null) {
            g.setColor(dashedColor);
            g.setStroke(dashes);
            g.draw(path);
        }

        if (orientationArrows != null) {
            g.setColor(isInactiveMode ? inactiveColor : color);
            g.setStroke(new BasicStroke(line.getLineWidth(), line.getEndCap(), BasicStroke.JOIN_MITER, line.getMiterLimit()));
            g.draw(orientationArrows);
        }

        if (onewayArrows != null) {
            g.setStroke(new BasicStroke(1, line.getEndCap(), BasicStroke.JOIN_MITER, line.getMiterLimit()));
            g.fill(onewayArrowsCasing);
            g.setColor(isInactiveMode ? inactiveColor : backgroundColor);
            g.fill(onewayArrows);
        }

        if (useStrokes) {
            g.setStroke(new BasicStroke());
        }
    }

    /**
     * Displays text at specified position including its halo, if applicable.
     *
     * @param gv Text's glyphs to display. If {@code null}, use text from {@code s} instead.
     * @param s text to display if {@code gv} is {@code null}
     * @param x X position
     * @param y Y position
     * @param disabled {@code true} if element is disabled (filtered out)
     * @param text text style to use
     */
    private void displayText(GlyphVector gv, String s, int x, int y, boolean disabled, TextElement text) {
        if (isInactiveMode || disabled) {
            g.setColor(inactiveColor);
            if (gv != null) {
                g.drawGlyphVector(gv, x, y);
            } else {
                g.setFont(text.font);
                g.drawString(s, x, y);
            }
        } else if (text.haloRadius != null) {
            g.setStroke(new BasicStroke(2*text.haloRadius, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
            g.setColor(text.haloColor);
            if (gv == null) {
                FontRenderContext frc = g.getFontRenderContext();
                gv = text.font.createGlyphVector(frc, s);
            }
            Shape textOutline = gv.getOutline(x, y);
            g.draw(textOutline);
            g.setStroke(new BasicStroke());
            g.setColor(text.color);
            g.fill(textOutline);
        } else {
            g.setColor(text.color);
            if (gv != null) {
                g.drawGlyphVector(gv, x, y);
            } else {
                g.setFont(text.font);
                g.drawString(s, x, y);
            }
        }
    }

    protected void drawArea(OsmPrimitive osm, Path2D.Double path, Color color, MapImage fillImage, TextElement text) {

        Shape area = path.createTransformedShape(nc.getAffineTransform());

        if (!isOutlineOnly) {
            if (fillImage == null) {
                if (isInactiveMode) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.33f));
                }
                g.setColor(color);
                g.fill(area);
            } else {
                TexturePaint texture = new TexturePaint(fillImage.getImage(),
                        new Rectangle(0, 0, fillImage.getWidth(), fillImage.getHeight()));
                g.setPaint(texture);
                Float alpha = Utils.color_int2float(fillImage.alpha);
                if (alpha != 1f) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                }
                g.fill(area);
                g.setPaintMode();
            }
        }

        drawAreaText(osm, text, area);
    }

    private void drawAreaText(OsmPrimitive osm, TextElement text, Shape area) {
        if (text != null && isShowNames()) {
            // abort if we can't compose the label to be rendered
            if (text.labelCompositionStrategy == null) return;
            String name = text.labelCompositionStrategy.compose(osm);
            if (name == null) return;

            Rectangle pb = area.getBounds();
            FontMetrics fontMetrics = g.getFontMetrics(orderFont); // if slow, use cache
            Rectangle2D nb = fontMetrics.getStringBounds(name, g); // if slow, approximate by strlen()*maxcharbounds(font)

            // Using the Centroid is Nicer for buildings like: +--------+
            // but this needs to be fast.  As most houses are  |   42   |
            // boxes anyway, the center of the bounding box    +---++---+
            // will have to do.                                    ++
            // Centroids are not optimal either, just imagine a U-shaped house.

            // quick check to see if label box is smaller than primitive box
            if (pb.width >= nb.getWidth() && pb.height >= nb.getHeight()) {

                final double w = pb.width  - nb.getWidth();
                final double h = pb.height - nb.getHeight();

                final int x2 = pb.x + (int)(w/2.0);
                final int y2 = pb.y + (int)(h/2.0);

                final int nbw = (int) nb.getWidth();
                final int nbh = (int) nb.getHeight();

                Rectangle centeredNBounds = new Rectangle(x2, y2, nbw, nbh);

                // slower check to see if label is displayed inside primitive shape
                boolean labelOK = area.contains(centeredNBounds);
                if (!labelOK) {
                    // if center position (C) is not inside osm shape, try naively some other positions as follows:
                    final int x1 = pb.x + (int)(  w/4.0);
                    final int x3 = pb.x + (int)(3*w/4.0);
                    final int y1 = pb.y + (int)(  h/4.0);
                    final int y3 = pb.y + (int)(3*h/4.0);
                    // +-----------+
                    // |  5  1  6  |
                    // |  4  C  2  |
                    // |  8  3  7  |
                    // +-----------+
                    Rectangle[] candidates = new Rectangle[] {
                            new Rectangle(x2, y1, nbw, nbh),
                            new Rectangle(x3, y2, nbw, nbh),
                            new Rectangle(x2, y3, nbw, nbh),
                            new Rectangle(x1, y2, nbw, nbh),
                            new Rectangle(x1, y1, nbw, nbh),
                            new Rectangle(x3, y1, nbw, nbh),
                            new Rectangle(x3, y3, nbw, nbh),
                            new Rectangle(x1, y3, nbw, nbh)
                    };
                    // Dumb algorithm to find a better placement. We could surely find a smarter one but it should
                    // solve most of building issues with only few calculations (8 at most)
                    for (int i = 0; i < candidates.length && !labelOK; i++) {
                        centeredNBounds = candidates[i];
                        labelOK = area.contains(centeredNBounds);
                    }
                }
                if (labelOK) {
                    Font defaultFont = g.getFont();
                    int x = (int)(centeredNBounds.getMinX() - nb.getMinX());
                    int y = (int)(centeredNBounds.getMinY() - nb.getMinY());
                    displayText(null, name, x, y, osm.isDisabled(), text);
                    g.setFont(defaultFont);
                } else if (Main.isDebugEnabled()) {
                    Main.debug("Couldn't find a correct label placement for "+osm+" / "+name);
                }
            }
        }
    }

    public void drawArea(Relation r, Color color, MapImage fillImage, TextElement text) {
        Multipolygon multipolygon = MultipolygonCache.getInstance().get(nc, r);
        if (!r.isDisabled() && !multipolygon.getOuterWays().isEmpty()) {
            for (PolyData pd : multipolygon.getCombinedPolygons()) {
                Path2D.Double p = pd.get();
                if (!isAreaVisible(p)) {
                    continue;
                }
                drawArea(r, p,
                        pd.selected ? paintSettings.getRelationSelectedColor(color.getAlpha()) : color,
                                fillImage, text);
            }
        }
    }

    public void drawArea(Way w, Color color, MapImage fillImage, TextElement text) {
        drawArea(w, getPath(w), color, fillImage, text);
    }

    public void drawBoxText(Node n, BoxTextElemStyle bs) {
        if (!isShowNames() || bs == null)
            return;

        Point p = nc.getPoint(n);
        TextElement text = bs.text;
        String s = text.labelCompositionStrategy.compose(n);
        if (s == null) return;

        Font defaultFont = g.getFont();
        g.setFont(text.font);

        int x = p.x + text.xOffset;
        int y = p.y + text.yOffset;
        /**
         *
         *       left-above __center-above___ right-above
         *         left-top|                 |right-top
         *                 |                 |
         *      left-center|  center-center  |right-center
         *                 |                 |
         *      left-bottom|_________________|right-bottom
         *       left-below   center-below    right-below
         *
         */
        Rectangle box = bs.getBox();
        if (bs.hAlign == HorizontalTextAlignment.RIGHT) {
            x += box.x + box.width + 2;
        } else {
            FontRenderContext frc = g.getFontRenderContext();
            Rectangle2D bounds = text.font.getStringBounds(s, frc);
            int textWidth = (int) bounds.getWidth();
            if (bs.hAlign == HorizontalTextAlignment.CENTER) {
                x -= textWidth / 2;
            } else if (bs.hAlign == HorizontalTextAlignment.LEFT) {
                x -= - box.x + 4 + textWidth;
            } else throw new AssertionError();
        }

        if (bs.vAlign == VerticalTextAlignment.BOTTOM) {
            y += box.y + box.height;
        } else {
            FontRenderContext frc = g.getFontRenderContext();
            LineMetrics metrics = text.font.getLineMetrics(s, frc);
            if (bs.vAlign == VerticalTextAlignment.ABOVE) {
                y -= - box.y + metrics.getDescent();
            } else if (bs.vAlign == VerticalTextAlignment.TOP) {
                y -= - box.y - metrics.getAscent();
            } else if (bs.vAlign == VerticalTextAlignment.CENTER) {
                y += (metrics.getAscent() - metrics.getDescent()) / 2;
            } else if (bs.vAlign == VerticalTextAlignment.BELOW) {
                y += box.y + box.height + metrics.getAscent() + 2;
            } else throw new AssertionError();
        }
        displayText(null, s, x, y, n.isDisabled(), text);
        g.setFont(defaultFont);
    }

    /**
     * Draw an image along a way repeatedly.
     *
     * @param way the way
     * @param pattern the image
     * @param offset offset from the way
     * @param spacing spacing between two images
     * @param phase initial spacing
     * @param align alignment of the image. The top, center or bottom edge
     * can be aligned with the way.
     */
    public void drawRepeatImage(Way way, Image pattern, float offset, float spacing, float phase, LineImageAlignment align) {
        final int imgWidth = pattern.getWidth(null);
        final double repeat = imgWidth + spacing;
        final int imgHeight = pattern.getHeight(null);

        Point lastP = null;
        double currentWayLength = phase % repeat;
        if (currentWayLength < 0) {
            currentWayLength += repeat;
        }

        int dy1, dy2;
        switch (align) {
            case TOP:
                dy1 = 0;
                dy2 = imgHeight;
                break;
            case CENTER:
                dy1 = - imgHeight / 2;
                dy2 = imgHeight + dy1;
                break;
            case BOTTOM:
                dy1 = -imgHeight;
                dy2 = 0;
                break;
            default:
                throw new AssertionError();
        }

        OffsetIterator it = new OffsetIterator(way.getNodes(), offset);
        while (it.hasNext()) {
            Point thisP = it.next();

            if (lastP != null) {
                final double segmentLength = thisP.distance(lastP);

                final double dx = thisP.x - lastP.x;
                final double dy = thisP.y - lastP.y;

                // pos is the position from the beginning of the current segment
                // where an image should be painted
                double pos = repeat - (currentWayLength % repeat);

                AffineTransform saveTransform = g.getTransform();
                g.translate(lastP.x, lastP.y);
                g.rotate(Math.atan2(dy, dx));

                // draw the rest of the image from the last segment in case it
                // is cut off
                if (pos > spacing) {
                    // segment is too short for a complete image
                    if (pos > segmentLength + spacing) {
                        g.drawImage(pattern, 0, dy1, (int) segmentLength, dy2,
                                (int) (repeat - pos), 0,
                                (int) (repeat - pos + segmentLength), imgHeight, null);
                    // rest of the image fits fully on the current segment
                    } else {
                        g.drawImage(pattern, 0, dy1, (int) (pos - spacing), dy2,
                                (int) (repeat - pos), 0, imgWidth, imgHeight, null);
                    }
                }
                // draw remaining images for this segment
                while (pos < segmentLength) {
                    // cut off at the end?
                    if (pos + imgWidth > segmentLength) {
                        g.drawImage(pattern, (int) pos, dy1, (int) segmentLength, dy2,
                                0, 0, (int) segmentLength - (int) pos, imgHeight, null);
                    } else {
                        g.drawImage(pattern, (int) pos, dy1, nc);
                    }
                    pos += repeat;
                }
                g.setTransform(saveTransform);

                currentWayLength += segmentLength;
            }
            lastP = thisP;
        }
    }

    @Override
    public void drawNode(Node n, Color color, int size, boolean fill) {
        if(size <= 0 && !n.isHighlighted())
            return;

        Point p = nc.getPoint(n);

        if(n.isHighlighted()) {
            drawPointHighlight(p, size);
        }

        if (size > 1) {
            if ((p.x < 0) || (p.y < 0) || (p.x > nc.getWidth()) || (p.y > nc.getHeight())) return;
            int radius = size / 2;

            if (isInactiveMode || n.isDisabled()) {
                g.setColor(inactiveColor);
            } else {
                g.setColor(color);
            }
            if (fill) {
                g.fillRect(p.x-radius-1, p.y-radius-1, size + 1, size + 1);
            } else {
                g.drawRect(p.x-radius-1, p.y-radius-1, size, size);
            }
        }
    }

    public void drawNodeIcon(Node n, Image img, float alpha, boolean selected, boolean member) {
        Point p = nc.getPoint(n);

        final int w = img.getWidth(null), h=img.getHeight(null);
        if(n.isHighlighted()) {
            drawPointHighlight(p, Math.max(w, h));
        }

        if (alpha != 1f) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        }
        g.drawImage(img, p.x-w/2, p.y-h/2, nc);
        g.setPaintMode();
        if (selected || member)
        {
            Color color;
            if (isInactiveMode || n.isDisabled()) {
                color = inactiveColor;
            } else if (selected) {
                color = selectedColor;
            } else {
                color = relationSelectedColor;
            }
            g.setColor(color);
            g.drawRect(p.x-w/2-2, p.y-h/2-2, w+4, h+4);
        }
    }

    public void drawNodeSymbol(Node n, Symbol s, Color fillColor, Color strokeColor) {
        Point p = nc.getPoint(n);
        int radius = s.size / 2;

        if(n.isHighlighted()) {
            drawPointHighlight(p, s.size);
        }

        if (fillColor != null) {
            g.setColor(fillColor);
            switch (s.symbol) {
            case SQUARE:
                g.fillRect(p.x - radius, p.y - radius, s.size, s.size);
                break;
            case CIRCLE:
                g.fillOval(p.x - radius, p.y - radius, s.size, s.size);
                break;
            case TRIANGLE:
                g.fillPolygon(buildPolygon(p, radius, 3, Math.PI / 2));
                break;
            case PENTAGON:
                g.fillPolygon(buildPolygon(p, radius, 5, Math.PI / 2));
                break;
            case HEXAGON:
                g.fillPolygon(buildPolygon(p, radius, 6));
                break;
            case HEPTAGON:
                g.fillPolygon(buildPolygon(p, radius, 7, Math.PI / 2));
                break;
            case OCTAGON:
                g.fillPolygon(buildPolygon(p, radius, 8, Math.PI / 8));
                break;
            case NONAGON:
                g.fillPolygon(buildPolygon(p, radius, 9, Math.PI / 2));
                break;
            case DECAGON:
                g.fillPolygon(buildPolygon(p, radius, 10));
                break;
            default:
                throw new AssertionError();
            }
        }
        if (s.stroke != null) {
            g.setStroke(s.stroke);
            g.setColor(strokeColor);
            switch (s.symbol) {
            case SQUARE:
                g.drawRect(p.x - radius, p.y - radius, s.size - 1, s.size - 1);
                break;
            case CIRCLE:
                g.drawOval(p.x - radius, p.y - radius, s.size - 1, s.size - 1);
                break;
            case TRIANGLE:
                g.drawPolygon(buildPolygon(p, radius, 3, Math.PI / 2));
                break;
            case PENTAGON:
                g.drawPolygon(buildPolygon(p, radius, 5, Math.PI / 2));
                break;
            case HEXAGON:
                g.drawPolygon(buildPolygon(p, radius, 6));
                break;
            case HEPTAGON:
                g.drawPolygon(buildPolygon(p, radius, 7, Math.PI / 2));
                break;
            case OCTAGON:
                g.drawPolygon(buildPolygon(p, radius, 8, Math.PI / 8));
                break;
            case NONAGON:
                g.drawPolygon(buildPolygon(p, radius, 9, Math.PI / 2));
                break;
            case DECAGON:
                g.drawPolygon(buildPolygon(p, radius, 10));
                break;
            default:
                throw new AssertionError();
            }
            g.setStroke(new BasicStroke());
        }
    }

    /**
     * Draw a number of the order of the two consecutive nodes within the
     * parents way
     */
    public void drawOrderNumber(Node n1, Node n2, int orderNumber, Color clr) {
        Point p1 = nc.getPoint(n1);
        Point p2 = nc.getPoint(n2);
        StyledMapRenderer.this.drawOrderNumber(p1, p2, orderNumber, clr);
    }

    /**
     * highlights a given GeneralPath using the settings from BasicStroke to match the line's
     * style. Width of the highlight is hard coded.
     * @param path
     * @param line
     */
    private void drawPathHighlight(GeneralPath path, BasicStroke line) {
        if(path == null)
            return;
        g.setColor(highlightColorTransparent);
        float w = (line.getLineWidth() + highlightLineWidth);
        if (useWiderHighlight) w+=widerHighlight;
        while(w >= line.getLineWidth()) {
            g.setStroke(new BasicStroke(w, line.getEndCap(), line.getLineJoin(), line.getMiterLimit()));
            g.draw(path);
            w -= highlightStep;
        }
    }
    /**
     * highlights a given point by drawing a rounded rectangle around it. Give the
     * size of the object you want to be highlighted, width is added automatically.
     */
    private void drawPointHighlight(Point p, int size) {
        g.setColor(highlightColorTransparent);
        int s = size + highlightPointRadius;
        if (useWiderHighlight) s+=widerHighlight;
        while(s >= size) {
            int r = (int) Math.floor(s/2);
            g.fillRoundRect(p.x-r, p.y-r, s, s, r, r);
            s -= highlightStep;
        }
    }

    public void drawRestriction(Image img, Point pVia, double vx, double vx2, double vy, double vy2, double angle, boolean selected) {
        // rotate image with direction last node in from to, and scale down image to 16*16 pixels
        Image smallImg = ImageProvider.createRotatedImage(img, angle, new Dimension(16, 16));
        int w = smallImg.getWidth(null), h=smallImg.getHeight(null);
        g.drawImage(smallImg, (int)(pVia.x+vx+vx2)-w/2, (int)(pVia.y+vy+vy2)-h/2, nc);

        if (selected) {
            g.setColor(isInactiveMode ? inactiveColor : relationSelectedColor);
            g.drawRect((int)(pVia.x+vx+vx2)-w/2-2,(int)(pVia.y+vy+vy2)-h/2-2, w+4, h+4);
        }
    }

    public void drawRestriction(Relation r, MapImage icon) {
        Way fromWay = null;
        Way toWay = null;
        OsmPrimitive via = null;

        /* find the "from", "via" and "to" elements */
        for (RelationMember m : r.getMembers()) {
            if(m.getMember().isIncomplete())
                return;
            else {
                if(m.isWay()) {
                    Way w = m.getWay();
                    if(w.getNodesCount() < 2) {
                        continue;
                    }

                    switch(m.getRole()) {
                    case "from":
                        if(fromWay == null) {
                            fromWay = w;
                        }
                        break;
                    case "to":
                        if(toWay == null) {
                            toWay = w;
                        }
                        break;
                    case "via":
                        if(via == null) {
                            via = w;
                        }
                    }
                } else if(m.isNode()) {
                    Node n = m.getNode();
                    if("via".equals(m.getRole()) && via == null) {
                        via = n;
                    }
                }
            }
        }

        if (fromWay == null || toWay == null || via == null)
            return;

        Node viaNode;
        if(via instanceof Node)
        {
            viaNode = (Node) via;
            if(!fromWay.isFirstLastNode(viaNode))
                return;
        }
        else
        {
            Way viaWay = (Way) via;
            Node firstNode = viaWay.firstNode();
            Node lastNode = viaWay.lastNode();
            Boolean onewayvia = false;

            String onewayviastr = viaWay.get("oneway");
            if(onewayviastr != null)
            {
                if("-1".equals(onewayviastr)) {
                    onewayvia = true;
                    Node tmp = firstNode;
                    firstNode = lastNode;
                    lastNode = tmp;
                } else {
                    onewayvia = OsmUtils.getOsmBoolean(onewayviastr);
                    if (onewayvia == null) {
                        onewayvia = false;
                    }
                }
            }

            if(fromWay.isFirstLastNode(firstNode)) {
                viaNode = firstNode;
            } else if (!onewayvia && fromWay.isFirstLastNode(lastNode)) {
                viaNode = lastNode;
            } else
                return;
        }

        /* find the "direct" nodes before the via node */
        Node fromNode;
        if(fromWay.firstNode() == via) {
            fromNode = fromWay.getNode(1);
        } else {
            fromNode = fromWay.getNode(fromWay.getNodesCount()-2);
        }

        Point pFrom = nc.getPoint(fromNode);
        Point pVia = nc.getPoint(viaNode);

        /* starting from via, go back the "from" way a few pixels
           (calculate the vector vx/vy with the specified length and the direction
           away from the "via" node along the first segment of the "from" way)
         */
        double distanceFromVia=14;
        double dx = (pFrom.x >= pVia.x) ? (pFrom.x - pVia.x) : (pVia.x - pFrom.x);
        double dy = (pFrom.y >= pVia.y) ? (pFrom.y - pVia.y) : (pVia.y - pFrom.y);

        double fromAngle;
        if(dx == 0.0) {
            fromAngle = Math.PI/2;
        } else {
            fromAngle = Math.atan(dy / dx);
        }
        double fromAngleDeg = Math.toDegrees(fromAngle);

        double vx = distanceFromVia * Math.cos(fromAngle);
        double vy = distanceFromVia * Math.sin(fromAngle);

        if(pFrom.x < pVia.x) {
            vx = -vx;
        }
        if(pFrom.y < pVia.y) {
            vy = -vy;
        }

        /* go a few pixels away from the way (in a right angle)
           (calculate the vx2/vy2 vector with the specified length and the direction
           90degrees away from the first segment of the "from" way)
         */
        double distanceFromWay=10;
        double vx2 = 0;
        double vy2 = 0;
        double iconAngle = 0;

        if(pFrom.x >= pVia.x && pFrom.y >= pVia.y) {
            if(!leftHandTraffic) {
                vx2 = distanceFromWay * Math.cos(Math.toRadians(fromAngleDeg - 90));
                vy2 = distanceFromWay * Math.sin(Math.toRadians(fromAngleDeg - 90));
            } else {
                vx2 = distanceFromWay * Math.cos(Math.toRadians(fromAngleDeg + 90));
                vy2 = distanceFromWay * Math.sin(Math.toRadians(fromAngleDeg + 90));
            }
            iconAngle = 270+fromAngleDeg;
        }
        if(pFrom.x < pVia.x && pFrom.y >= pVia.y) {
            if(!leftHandTraffic) {
                vx2 = distanceFromWay * Math.sin(Math.toRadians(fromAngleDeg));
                vy2 = distanceFromWay * Math.cos(Math.toRadians(fromAngleDeg));
            } else {
                vx2 = distanceFromWay * Math.sin(Math.toRadians(fromAngleDeg + 180));
                vy2 = distanceFromWay * Math.cos(Math.toRadians(fromAngleDeg + 180));
            }
            iconAngle = 90-fromAngleDeg;
        }
        if(pFrom.x < pVia.x && pFrom.y < pVia.y) {
            if(!leftHandTraffic) {
                vx2 = distanceFromWay * Math.cos(Math.toRadians(fromAngleDeg + 90));
                vy2 = distanceFromWay * Math.sin(Math.toRadians(fromAngleDeg + 90));
            } else {
                vx2 = distanceFromWay * Math.cos(Math.toRadians(fromAngleDeg - 90));
                vy2 = distanceFromWay * Math.sin(Math.toRadians(fromAngleDeg - 90));
            }
            iconAngle = 90+fromAngleDeg;
        }
        if(pFrom.x >= pVia.x && pFrom.y < pVia.y) {
            if(!leftHandTraffic) {
                vx2 = distanceFromWay * Math.sin(Math.toRadians(fromAngleDeg + 180));
                vy2 = distanceFromWay * Math.cos(Math.toRadians(fromAngleDeg + 180));
            } else {
                vx2 = distanceFromWay * Math.sin(Math.toRadians(fromAngleDeg));
                vy2 = distanceFromWay * Math.cos(Math.toRadians(fromAngleDeg));
            }
            iconAngle = 270-fromAngleDeg;
        }

        drawRestriction(isInactiveMode || r.isDisabled() ? icon.getDisabled() : icon.getImage(),
                pVia, vx, vx2, vy, vy2, iconAngle, r.isSelected());
    }

    public void drawTextOnPath(Way way, TextElement text) {
        if (way == null || text == null)
            return;
        String name = text.getString(way);
        if (name == null || name.isEmpty())
            return;

        Polygon poly = new Polygon();
        Point lastPoint = null;
        Iterator<Node> it = way.getNodes().iterator();
        double pathLength = 0;
        long dx, dy;
        while (it.hasNext()) {
            Node n = it.next();
            Point p = nc.getPoint(n);
            poly.addPoint(p.x, p.y);

            if(lastPoint != null) {
                dx = p.x - lastPoint.x;
                dy = p.y - lastPoint.y;
                pathLength += Math.sqrt(dx*dx + dy*dy);
            }
            lastPoint = p;
        }

        FontMetrics fontMetrics = g.getFontMetrics(text.font); // if slow, use cache
        Rectangle2D rec = fontMetrics.getStringBounds(name, g); // if slow, approximate by strlen()*maxcharbounds(font)

        if (rec.getWidth() > pathLength)
            return;

        double t1 = (pathLength/2 - rec.getWidth()/2) / pathLength;
        double t2 = (pathLength/2 + rec.getWidth()/2) / pathLength;

        double[] p1 = pointAt(t1, poly, pathLength);
        double[] p2 = pointAt(t2, poly, pathLength);

        if (p1 == null || p2 == null)
            return;

        double angleOffset;
        double offsetSign;
        double tStart;

        if (p1[0] < p2[0] &&
                p1[2] < Math.PI/2 &&
                p1[2] > -Math.PI/2) {
            angleOffset = 0;
            offsetSign = 1;
            tStart = t1;
        } else {
            angleOffset = Math.PI;
            offsetSign = -1;
            tStart = t2;
        }

        FontRenderContext frc = g.getFontRenderContext();
        GlyphVector gv = text.font.createGlyphVector(frc, name);

        for (int i=0; i<gv.getNumGlyphs(); ++i) {
            Rectangle2D rect = gv.getGlyphLogicalBounds(i).getBounds2D();
            double t = tStart + offsetSign * (rect.getX() + rect.getWidth()/2) / pathLength;
            double[] p = pointAt(t, poly, pathLength);
            if (p != null) {
                AffineTransform trfm = AffineTransform.getTranslateInstance(p[0] - rect.getX(), p[1]);
                trfm.rotate(p[2]+angleOffset);
                double off = -rect.getY() - rect.getHeight()/2 + text.yOffset;
                trfm.translate(-rect.getWidth()/2, off);
                if (isGlyphVectorDoubleTranslationBug()) {
                    // scale the translation components by one half
                    AffineTransform tmp = AffineTransform.getTranslateInstance(-0.5 * trfm.getTranslateX(), -0.5 * trfm.getTranslateY());
                    tmp.concatenate(trfm);
                    trfm = tmp;
                }
                gv.setGlyphTransform(i, trfm);
            }
        }
        displayText(gv, null, 0, 0, way.isDisabled(), text);
    }

    /**
     * draw way
     * @param showOrientation show arrows that indicate the technical orientation of
     *              the way (defined by order of nodes)
     * @param showOneway show symbols that indicate the direction of the feature,
     *              e.g. oneway street or waterway
     * @param onewayReversed for oneway=-1 and similar
     */
    public void drawWay(Way way, Color color, BasicStroke line, BasicStroke dashes, Color dashedColor, float offset,
            boolean showOrientation, boolean showHeadArrowOnly,
            boolean showOneway, boolean onewayReversed) {

        GeneralPath path = new GeneralPath();
        GeneralPath orientationArrows = showOrientation ? new GeneralPath() : null;
        GeneralPath onewayArrows = showOneway ? new GeneralPath() : null;
        GeneralPath onewayArrowsCasing = showOneway ? new GeneralPath() : null;
        Rectangle bounds = g.getClipBounds();
        if (bounds != null) {
            // avoid arrow heads at the border
            bounds.grow(100, 100);
        }

        double wayLength = 0;
        Point lastPoint = null;
        boolean initialMoveToNeeded = true;
        List<Node> wayNodes = way.getNodes();
        if (wayNodes.size() < 2) return;

        // only highlight the segment if the way itself is not highlighted
        if (!way.isHighlighted()) {
            GeneralPath highlightSegs = null;
            for (WaySegment ws : highlightWaySegments) {
                if (ws.way != way || ws.lowerIndex < offset) {
                    continue;
                }
                if(highlightSegs == null) {
                    highlightSegs = new GeneralPath();
                }

                Point p1 = nc.getPoint(ws.getFirstNode());
                Point p2 = nc.getPoint(ws.getSecondNode());
                highlightSegs.moveTo(p1.x, p1.y);
                highlightSegs.lineTo(p2.x, p2.y);
            }

            drawPathHighlight(highlightSegs, line);
        }

        Iterator<Point> it = new OffsetIterator(wayNodes, offset);
        while (it.hasNext()) {
            Point p = it.next();
            if (lastPoint != null) {
                Point p1 = lastPoint;
                Point p2 = p;

                /**
                 * Do custom clipping to work around openjdk bug. It leads to
                 * drawing artefacts when zooming in a lot. (#4289, #4424)
                 * (Looks like int overflow.)
                 */
                LineClip clip = new LineClip(p1, p2, bounds);
                if (clip.execute()) {
                    if (!p1.equals(clip.getP1())) {
                        p1 = clip.getP1();
                        path.moveTo(p1.x, p1.y);
                    } else if (initialMoveToNeeded) {
                        initialMoveToNeeded = false;
                        path.moveTo(p1.x, p1.y);
                    }
                    p2 = clip.getP2();
                    path.lineTo(p2.x, p2.y);

                    /* draw arrow */
                    if (showHeadArrowOnly ? !it.hasNext() : showOrientation) {
                        final double segmentLength = p1.distance(p2);
                        if (segmentLength != 0.0) {
                            final double l =  (10. + line.getLineWidth()) / segmentLength;

                            final double sx = l * (p1.x - p2.x);
                            final double sy = l * (p1.y - p2.y);

                            orientationArrows.moveTo (p2.x + cosPHI * sx - sinPHI * sy, p2.y + sinPHI * sx + cosPHI * sy);
                            orientationArrows.lineTo(p2.x, p2.y);
                            orientationArrows.lineTo (p2.x + cosPHI * sx + sinPHI * sy, p2.y - sinPHI * sx + cosPHI * sy);
                        }
                    }
                    if (showOneway) {
                        final double segmentLength = p1.distance(p2);
                        if (segmentLength != 0.0) {
                            final double nx = (p2.x - p1.x) / segmentLength;
                            final double ny = (p2.y - p1.y) / segmentLength;

                            final double interval = 60;
                            // distance from p1
                            double dist = interval - (wayLength % interval);

                            while (dist < segmentLength) {
                                for (int i=0; i<2; ++i) {
                                    float onewaySize = i == 0 ? 3f : 2f;
                                    GeneralPath onewayPath = i == 0 ? onewayArrowsCasing : onewayArrows;

                                    // scale such that border is 1 px
                                    final double fac = - (onewayReversed ? -1 : 1) * onewaySize * (1 + sinPHI) / (sinPHI * cosPHI);
                                    final double sx = nx * fac;
                                    final double sy = ny * fac;

                                    // Attach the triangle at the incenter and not at the tip.
                                    // Makes the border even at all sides.
                                    final double x = p1.x + nx * (dist + (onewayReversed ? -1 : 1) * (onewaySize / sinPHI));
                                    final double y = p1.y + ny * (dist + (onewayReversed ? -1 : 1) * (onewaySize / sinPHI));

                                    onewayPath.moveTo(x, y);
                                    onewayPath.lineTo (x + cosPHI * sx - sinPHI * sy, y + sinPHI * sx + cosPHI * sy);
                                    onewayPath.lineTo (x + cosPHI * sx + sinPHI * sy, y - sinPHI * sx + cosPHI * sy);
                                    onewayPath.lineTo(x, y);
                                }
                                dist += interval;
                            }
                        }
                        wayLength += segmentLength;
                    }
                }
            }
            lastPoint = p;
        }
        if(way.isHighlighted()) {
            drawPathHighlight(path, line);
        }
        displaySegments(path, orientationArrows, onewayArrows, onewayArrowsCasing, color, line, dashes, dashedColor);
    }

    public double getCircum() {
        return circum;
    }

    @Override
    public void getColors() {
        super.getColors();
        this.relationSelectedColor = PaintColors.RELATIONSELECTED.get();
        this.highlightColorTransparent = new Color(highlightColor.getRed(), highlightColor.getGreen(), highlightColor.getBlue(), 100);
        this.backgroundColor = PaintColors.getBackgroundColor();
    }

    @Override
    protected void getSettings(boolean virtual) {
        super.getSettings(virtual);
        paintSettings = MapPaintSettings.INSTANCE;

        circum = nc.getDist100Pixel();

        leftHandTraffic = Main.pref.getBoolean("mappaint.lefthandtraffic", false);

        useStrokes = paintSettings.getUseStrokesDistance() > circum;
        showNames = paintSettings.getShowNamesDistance() > circum;
        showIcons = paintSettings.getShowIconsDistance() > circum;
        isOutlineOnly = paintSettings.isOutlineOnly();
        orderFont = new Font(Main.pref.get("mappaint.font", "Droid Sans"), Font.PLAIN, Main.pref.getInteger("mappaint.fontsize", 8));

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                Main.pref.getBoolean("mappaint.use-antialiasing", true) ?
                        RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);

        highlightLineWidth = Main.pref.getInteger("mappaint.highlight.width", 4);
        highlightPointRadius = Main.pref.getInteger("mappaint.highlight.radius", 7);
        widerHighlight = Main.pref.getInteger("mappaint.highlight.bigger-increment", 5);
        highlightStep = Main.pref.getInteger("mappaint.highlight.step", 4);
    }

    private Path2D.Double getPath(Way w) {
        Path2D.Double path = new Path2D.Double();
        boolean initial = true;
        for (Node n : w.getNodes()) {
            EastNorth p = n.getEastNorth();
            if (p != null) {
                if (initial) {
                    path.moveTo(p.getX(), p.getY());
                    initial = false;
                } else {
                    path.lineTo(p.getX(), p.getY());
                }
            }
        }
        return path;
    }

    private boolean isAreaVisible(Path2D.Double area) {
        Rectangle2D bounds = area.getBounds2D();
        if (bounds.isEmpty()) return false;
        Point2D p = nc.getPoint2D(new EastNorth(bounds.getX(), bounds.getY()));
        if (p.getX() > nc.getWidth()) return false;
        if (p.getY() < 0) return false;
        p = nc.getPoint2D(new EastNorth(bounds.getX() + bounds.getWidth(), bounds.getY() + bounds.getHeight()));
        if (p.getX() < 0) return false;
        if (p.getY() > nc.getHeight()) return false;
        return true;
    }

    public boolean isInactiveMode() {
        return isInactiveMode;
    }

    public boolean isShowIcons() {
        return showIcons;
    }

    public boolean isShowNames() {
        return showNames;
    }

    private double[] pointAt(double t, Polygon poly, double pathLength) {
        double totalLen = t * pathLength;
        double curLen = 0;
        long dx, dy;
        double segLen;

        // Yes, it is inefficient to iterate from the beginning for each glyph.
        // Can be optimized if it turns out to be slow.
        for (int i = 1; i < poly.npoints; ++i) {
            dx = poly.xpoints[i] - poly.xpoints[i-1];
            dy = poly.ypoints[i] - poly.ypoints[i-1];
            segLen = Math.sqrt(dx*dx + dy*dy);
            if (totalLen > curLen + segLen) {
                curLen += segLen;
                continue;
            }
            return new double[] {
                    poly.xpoints[i-1]+(totalLen - curLen)/segLen*dx,
                    poly.ypoints[i-1]+(totalLen - curLen)/segLen*dy,
                    Math.atan2(dy, dx)};
        }
        return null;
    }

    private class ComputeStyleListWorker implements Callable<List<StyleRecord>>, Visitor {
        private final List<? extends OsmPrimitive> input;
        private final int from;
        private final int to;
        private final List<StyleRecord> output;
        private final DataSet data;

        private final ElemStyles styles = MapPaintStyles.getStyles();

        private final boolean drawArea = circum <= Main.pref.getInteger("mappaint.fillareas", 10000000);
        private final boolean drawMultipolygon = drawArea && Main.pref.getBoolean("mappaint.multipolygon", true);
        private final boolean drawRestriction = Main.pref.getBoolean("mappaint.restriction", true);

        /**
         * Constructs a new {@code ComputeStyleListWorker}.
         * @param input the primitives to process
         * @param from first index of <code>input</code> to use
         * @param to last index + 1
         * @param output the list of styles to which styles will be added
         * @param data the data set
         */
        public ComputeStyleListWorker(final List<? extends OsmPrimitive> input, int from, int to, List<StyleRecord> output, DataSet data) {
            this.input = input;
            this.from = from;
            this.to = to;
            this.output = output;
            this.data = data;
            this.styles.setDrawMultipolygon(drawMultipolygon);
        }

        @Override
        public List<StyleRecord> call() throws Exception {
            MapCSSStyleSource.STYLE_SOURCE_LOCK.readLock().lock();
            try {
                for (int i = from; i<to; i++) {
                    OsmPrimitive osm = input.get(i);
                    if (osm.isDrawable()) {
                        osm.accept(this);
                    }
                }
                return output;
            } finally {
                MapCSSStyleSource.STYLE_SOURCE_LOCK.readLock().unlock();
            }
        }

        @Override
        public void visit(Node n) {
            if (n.isDisabled()) {
                add(n, FLAG_DISABLED);
            } else if (data.isSelected(n)) {
                add(n, FLAG_SELECTED);
            } else if (n.isMemberOfSelected()) {
                add(n, FLAG_MEMBER_OF_SELECTED);
            } else {
                add(n, FLAG_NORMAL);
            }
        }

        @Override
        public void visit(Way w) {
            if (w.isDisabled()) {
                add(w, FLAG_DISABLED);
            } else if (data.isSelected(w)) {
                add(w, FLAG_SELECTED);
            } else if (w.isMemberOfSelected()) {
                add(w, FLAG_MEMBER_OF_SELECTED);
            } else {
                add(w, FLAG_NORMAL);
            }
        }

        @Override
        public void visit(Relation r) {
            if (r.isDisabled()) {
                add(r, FLAG_DISABLED);
            } else if (data.isSelected(r)) {
                add(r, FLAG_SELECTED);
            } else {
                add(r, FLAG_NORMAL);
            }
        }

        @Override
        public void visit(Changeset cs) {
            throw new UnsupportedOperationException();
        }

        public void add(Node osm, int flags) {
            StyleList sl = styles.get(osm, circum, nc);
            for (ElemStyle s : sl) {
                output.add(new StyleRecord(s, osm, flags));
            }
        }

        public void add(Relation osm, int flags) {
            StyleList sl = styles.get(osm, circum, nc);
            for (ElemStyle s : sl) {
                if (drawMultipolygon && drawArea && s instanceof AreaElemStyle && (flags & FLAG_DISABLED) == 0) {
                    output.add(new StyleRecord(s, osm, flags));
                } else if (drawRestriction && s instanceof NodeElemStyle) {
                    output.add(new StyleRecord(s, osm, flags));
                }
            }
        }

        public void add(Way osm, int flags) {
            StyleList sl = styles.get(osm, circum, nc);
            for (ElemStyle s : sl) {
                if (!(drawArea && (flags & FLAG_DISABLED) == 0) && s instanceof AreaElemStyle) {
                    continue;
                }
                output.add(new StyleRecord(s, osm, flags));
            }
        }
    }

    private class ConcurrentTasksHelper {

        private final List<StyleRecord> allStyleElems;
        private final DataSet data;

        public ConcurrentTasksHelper(List<StyleRecord> allStyleElems, DataSet data) {
            this.allStyleElems = allStyleElems;
            this.data = data;
        }

        void process(List<? extends OsmPrimitive> prims) {
            final List<ComputeStyleListWorker> tasks = new ArrayList<>();
            final int bucketsize = Math.max(100, prims.size()/THREAD_POOL.a/3);
            final int noBuckets = (prims.size() + bucketsize - 1) / bucketsize;
            final boolean singleThread = THREAD_POOL.a == 1 || noBuckets == 1;
            for (int i=0; i<noBuckets; i++) {
                int from = i*bucketsize;
                int to = Math.min((i+1)*bucketsize, prims.size());
                List<StyleRecord> target = singleThread ? allStyleElems : new ArrayList<StyleRecord>(to - from);
                tasks.add(new ComputeStyleListWorker(prims, from, to, target, data));
            }
            if (singleThread) {
                try {
                    for (ComputeStyleListWorker task : tasks) {
                        task.call();
                    }
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            } else if (!tasks.isEmpty()) {
                try {
                    for (Future<List<StyleRecord>> future : THREAD_POOL.b.invokeAll(tasks)) {
                        allStyleElems.addAll(future.get());
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    @Override
    public void render(final DataSet data, boolean renderVirtualNodes, Bounds bounds) {
        BBox bbox = bounds.toBBox();
        getSettings(renderVirtualNodes);

        data.getReadLock().lock();
        try {
            highlightWaySegments = data.getHighlightedWaySegments();

            long timeStart=0, timePhase1=0, timeFinished;
            if (Main.isTraceEnabled()) {
                timeStart = System.currentTimeMillis();
                System.err.print("BENCHMARK: rendering ");
                Main.debug(null);
            }

            List<Node> nodes = data.searchNodes(bbox);
            List<Way> ways = data.searchWays(bbox);
            List<Relation> relations = data.searchRelations(bbox);

            final List<StyleRecord> allStyleElems = new ArrayList<>(nodes.size()+ways.size()+relations.size());

            ConcurrentTasksHelper helper = new ConcurrentTasksHelper(allStyleElems, data);

            // Need to process all relations first.
            // Reason: Make sure, ElemStyles.getStyleCacheWithRange is
            // not called for the same primitive in parallel threads.
            // (Could be synchronized, but try to avoid this for
            // performance reasons.)
            helper.process(relations);
            helper.process(new CompositeList<>(nodes, ways));

            if (Main.isTraceEnabled()) {
                timePhase1 = System.currentTimeMillis();
                System.err.print("phase 1 (calculate styles): " + (timePhase1 - timeStart) + " ms");
            }

            Collections.sort(allStyleElems); // TODO: try parallel sort when switching to Java 8

            for (StyleRecord r : allStyleElems) {
                r.style.paintPrimitive(
                        r.osm,
                        paintSettings,
                        StyledMapRenderer.this,
                        (r.flags & FLAG_SELECTED) != 0,
                        (r.flags & FLAG_MEMBER_OF_SELECTED) != 0
                );
            }

            if (Main.isTraceEnabled()) {
                timeFinished = System.currentTimeMillis();
                System.err.println("; phase 2 (draw): " + (timeFinished - timePhase1) + " ms; total: " + (timeFinished - timeStart) + " ms" +
                    " (scale: " + circum + " zoom level: " + Selector.GeneralSelector.scale2level(circum) + ")");
            }

            drawVirtualNodes(data, bbox);
        } finally {
            data.getReadLock().unlock();
        }
    }
}
