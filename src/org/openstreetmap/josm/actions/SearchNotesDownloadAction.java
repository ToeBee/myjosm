// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import java.awt.event.ActionEvent;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.downloadtasks.DownloadNotesTask;

/**
 * Action to use the Notes search API to download a set of notes
 */
public class SearchNotesDownloadAction extends JosmAction {

    public SearchNotesDownloadAction() {
        super("Search Notes...", "eye", "Download notes from the note search API", null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Main.debug("searching for notes");
        DownloadNotesTask task = new DownloadNotesTask();
        task.loadUrl(false, "http://api.openstreetmap.org/api/0.6/notes/search?limit=10000&closed=0&q=bounds", null);

    }

}
