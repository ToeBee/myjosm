// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions.mapmode;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.MouseEvent;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.dialogs.NoteDialog;
import org.openstreetmap.josm.gui.layer.NoteLayer;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Map mode to add a new note. Listens for a mouse click and then
 * prompts the user for text and adds a note to the note layer
 */
public class AddNoteAction extends MapMode {

    private NoteLayer noteLayer;

    /**
     * Construct a new map mode.
     * @param mapFrame Map frame to pass to the superconstructor
     * @param layer Note layer. May not be null
     */
    public AddNoteAction(MapFrame mapFrame, NoteLayer layer) {
        super(tr("Add a new Note"), "addnote.png",
            tr("Add note mode"),
            mapFrame, ImageProvider.getCursor("crosshair", "create_note"));
        if (layer == null) {
            throw new IllegalArgumentException("Note layer must not be null");
        }
        noteLayer = layer;
    }

    @Override
    public String getModeHelpText() {
        return tr("Click the location where you wish to create a new note");
    }

    @Override
    public void enterMode() {
        super.enterMode();
        Main.map.mapView.addMouseListener(this);
    }

    @Override
    public void exitMode() {
        super.exitMode();
        Main.map.mapView.removeMouseListener(this);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        Main.map.selectMapMode(Main.map.mapModeSelect);
        LatLon latlon = Main.map.mapView.getLatLon(e.getPoint().x, e.getPoint().y);
        Object userInput = JOptionPane.showInputDialog(Main.map,
                tr("Create a new note"),
                tr("Create note"),
                JOptionPane.QUESTION_MESSAGE,
                ImageProvider.get("notes", NoteDialog.ICON_NEW_24),
                null,null);
        if(userInput == null) { //user pressed cancel
            return;
        }
        noteLayer.createNote(latlon, userInput.toString());
    }
}
