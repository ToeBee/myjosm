// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.tools.bzip2.CBZip2InputStream;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.data.notes.Note;
import org.openstreetmap.josm.gui.layer.NoteLayer;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.xml.sax.SAXException;

/**
 * File importer that reads note dump files (*.osn)
 */
public class NoteImporter extends FileImporter {

    private static final ExtensionFileFilter FILE_FILTER = new ExtensionFileFilter(
            "osn,osn.bz2", "osn", tr("Note Files") + " (*.osn *.osn.bz2)");

    public NoteImporter() {
        super(FILE_FILTER);
    }

    @Override
    public void importData(File file, ProgressMonitor progressMonitor) throws IOException {
        Main.debug("importing notes file " + file.getAbsolutePath());
        final String fileName = file.getName();
        InputStream is = new FileInputStream(file);
        if(fileName.endsWith(".bz2")) {
            is = new CBZip2InputStream(is);
        }
        NoteReader reader = new NoteReader(is);
        try {
            final List<Note> fileNotes = reader.parse();

            List<NoteLayer> noteLayers = null;
            if (Main.map != null) {
                noteLayers = Main.map.mapView.getLayersOfType(NoteLayer.class);
            }
            if (noteLayers != null && noteLayers.size() > 0) {
                NoteLayer layer = noteLayers.get(0);
                layer.addNotes(fileNotes);
            } else {
                GuiHelper.runInEDT(new Runnable() {
                    @Override
                    public void run() {
                        NoteLayer layer = new NoteLayer(fileNotes, fileName);
                        Main.main.addLayer(layer);
                    }
                });
            }
        } catch (SAXException e) {
            Main.error("error opening up notes file");
            Main.error(e, true);
            throw new IOException(tr("Error loading notes file {1}", fileName), e);
        }
    }

}
