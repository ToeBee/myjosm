// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.util.Comparator;

import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.notes.Note;
import org.openstreetmap.josm.data.osm.NoteData;

public class NoteSortDialog extends ExtendedDialog {

    private JRadioButton defaultSort = new JRadioButton(tr("Default (open, closed, new)"));
    private JRadioButton userSort = new JRadioButton(tr("Username"));
    private JRadioButton dateSort = new JRadioButton(tr("Created date"));
    private JRadioButton lastActionSort = new JRadioButton(tr("Last change date"));

    public NoteSortDialog(Component parent, String title, String buttonText) {
        super(parent, title, new String[] {buttonText, tr("Cancel")});
    }

    public ExtendedDialog showSortDialog(Comparator currentSortMode) {
        JLabel label = new JLabel(tr("Select note sorting method"));
        if (currentSortMode == NoteData.DEFAULT_COMPARATOR) {
            defaultSort.setSelected(true);
        } else if (currentSortMode == NoteData.DATE_COMPARATOR) {
            dateSort.setSelected(true);
        } else if (currentSortMode == NoteData.USER_COMPARATOR) {
            userSort.setSelected(true);
        } else if (currentSortMode == NoteData.LAST_ACTION_COMPARATOR) {
            lastActionSort.setSelected(true);
        } else {
            Main.warn("sort mode not recognized");
        }

        ButtonGroup bg = new ButtonGroup();
        bg.add(defaultSort);
        bg.add(userSort);
        bg.add(dateSort);
        bg.add(lastActionSort);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.add(label);
        panel.add(defaultSort);
        panel.add(userSort);
        panel.add(dateSort);
        panel.add(lastActionSort);

        setContent(panel);

        return showDialog();
    }

    public Comparator<Note> getSelectedComparator() {
        if (dateSort.isSelected()) {
            return NoteData.DATE_COMPARATOR;
        } else if (userSort.isSelected()) {
            return NoteData.USER_COMPARATOR;
        } else if (lastActionSort.isSelected()) {
            return NoteData.LAST_ACTION_COMPARATOR;
        } else {
            return NoteData.DEFAULT_COMPARATOR;
        }
    }
}
