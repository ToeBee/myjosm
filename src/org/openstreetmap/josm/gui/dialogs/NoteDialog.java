// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.mapmode.AddNoteAction;
import org.openstreetmap.josm.data.notes.Note;
import org.openstreetmap.josm.data.notes.Note.State;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.MapView.LayerChangeListener;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.NoteLayer;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Dialog to display and manipulate notes
 */
public class NoteDialog extends ToggleDialog implements LayerChangeListener {

    /** File name for 16x16 unresolved note icon */
    public static final String ICON_OPEN_16 = "note_open_16x16.png";
    /** File name for 24x24 unresolved note icon */
    public static final String ICON_OPEN_24 = "note_open_24x24.png";
    /** File name for 16x16 resolved note icon */
    public static final String ICON_CLOSED_16 = "note_closed_16x16.png";
    /** File name for 24x24 resolved note icon */
    public static final String ICON_CLOSED_24 = "note_closed_24x24.png";
    /** File name for 16x16 newly created note icon */
    public static final String ICON_NEW_16 = "note_new_16x16.png";
    /** File name for 24x24 newly created note icon */
    public static final String ICON_NEW_24 = "note_new_24x24.png";
    /** File name for adding a comment to a note */
    public static final String ICON_COMMENT = "note_comment.png";
    /** Small icon size for use in graphics calculations */
    public static final int ICON_SMALL_SIZE = 16;
    /** Large icon size for use in graphics calculations */
    public static final int ICON_LARGE_SIZE = 24;

    private NoteTableModel model;
    private JList<Note> displayList;
    private final AddCommentAction addCommentAction;
    private final CloseAction closeAction;
    private final NewAction newAction;
    private final ReopenAction reopenAction;

    private NoteLayer noteLayer;
    private Note selectedNote = null;

    /** Creates a new toggle dialog for notes */
    public NoteDialog() {
        super("Notes", "notes", "List of notes", null, 150);
        Main.debug("constructed note dialog");
        addCommentAction = new AddCommentAction();
        closeAction = new CloseAction();
        newAction = new NewAction();
        reopenAction = new ReopenAction();
        build();
    }

    @Override
    public void showDialog() {
        Main.debug("showing note dialog. Note layer: " + noteLayer);
        super.showDialog();
    }

