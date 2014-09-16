// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs;

import java.awt.BorderLayout;
import java.awt.Component;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.notes.Note;
import org.openstreetmap.josm.data.notes.Note.State;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.MapView.LayerChangeListener;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.NoteLayer;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Dialog to display and manipulate notes
 */
public class NoteDialog extends ToggleDialog implements LayerChangeListener {

    private NoteTableModel model;
    private JList<Note> displayList;

    public NoteDialog() {
        super("Notes", "notes", "List of notes", null, 150);
        Main.debug("constructed note dialog");
        build();
    }

    @Override
    public void showDialog() {
        Main.debug("showing note dialog");
        super.showDialog();
    }

    private void build() {
        model = new NoteTableModel();
        displayList = new JList<Note>(model);
        displayList.setCellRenderer(new NoteRenderer());

        JPanel pane = new JPanel(new BorderLayout());
        pane.add(new JScrollPane(displayList), BorderLayout.CENTER);

        createLayout(pane, false, null);
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
        if(newLayer instanceof NoteLayer) {
            Main.debug("note layer added");
            model.setData(((NoteLayer)newLayer).getNotes());
        }
    }

    @Override
    public void layerRemoved(Layer oldLayer) {
        Main.debug("layer removed " + oldLayer);
        if(oldLayer instanceof NoteLayer) {
            model.clearData();
        }
    }

    private class NoteRenderer implements ListCellRenderer<Note> {

        private DefaultListCellRenderer defaultListCellRenderer = new DefaultListCellRenderer();
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy kk:mm");

        @Override
        public Component getListCellRendererComponent(JList<? extends Note> list, Note note, int index,
                boolean isSelected, boolean cellHasFocus) {
            Component comp = defaultListCellRenderer.getListCellRendererComponent(list, note, index, isSelected, cellHasFocus);
            if(note != null && comp instanceof JLabel) {
                String text = note.getFirstComment().getUser().getName()
                        + " " + sdf.format(note.getCreatedAt()) + " " + note.getFirstComment().getText();
                JLabel jlabel = (JLabel)comp;
                jlabel.setText(text);
                ImageIcon icon;
                if(note.getId() < 0) {
                    icon = ImageProvider.get("notes", "note_new_16x16.png");
                } else if (note.getState() == State.closed) {
                    icon = ImageProvider.get("notes", "note_closed_16x16.png");
                } else {
                    icon = ImageProvider.get("notes", "note_open_16x16.png");
                }
                jlabel.setIcon(icon);
                jlabel.setToolTipText("note tooltip");
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
            fireIntervalAdded(this, 0, getSize());
            Main.debug("set note list and fired intervalAdded. List size: " + data.size());
        }

        public void clearData() {
            data.clear();
            fireIntervalAdded(this, 0, getSize());
        }
    }
}
