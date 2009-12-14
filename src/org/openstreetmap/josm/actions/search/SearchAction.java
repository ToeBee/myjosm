// License: GPL. Copyright 2007 by Immanuel Scholz and others
package org.openstreetmap.josm.actions.search;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Filter;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.widgets.HistoryComboBox;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Shortcut;

public class SearchAction extends JosmAction{

    public static final int DEFAULT_SEARCH_HISTORY_SIZE = 10;

    public static enum SearchMode {
        replace, add, remove, in_selection
    }

    public static final LinkedList<SearchSetting> searchHistory = new LinkedList<SearchSetting>();

    private static SearchSetting lastSearch = null;

    public SearchAction() {
        super(tr("Search..."), "dialogs/search", tr("Search for objects."),
                Shortcut.registerShortcut("system:find", tr("Search..."), KeyEvent.VK_F, Shortcut.GROUP_HOTKEY), true);
        putValue("help", ht("/Action/Search"));
    }

    public void actionPerformed(ActionEvent e) {
        if (!isEnabled())
            return;
        SearchSetting s = lastSearch;
        if (s == null) {
            s = new SearchSetting("", SearchMode.replace, false /* case insensitive */, false /* no regexp */);
        }
        SearchSetting se = showSearchDialog(s);
        if(se != null) {
            searchWithHistory(se);
        }
    }

    public static List<String> getSearchExpressionHistory() {
        ArrayList<String> ret = new ArrayList<String>(searchHistory.size());
        for (SearchSetting ss: searchHistory) {
            ret.add(ss.text);
        }
        return ret;
    }