    private void build() {
        model = new NoteTableModel();
        displayList = new JList<Note>(model);
        displayList.setCellRenderer(new NoteRenderer());
        displayList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        displayList.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                selectedNote = displayList.getSelectedValue();
                updateButtonStates();
                noteLayer.setSelectedNote(selectedNote);
            }});

        JPanel pane = new JPanel(new BorderLayout());
        pane.add(new JScrollPane(displayList), BorderLayout.CENTER);

        createLayout(pane, false, Arrays.asList(new SideButton[]{
                new SideButton(newAction, false),
                new SideButton(addCommentAction, false),
                new SideButton(closeAction, false),
                new SideButton(reopenAction, false)}));
        updateButtonStates();
    }

    private void updateButtonStates() {
        boolean enabled = selectedNote != null;
        closeAction.setEnabled(enabled);
        addCommentAction.setEnabled(enabled);
        reopenAction.setEnabled(enabled);
    }


    /**
     * Sets the list of notes to be displayed in the dialog.
     * The dialog should match the notes displayed in the note layer.
     * @param noteList List of notes to display
     */
    public void setNoteList(List<Note> noteList) {
        model.setData(noteList);
        this.repaint();
    }

    /**
     * Set the selected note. Causes the dialog to scroll to and highlight
     * the given note. Clears the selection if null.
     * @param note Note to select
     */
    public void setSelectedNote(Note note) {
        selectedNote = note;
        if (selectedNote == null) {
            displayList.clearSelection();
        } else {
            displayList.setSelectedValue(selectedNote, true);
        }
        updateButtonStates();
    }

    @Override
    public void showNotify() {
        MapView.addLayerChangeListener(this);
    }

    @Override
    public void hideNotify() {
        MapView.removeLayerChangeListener(this);
    }

    @Override
    public void activeLayerChange(Layer oldLayer, Layer newLayer) { }

    @Override
    public void layerAdded(Layer newLayer) {
        Main.debug("layer added: " + newLayer);
        if (newLayer instanceof NoteLayer) {
            Main.debug("note layer added");
            model.setData(((NoteLayer)newLayer).getNotes());
            noteLayer = (NoteLayer)newLayer;
        }
    }

    @Override
    public void layerRemoved(Layer oldLayer) {
        Main.debug("layer removed " + oldLayer);
        if (oldLayer instanceof NoteLayer) {
            model.clearData();
            noteLayer = null;
            if (Main.map.mapMode instanceof AddNoteAction) {
                Main.map.selectMapMode(Main.map.mapModeSelect);
            }
        }
    }

    private class NoteRenderer implements ListCellRenderer<Note> {

        private DefaultListCellRenderer defaultListCellRenderer = new DefaultListCellRenderer();
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy kk:mm");

        @Override
        public Component getListCellRendererComponent(JList<? extends Note> list, Note note, int index,
                boolean isSelected, boolean cellHasFocus) {
            Component comp = defaultListCellRenderer.getListCellRendererComponent(list, note, index, isSelected, cellHasFocus);
            if (note != null && comp instanceof JLabel) {
                String text = note.getFirstComment().getText();
                String toolTipText = note.getFirstComment().getUser().getName() + " @ " + sdf.format(note.getCreatedAt());
                JLabel jlabel = (JLabel)comp;
                jlabel.setText(text);
                ImageIcon icon;
                if (note.getId() < 0) {
                    icon = ImageProvider.get("notes", ICON_NEW_16);
                } else if (note.getState() == State.closed) {
                    icon = ImageProvider.get("notes", ICON_CLOSED_16);
                } else {
                    icon = ImageProvider.get("notes", ICON_OPEN_16);
                }
                jlabel.setIcon(icon);
                jlabel.setToolTipText(toolTipText);
            }
            return comp;
        }

    }

    class NoteTableModel extends AbstractListModel<Note> {
        private List<Note> data;

        public NoteTableModel() {
            data = new ArrayList<Note>();
            if (Main.isDisplayingMapView()) {
               List<NoteLayer> layers = Main.map.mapView.getLayersOfType(NoteLayer.class);
               NoteLayer layer = layers.get(0);
               data = layer.getNotes();
            }
        }

        @Override
        public int getSize() {
            if (data == null) {
                return 0;
            }
            return data.size();
        }

        @Override
        public Note getElementAt(int index) {
            return data.get(index);
        }

        public void setData(List<Note> noteList) {
            data.clear();
            data.addAll(noteList);
            fireContentsChanged(this, 0, noteList.size());
        }

        public void clearData() {
            data.clear();
            fireIntervalAdded(this, 0, getSize());
        }
    }

    class AddCommentAction extends AbstractAction {

        public AddCommentAction() {
            putValue(SHORT_DESCRIPTION,tr("Add comment"));
            putValue(NAME, tr("Comment"));
            putValue(SMALL_ICON, ImageProvider.get("notes", ICON_COMMENT));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Main.debug("add comment action fired");
            Note note = displayList.getSelectedValue();
            if (note == null) {
                JOptionPane.showMessageDialog(Main.map,
                        "You must select a note first",
                        "No note selected",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            Object userInput = JOptionPane.showInputDialog(Main.map,
                    tr("Add comment to note:"),
                    tr("Add comment"),
                    JOptionPane.QUESTION_MESSAGE,
                    ImageProvider.get("notes", ICON_COMMENT),
                    null,null);
            if (userInput == null) { //user pressed cancel
                return;
            }

            Main.debug("adding comment to note: " + note);
            noteLayer.addCommentToNote(note, userInput.toString());
        }
    }

    class CloseAction extends AbstractAction {

        public CloseAction() {
            putValue(SHORT_DESCRIPTION,tr("Close note"));
            putValue(NAME, tr("Close"));
            putValue(SMALL_ICON, ImageProvider.get("notes", ICON_CLOSED_24));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Main.debug("close action fired");
            Object userInput = JOptionPane.showInputDialog(Main.map,
                    tr("Close note with message:"),
                    tr("Close Note"),
                    JOptionPane.QUESTION_MESSAGE,
                    ImageProvider.get("notes", ICON_CLOSED_24),
                    null,null);
            if (userInput == null) { //user pressed cancel
                return;
            }
            Note note = displayList.getSelectedValue();
            noteLayer.closeNote(note, userInput.toString());
        }
    }

    class NewAction extends AbstractAction {

        public NewAction() {
            putValue(SHORT_DESCRIPTION,tr("Create a new note"));
            putValue(NAME, tr("Create"));
            putValue(SMALL_ICON, ImageProvider.get("notes", ICON_NEW_24));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Main.debug("create action fired");
            if (noteLayer == null) { //there is no notes layer. Create one first
                Main.map.mapView.addLayer(new NoteLayer());
            }
            Main.map.selectMapMode(new AddNoteAction(Main.map, noteLayer));
        }
    }

    class ReopenAction extends AbstractAction {

        public ReopenAction() {
            putValue(SHORT_DESCRIPTION,tr("Reopen note"));
            putValue(NAME, tr("Reopen"));
            putValue(SMALL_ICON, ImageProvider.get("notes", ICON_OPEN_24));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Main.debug("reopen action fired");
            Object userInput = JOptionPane.showInputDialog(Main.map,
                    tr("Reopen note with message:"),
                    tr("Reopen note"),
                    JOptionPane.QUESTION_MESSAGE,
                    ImageProvider.get("notes", ICON_OPEN_24),
                    null,null);
            if (userInput == null) { //user pressed cancel
                return;
            }
            Note note = displayList.getSelectedValue();
            noteLayer.reOpenNote(note, userInput.toString());
        }
    }
}
