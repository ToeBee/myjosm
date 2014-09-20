// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JToolTip;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.notes.Note;
import org.openstreetmap.josm.data.notes.Note.State;
import org.openstreetmap.josm.data.notes.NoteComment;
import org.openstreetmap.josm.data.osm.User;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.JosmUserIdentityManager;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.gui.dialogs.NoteDialog;
import org.openstreetmap.josm.tools.ColorHelper;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * A layer to hold Note objects
 */
public class NoteLayer extends AbstractModifiableLayer implements MouseListener {

    private final List<Note> notes;
    private long newNoteId = -1;

    private Note selectedNote;

    /**
     * Create a new note layer with a set of notes
     * @param notes A list of notes to show in this layer
     * @param name The name of the layer. Typically "Notes"
     */
    public NoteLayer(List<Note> notes, String name) {
        super(name);
        this.notes = notes;
        init();
    }

    /** Convenience constructor that creates a layer with an empty note list */
    public NoteLayer() {
        super(tr("Notes"));
        notes = new ArrayList<>();
        init();
    }

    private void init() {
        if(Main.map != null && Main.map.mapView != null) {
            Main.map.mapView.addMouseListener(this);
        }
    }

    public void setSelectedNote(Note note) {
        selectedNote = note;
        Main.map.mapView.repaint();
    }

