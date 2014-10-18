// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.util.List;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.upload.UploadNotesTask;
import org.openstreetmap.josm.data.osm.NoteData;
import org.openstreetmap.josm.gui.layer.NoteLayer;
import org.openstreetmap.josm.gui.progress.PleaseWaitProgressMonitor;

public class UploadNotesAction extends JosmAction {

    @Override
    public void actionPerformed(ActionEvent e) {
        List<NoteLayer> noteLayers = null;
        if (Main.map != null) {
            noteLayers = Main.map.mapView.getLayersOfType(NoteLayer.class);
        }
        NoteLayer layer;
        if (noteLayers != null && noteLayers.size() > 0) {
            layer = noteLayers.get(0);
        } else {
            Main.error("No note layer found");
            return;
        }
        Main.debug("uploading note changes");
        NoteData noteData = layer.getNoteData();

        if(noteData == null || !noteData.isModified()) {
            Main.debug("No changed notes to upload");
            return;
        }
        PleaseWaitProgressMonitor monitor = new PleaseWaitProgressMonitor(tr("Uploading Notes"));
        UploadNotesTask uploadTask = new UploadNotesTask();
        uploadTask.uploadNotes(noteData, new PleaseWaitProgressMonitor("Uploading notes to server"));
    }
}