    public static SearchSetting showSearchDialog(SearchSetting initialValues) {

        // -- prepare the combo box with the search expressions
        //
        JLabel label = new JLabel( initialValues instanceof Filter ? tr("Please enter a filter string.") : tr("Please enter a search string."));
        final HistoryComboBox hcbSearchString = new HistoryComboBox();
        hcbSearchString.setText(initialValues.text);
        hcbSearchString.getEditor().selectAll();
        hcbSearchString.getEditor().getEditorComponent().requestFocusInWindow();
        hcbSearchString.setToolTipText(tr("Enter the search expression"));
        // we have to reverse the history, because ComboBoxHistory will reverse it again
        // in addElement()
        //
        List<String> searchExpressionHistory = getSearchExpressionHistory();
        Collections.reverse(searchExpressionHistory);
        hcbSearchString.setPossibleItems(searchExpressionHistory);

        JRadioButton replace = new JRadioButton(tr("replace selection"), initialValues.mode == SearchMode.replace);
        JRadioButton add = new JRadioButton(tr("add to selection"), initialValues.mode == SearchMode.add);
        JRadioButton remove = new JRadioButton(tr("remove from selection"), initialValues.mode == SearchMode.remove);
        JRadioButton in_selection = new JRadioButton(tr("find in selection"), initialValues.mode == SearchMode.in_selection);
        ButtonGroup bg = new ButtonGroup();
        bg.add(replace);
        bg.add(add);
        bg.add(remove);
        bg.add(in_selection);

        JCheckBox caseSensitive = new JCheckBox(tr("case sensitive"), initialValues.caseSensitive);
        JCheckBox regexSearch   = new JCheckBox(tr("regular expression"), initialValues.regexSearch);

        JPanel left = new JPanel(new GridBagLayout());
        left.add(label, GBC.eop());
        left.add(hcbSearchString, GBC.eop().fill(GBC.HORIZONTAL));
        left.add(replace, GBC.eol());
        left.add(add, GBC.eol());
        left.add(remove, GBC.eol());
        left.add(in_selection, GBC.eop());
        left.add(caseSensitive, GBC.eol());
        left.add(regexSearch, GBC.eol());

        JPanel right = new JPanel();
        JLabel description =
            new JLabel("<html><ul>"
                    + "<li>"+tr("<b>Baker Street</b> - 'Baker' and 'Street' in any key or name.")+"</li>"
                    + "<li>"+tr("<b>\"Baker Street\"</b> - 'Baker Street' in any key or name.")+"</li>"
                    + "<li>"+tr("<b>name:Bak</b> - 'Bak' anywhere in the name.")+"</li>"
                    + "<li>"+tr("<b>type=route</b> - key 'type' with value exactly 'route'.") + "</li>"
                    + "<li>"+tr("<b>type=*</b> - key 'type' with any value. Try also <b>*=value</b>, <b>type=</b>, <b>*=*</b>, <b>*=</b>") + "</li>"
                    + "<li>"+tr("<b>-name:Bak</b> - not 'Bak' in the name.")+"</li>"
                    + "<li>"+tr("<b>foot:</b> - key=foot set to any value.")+"</li>"
                    + "<li>"+tr("<u>Special targets:</u>")+"</li>"
                    + "<li>"+tr("<b>type:</b> - type of the object (<b>node</b>, <b>way</b>, <b>relation</b>)")+"</li>"
                    + "<li>"+tr("<b>user:</b>... - all objects changed by user")+"</li>"
                    + "<li>"+tr("<b>user:anonymous</b> - all objects changed by anonymous users")+"</li>"
                    + "<li>"+tr("<b>id:</b>... - object with given ID (0 for new objects)")+"</li>"
                    + "<li>"+tr("<b>changeset:</b>... - object with given changeset id (0 objects without assigned changeset)")+"</li>"
                    + "<li>"+tr("<b>nodes:</b>... - object with given number of nodes (nodes:count or nodes:min-max)")+"</li>"
                    + "<li>"+tr("<b>tags:</b>... - object with given number of tags (tags:count or tags:min-max)")+"</li>"
                    + "<li>"+tr("<b>timestamp:</b>... -  objects with this timestamp (<b>2009-11-12T14:51:09Z</b>, <b>2009-11-12</b> or <b>T14:51</b> ...)")+"</li>"
                    + "<li>"+tr("<b>modified</b> - all changed objects")+"</li>"
                    + "<li>"+tr("<b>selected</b> - all selected objects")+"</li>"
                    + "<li>"+tr("<b>incomplete</b> - all incomplete objects")+"</li>"
                    + "<li>"+tr("<b>untagged</b> - all untagged objects")+"</li>"
                    + "<li>"+tr("<b>child <i>expr</i></b> - all children of objects matching the expression")+"</li>"
                    + "<li>"+tr("<b>parent <i>expr</i></b> - all parents of objects matching the expression")+"</li>"
                    + "<li>"+tr("Use <b>|</b> or <b>OR</b> to combine with logical or")+"</li>"
                    + "<li>"+tr("Use <b>\"</b> to quote operators (e.g. if key contains :)")+"</li>"
                    + "<li>"+tr("Use <b>(</b> and <b>)</b> to group expressions")+"</li>"
                    + "</ul></html>");
        description.setFont(description.getFont().deriveFont(Font.PLAIN));
        right.add(description);

        final JPanel p = new JPanel();
        p.add(left);
        p.add(right);
        ExtendedDialog dialog = new ExtendedDialog(
                Main.parent,
                initialValues instanceof Filter ? tr("Filter") : tr("Search"),
                        new String[] {
                    initialValues instanceof Filter ? tr("Submit filter") : tr("Start Search"),
                            tr("Cancel")}
        );
        dialog.setButtonIcons(new String[] {"dialogs/search.png", "cancel.png"});
        dialog.configureContextsensitiveHelp("/Action/Search", true /* show help button */);
        dialog.setContent(p);
        dialog.showDialog();
        int result = dialog.getValue();

        if(result != 1) return null;

        // User pressed OK - let's perform the search
        SearchMode mode = replace.isSelected() ? SearchAction.SearchMode.replace
                : (add.isSelected() ? SearchAction.SearchMode.add
                        : (remove.isSelected() ? SearchAction.SearchMode.remove : SearchAction.SearchMode.in_selection));
        initialValues.text = hcbSearchString.getText();
        initialValues.mode = mode;
        initialValues.caseSensitive = caseSensitive.isSelected();
        initialValues.regexSearch = regexSearch.isSelected();
        return initialValues;
    }

    /**
     * Adds the search specified by the settings in <code>s</code> to the
     * search history and performs the search.
     *
     * @param s
     */
    public static void searchWithHistory(SearchSetting s) {
        if(searchHistory.isEmpty() || !s.equals(searchHistory.getFirst())) {
            searchHistory.addFirst(new SearchSetting(s));
        }
        int maxsize = Main.pref.getInteger("search.history-size", DEFAULT_SEARCH_HISTORY_SIZE);
        while (searchHistory.size() > maxsize) {
            searchHistory.removeLast();
        }
        lastSearch = new SearchSetting(s);
        search(s);
    }

    public static void searchWithoutHistory(SearchSetting s) {
        lastSearch = new SearchSetting(s);
        search(s);
    }

    public interface Function{
        public Boolean isSomething(OsmPrimitive o);
    }