    @Override
    public boolean isModified() {
        for (Note note : notes) {
            if (note.getId() < 0) { //notes with negative IDs are new
                return true;
            }
            for (NoteComment comment : note.getComments()) {
                if (comment.getIsNew()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean requiresUploadToServer() {
        return isModified();
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds box) {
        for (Note note : notes) {
            Point p = mv.getPoint(note.getLatLon());

            ImageIcon icon = null;
            if (note.getId() < 0) {
                icon = ImageProvider.get("notes", NoteDialog.ICON_NEW_16);
            } else if (note.getState() == State.closed) {
                icon = ImageProvider.get("notes", NoteDialog.ICON_CLOSED_16);
            } else {
                icon = ImageProvider.get("notes", NoteDialog.ICON_OPEN_16);
            }
            int width = icon.getIconWidth();
            int height = icon.getIconHeight();
            g.drawImage(icon.getImage(), p.x - (width / 2), p.y - height, Main.map.mapView);
        }
        if (selectedNote != null) {
            JToolTip toolTip = new JToolTip();
            toolTip.setTipText(selectedNote.getFirstComment().getText());
            Point p = mv.getPoint(selectedNote.getLatLon());

            g.setColor(ColorHelper.html2color(Main.pref.get("color.selected")));
            g.drawRect(p.x - (NoteDialog.ICON_SMALL_SIZE / 2), p.y - NoteDialog.ICON_SMALL_SIZE, NoteDialog.ICON_SMALL_SIZE - 1, NoteDialog.ICON_SMALL_SIZE - 1);

            int tx = p.x + (NoteDialog.ICON_SMALL_SIZE / 2) + 5;
            int ty = p.y - NoteDialog.ICON_SMALL_SIZE - 1;
            g.translate(tx, ty);

            for (int x = 0; x < 2; x++) {
                Dimension d = toolTip.getUI().getPreferredSize(toolTip);
                d.width = Math.min(d.width, (mv.getWidth() * 1 / 2));
                toolTip.setSize(d);
                toolTip.paint(g);
            }

            g.translate(-tx, -ty);
        }
    }

    @Override
    public Icon getIcon() {
        return ImageProvider.get("notes", NoteDialog.ICON_OPEN_16);
    }

    @Override
    public String getToolTipText() {
        return notes.size() + " " + tr("Notes");
    }

    @Override
    public void mergeFrom(Layer from) {
        throw new UnsupportedOperationException("Notes layer does not support merging yet");
    }

    @Override
    public boolean isMergable(Layer other) {
        return false;
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor v) {
    }

    @Override
    public Object getInfoComponent() {
        StringBuilder sb = new StringBuilder();
        sb.append(tr("Notes layer"));
        sb.append("\n");
        sb.append(tr("Total notes:"));
        sb.append(" ");
        sb.append(notes.size());
        sb.append("\n");
        sb.append(tr("Changes need uploading?"));
        sb.append(" ");
        sb.append(isModified());
        return sb.toString();
    }

    @Override
    public Action[] getMenuEntries() {
        List<Action> actions = new ArrayList<>();
        actions.add(LayerListDialog.getInstance().createShowHideLayerAction());
        actions.add(LayerListDialog.getInstance().createDeleteLayerAction());
        actions.add(new LayerListPopup.InfoAction(this));
        return actions.toArray(new Action[actions.size()]);
    }

    /**
     * Returns the notes stored in this layer
     * @return List of Note objects
     */
    public List<Note> getNotes() {
        return notes;
    }

    /**
     * Add notes to the layer. It only adds a note if the ID is not already present
     * @param newNotes A list of notes to add
     */
    public void addNotes(List<Note> newNotes) {
        for (Note newNote : newNotes) {
            if (!notes.contains(newNote)) {
                notes.add(newNote);
            }
            if(newNote.getId() <= newNoteId) {
                newNoteId = newNote.getId() - 1;
            }
        }
        dataUpdated();
        Main.debug("notes in layer: " + notes.size());
    }

    public void createNote(LatLon location, String text) {
        Note note = new Note(location);
        note.setCreatedAt(new Date());
        note.setId(newNoteId--);
        NoteComment comment = new NoteComment(new Date(), getCurrentUser(), text, NoteComment.Action.opened, true);
        note.addComment(comment);
        notes.add(note);
        dataUpdated();
    }

    public void addCommentToNote(Note note, String text) {
        if(!notes.contains(note)) {
            throw new IllegalArgumentException("Note to modify must be in layer");
        }
        NoteComment comment = new NoteComment(new Date(), getCurrentUser(), text, NoteComment.Action.commented, true);
        note.addComment(comment);
        dataUpdated();
    }

    public void closeNote(Note note, String text) {
        if(!notes.contains(note)) {
            throw new IllegalArgumentException("Note to close must be in layer");
        }
        NoteComment comment = new NoteComment(new Date(), getCurrentUser(), text, NoteComment.Action.closed, true);
        note.addComment(comment);
        dataUpdated();
    }

    public void reOpenNote(Note note, String text) {
        if(!notes.contains(note)) {
            throw new IllegalArgumentException("Note to reopen must be in layer");
        }
        NoteComment comment = new NoteComment(new Date(), getCurrentUser(), text, NoteComment.Action.reopened, true);
        note.addComment(comment);
        dataUpdated();
    }

    private void dataUpdated() {
        Main.map.mapView.repaint();
        Main.map.noteDialog.setNoteList(notes);
    }

    private User getCurrentUser() {
        JosmUserIdentityManager userMgr = JosmUserIdentityManager.getInstance();
        return User.createOsmUser(userMgr.getUserId(), userMgr.getUserName());
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        Main.debug("caught mouse clicked event");
        if (e.getButton() != MouseEvent.BUTTON1) {
            return;
        }
        Point clickPoint = e.getPoint();
        double snapDistance = 10;
        double minDistance = Double.MAX_VALUE;
        Note closestNote = null;
        for (Note note : notes) {
            Point notePoint = Main.map.mapView.getPoint(note.getLatLon());
            //move the note point to the center of the icon where users are most likely to click when selecting
            notePoint.setLocation(notePoint.getX(), notePoint.getY() - NoteDialog.ICON_SMALL_SIZE / 2);
            double dist = clickPoint.distanceSq(notePoint);
            if (minDistance > dist && clickPoint.distance(notePoint) < snapDistance ) {
                minDistance = dist;
                closestNote = note;
            }
        }
        if (closestNote == null) {
            selectedNote = null;
        } else {
            selectedNote = closestNote;
        }
        Main.debug("selected note: " + selectedNote);
        Main.map.noteDialog.setSelectedNote(selectedNote);
        Main.map.mapView.repaint();
    }

    @Override
    public void mousePressed(MouseEvent e) { }

    @Override
    public void mouseReleased(MouseEvent e) { }

    @Override
    public void mouseEntered(MouseEvent e) { }

    @Override
    public void mouseExited(MouseEvent e) { }
}
