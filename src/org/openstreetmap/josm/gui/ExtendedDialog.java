package org.openstreetmap.josm.gui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.GridBagLayout;
import java.awt.Toolkit;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.JMultilineLabel;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.ImageProvider;


public class ExtendedDialog extends JDialog {
    private int result = 0;
    private final String[] bTexts;
    
    /**
     * Sets up the dialog. The first button is always the default.
     * @param Component The parent element that will be used for position and maximum size
     * @param String The text that will be shown in the window titlebar
     * @param Component Any component that should be show above the buttons (e.g. JLabel)
     * @param String[] The labels that will be displayed on the buttons
     * @param String[] The path to the icons that will be displayed on the buttons. Path is relative to JOSM's image directory. File extensions need to be included. If a button should not have an icon pass null.
     */ 
    public ExtendedDialog(Component parent, String title, Component content, String[] buttonTexts, String[] buttonIcons) {
        super(JOptionPane.getFrameForComponent(parent), title, true);     
        bTexts = buttonTexts;        
        setupDialog(parent, title, content, buttonTexts, buttonIcons);
    }
    
    public ExtendedDialog(Component parent, String title, Component content, String[] buttonTexts) {
        this(parent, title, content, buttonTexts, null);
    }
    
    // just display a breakable message
    public ExtendedDialog(Component parent, String title, String message, String[] buttonTexts, String[] buttonIcons) {
        super(JOptionPane.getFrameForComponent(parent), title, true);
        
        JMultilineLabel lbl = new JMultilineLabel(message);
        // Make it not wider than 2/3 of the screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        lbl.setMaxWidth(Math.round(screenSize.width*2/3));
        
        bTexts = buttonTexts;        
        setupDialog(parent, title, lbl, buttonTexts, buttonIcons);
    }
    
    public ExtendedDialog(Component parent, String title, String message, String[] buttonTexts) {
        this(parent, title, message, buttonTexts, null);
    }
    
    private void setupDialog(Component parent, String title, Component content, String[] buttonTexts, String[] buttonIcons) {
        setupEscListener();
        
        JButton button;
        JPanel buttonsPanel = new JPanel(new GridBagLayout());
        
        for(int i=0; i < bTexts.length; i++) {
            Action action = new AbstractAction(bTexts[i]) {
                public void actionPerformed(ActionEvent evt) {
                    String a = evt.getActionCommand();
                    for(int i=0; i < bTexts.length; i++)
                        if(bTexts[i].equals(a)) {
                            result = i+1;
                            break;
                        }
                        
                    setVisible(false);
                }
            };
            
            button = new JButton(action);            
            if(buttonIcons != null && buttonIcons[i] != null)
                button.setIcon(ImageProvider.get(buttonIcons[i]));
            
            if(i == 0) rootPane.setDefaultButton(button);           
            buttonsPanel.add(button, GBC.std().insets(2,2,2,2));
        }
        
        JPanel cp = new JPanel(new GridBagLayout());        
        cp.add(content, GBC.eol().anchor(GBC.CENTER).insets(5,10,5,0));
        cp.add(buttonsPanel, GBC.eol().anchor(GBC.CENTER).insets(5,5,5,5));
        
        JScrollPane pane = new JScrollPane(cp);
        pane.setBorder(null);        
        setContentPane(pane);
        
        pack(); 
        
        // Try to make it not larger than the parent window or at least not larger than 2/3 of the screen
        Dimension d = getSize();
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        Dimension x = new Dimension(Math.round(screenSize.width*2/3), Math.round(screenSize.height*2/3));
        
        try {
            if(parent != null)
                x = JOptionPane.getFrameForComponent(parent).getSize();
        } catch(NullPointerException e) { }
        
        boolean limitedInWidth = d.width > x.width;
        boolean limitedInHeight = d.height > x.height;

        if(x.width  > 0 && d.width  > x.width)  d.width  = x.width;
        if(x.height > 0 && d.height > x.height) d.height = x.height;
        
        // We have a vertical scrollbar and enough space to prevent a horizontal one
        if(!limitedInWidth && limitedInHeight)
            d.width += new JScrollBar().getPreferredSize().width;
        
        setSize(d);
        setLocationRelativeTo(parent);
        setVisible(true);
    }
    
    /**
     * @return int The selected button. The count starts with 1. 
     *             A return value of 0 means the dialog has been closed otherwise.
     */    
    public int getValue() {
        return result;
    }
    
    /**
     * Makes the dialog listen to ESC keypressed
     */
    private void setupEscListener() {
        Action actionListener = new AbstractAction() { 
            public void actionPerformed(ActionEvent actionEvent) { 
                setVisible(false);
            } 
        };
        
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("ESCAPE"), "ESCAPE");
        rootPane.getActionMap().put("ESCAPE", actionListener);        
    }
}