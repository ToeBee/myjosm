// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import org.openstreetmap.josm.tools.ImageProvider;

public class NoteInputDialog extends ExtendedDialog {

    private JTextArea textArea = new JTextArea();

    public NoteInputDialog(Component parent, String title, String buttonText) {
        super(parent, title, new String[] {buttonText, tr("Cancel")});
    }

    public void showNoteDialog(String message, Icon icon) {
        JLabel label = new JLabel(message);
        textArea.setRows(6);
        textArea.setColumns(30);
        textArea.setLineWrap(true);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setAlignmentX(Component.LEFT_ALIGNMENT); //without this the label gets pushed to the right

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.add(label);
        contentPanel.add(scrollPane);
        setContent(contentPanel, false);
        setButtonIcons(new Icon[] {icon, ImageProvider.get("cancel.png")});

        super.showDialog();
    }

    public String getInputText() {
        return textArea.getText();
    }

}
