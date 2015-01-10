// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.downloadtasks.DownloadNotesTask;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.widgets.HistoryComboBox;
import org.openstreetmap.josm.io.OsmApi;

/**
 * Action to use the Notes search API to download a set of notes
 */
public class SearchNotesDownloadAction extends JosmAction {

    private static final String HISTORY_KEY = "osm.notes.searchHistory";

    public SearchNotesDownloadAction() {
        super(tr("Search Notes..."), "eye", tr("Download notes from the note search API"), null, false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Main.debug("searching for notes");

        HistoryComboBox searchTermBox = new HistoryComboBox();
        List<String> searchHistory = new LinkedList<>(Main.pref.getCollection(HISTORY_KEY, new LinkedList<String>()));
        Collections.reverse(searchHistory);
        searchTermBox.setPossibleItems(searchHistory);

        JPanel contentPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1.0;
        gc.anchor = GridBagConstraints.FIRST_LINE_START;
        contentPanel.add(new JLabel(tr("Search the OSM API for notes containing words:")), gc);
        gc.gridy = 1;
        contentPanel.add(searchTermBox, gc);

        ExtendedDialog ed = new ExtendedDialog(Main.parent, tr("Search for notes"), new String[] {tr("Search for notes"), tr("Cancel")});
        ed.setContent(contentPanel);
        ed.showDialog();
        if(ed.getValue() != 1) {
            return;
        }

        String searchTerm = searchTermBox.getText();
        if(searchTerm == null || searchTerm.trim().isEmpty()) {
            Notification notification = new Notification(tr("You must enter a search term"));
            notification.setIcon(JOptionPane.WARNING_MESSAGE);
            notification.show();
            return;
        }

        searchTermBox.addCurrentItemToHistory();
        Main.pref.putCollection(HISTORY_KEY, searchTermBox.getHistory());

        searchTerm = searchTerm.trim();
        int noteLimit = Main.pref.getInteger("osm.notes.downloadLimit", 1000);
        int closedLimit = Main.pref.getInteger("osm.notes.daysCloased", 7);

        StringBuilder sb = new StringBuilder();
        sb.append(OsmApi.getOsmApi().getBaseUrl());
        sb.append("notes/search?limit=");
        sb.append(noteLimit);
        sb.append("&closed=");
        sb.append(closedLimit);
        sb.append("&q=");
        try {
            sb.append(URLEncoder.encode(searchTerm, "UTF-8"));
        } catch (UnsupportedEncodingException ex) {
            Main.error(ex, true); //thrown if UTF-8 isn't supported which seems unlikely.
            return;
        }

        DownloadNotesTask task = new DownloadNotesTask();
        task.loadUrl(false, sb.toString(), null);
    }

}
