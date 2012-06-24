// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.conflict.pair.nodes;

import java.util.List;

import javax.swing.JScrollPane;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.conflict.Conflict;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.conflict.pair.IConflictResolver;
import org.openstreetmap.josm.gui.conflict.pair.ListMerger;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/**
 * A UI component for resolving conflicts in the node lists of two {@link Way}s.
 *
 */
public class NodeListMerger extends ListMerger<Node> implements IConflictResolver {
    public NodeListMerger() {
        super(new NodeListMergeModel());
    }

    @Override
    protected JScrollPane buildMyElementsTable() {
        myEntriesTable  = new NodeListTable(
                "table.mynodes",
                model.getMyTableModel(),
                model.getMySelectionModel()
        );
        return embeddInScrollPane(myEntriesTable);
    }

    @Override
    protected JScrollPane buildMergedElementsTable() {
        mergedEntriesTable  = new NodeListTable(
                "table.mergednodes",
                model.getMergedTableModel(),
                model.getMergedSelectionModel()
        );
        return embeddInScrollPane(mergedEntriesTable);
    }

    @Override
    protected JScrollPane buildTheirElementsTable() {
        theirEntriesTable  = new NodeListTable(
                "table.theirnodes",
                model.getTheirTableModel(),
                model.getTheirSelectionModel()
        );
        return embeddInScrollPane(theirEntriesTable);
    }

    public void populate(Conflict<? extends OsmPrimitive> conflict) {
        Way myWay = (Way)conflict.getMy();
        Way theirWay = (Way)conflict.getTheir();
        ((NodeListMergeModel)model).populate(myWay, theirWay);
        myEntriesTable.setLayer(findLayerFor(myWay));
        theirEntriesTable.setLayer(findLayerFor(theirWay));
    }
    
    protected OsmDataLayer findLayerFor(Way w) {
        List<OsmDataLayer> layers = Main.map.mapView.getLayersOfType(OsmDataLayer.class);
        // Find layer with same dataset
        for (OsmDataLayer layer : layers) {
            if (layer.data == w.getDataSet()) {
                return layer;
            }
        }
        // Conflict after merging layers: a dataset could be no more in any layer, try to find another layer with same primitive
        for (OsmDataLayer layer : layers) {
            for (Way way : layer.data.getWays()) {
                if (way.getPrimitiveId().equals(w.getPrimitiveId())) {
                    return layer;
                }
            }
        }
        return null;
    }

    public void deletePrimitive(boolean deleted) {
        if (deleted) {
            model.setFrozen(true);
            model.clearMerged();
        } else {
            model.setFrozen(false);
        }
    }
}
