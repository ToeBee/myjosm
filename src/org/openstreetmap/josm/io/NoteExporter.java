// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.io.IOException;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.NoteLayer;

public class NoteExporter extends FileExporter {

    public static final ExtensionFileFilter FILE_FILTER = new ExtensionFileFilter(
            "osn", "osn", tr("Note Files") + " (*.osn)");

    public NoteExporter() {
        super(FILE_FILTER);
    }

    @Override
    public boolean acceptFile(File pathname, Layer layer) {
        Main.debug("checking if file is accepted" + pathname);
        if (!(layer instanceof NoteLayer))
            return false;
        return super.acceptFile(pathname, layer);
    }

    @Override
    public void exportData(File file, Layer layer) throws IOException {
        Main.debug("exporting note file: " + file);
    }
}