    public static Integer getSelection(SearchSetting s, Collection<OsmPrimitive> sel, Function f) {
        Integer foundMatches = 0;
        try {
            String searchText = s.text;
            if(s instanceof Filter){
                searchText = "(" + s.text + ")" + (((Filter)s).applyForChildren ? ("| child (" + s.text + ")"): "");
                searchText = (((Filter)s).inverted ? "-" : "") + "(" +  searchText + ")";
            }
            /*System.out.println(searchText);*/
            SearchCompiler.Match matcher = SearchCompiler.compile(searchText, s.caseSensitive, s.regexSearch);
            foundMatches = 0;
            for (OsmPrimitive osm : Main.main.getCurrentDataSet().allNonDeletedCompletePrimitives()) {
                if (s.mode == SearchMode.replace) {
                    if (matcher.match(osm)) {
                        sel.add(osm);
                        ++foundMatches;
                    } else {
                        sel.remove(osm);
                    }
                } else if (s.mode == SearchMode.add && !f.isSomething(osm) && matcher.match(osm)) {
                    sel.add(osm);
                    ++foundMatches;
                } else if (s.mode == SearchMode.remove && f.isSomething(osm) && matcher.match(osm)) {
                    sel.remove(osm);
                    ++foundMatches;
                } else if (s.mode == SearchMode.in_selection &&  f.isSomething(osm)&& !matcher.match(osm)) {
                    sel.remove(osm);
                    ++foundMatches;
                }
            }
        } catch (SearchCompiler.ParseError e) {
            JOptionPane.showMessageDialog(
                    Main.parent,
                    e.getMessage(),
                    tr("Error"),
                    JOptionPane.ERROR_MESSAGE

            );
        }
        return foundMatches;
    }

    public static void search(String search, SearchMode mode, boolean caseSensitive, boolean regexSearch) {
        search(new SearchSetting(search, mode, caseSensitive, regexSearch));
    }

    public static void search(SearchSetting s) {
        // FIXME: This is confusing. The GUI says nothing about loading primitives from an URL. We'd like to *search*
        // for URLs in the current data set.
        // Disabling until a better solution is in place
        //
        //        if (search.startsWith("http://") || search.startsWith("ftp://") || search.startsWith("https://")
        //                || search.startsWith("file:/")) {
        //            SelectionWebsiteLoader loader = new SelectionWebsiteLoader(search, mode);
        //            if (loader.url != null && loader.url.getHost() != null) {
        //                Main.worker.execute(loader);
        //                return;
        //            }
        //        }

        final DataSet ds = Main.main.getCurrentDataSet();
        Collection<OsmPrimitive> sel = ds.getSelected();
        int foundMatches = getSelection(s, sel, new Function(){
            public Boolean isSomething(OsmPrimitive o){
                return ds.isSelected(o);
            }
        });
        ds.setSelected(sel);
        if (foundMatches == 0) {
            String msg = null;
            if (s.mode == SearchMode.replace) {
                msg = tr("No match found for ''{0}''", s.text);
            } else if (s.mode == SearchMode.add) {
                msg = tr("Nothing added to selection by searching for ''{0}''", s.text);
            } else if (s.mode == SearchMode.remove) {
                msg = tr("Nothing removed from selection by searching for ''{0}''", s.text);
            } else if (s.mode == SearchMode.in_selection) {
                msg = tr("Nothing found in selection by searching for ''{0}''", s.text);
            }
            Main.map.statusLine.setHelpText(msg);
            JOptionPane.showMessageDialog(
                    Main.parent,
                    msg,
                    tr("Warning"),
                    JOptionPane.WARNING_MESSAGE
            );
        } else {
            Main.map.statusLine.setHelpText(tr("Found {0} matches", foundMatches));
        }
    }

    public static class SearchSetting {
        public String text;
        public SearchMode mode;
        public boolean caseSensitive;
        public boolean regexSearch;

        public SearchSetting(String text, SearchMode mode, boolean caseSensitive, boolean regexSearch) {
            super();
            this.caseSensitive = caseSensitive;
            this.regexSearch = regexSearch;
            this.mode = mode;
            this.text = text;
        }

        public SearchSetting(SearchSetting original) {
            super();
            this.caseSensitive = original.caseSensitive;
            this.regexSearch = original.regexSearch;
            this.mode = original.mode;
            this.text = original.text;
        }

        @Override
        public String toString() {
            String cs = caseSensitive ? tr("CS") : tr("CI");
            String rx = regexSearch ? (", " + tr("RX")) : "";
            return "\"" + text + "\" (" + cs + rx + ", " + mode + ")";
        }

        @Override
        public boolean equals(Object other) {
            if(!(other instanceof SearchSetting))
                return false;
            SearchSetting o = (SearchSetting) other;
            return (o.caseSensitive == this.caseSensitive
                    && o.regexSearch == this.regexSearch
                    && o.mode.equals(this.mode)
                    && o.text.equals(this.text));
        }
    }

    /**
     * Refreshes the enabled state
     *
     */
    @Override
    protected void updateEnabledState() {
        setEnabled(getEditLayer() != null);
    }
}
