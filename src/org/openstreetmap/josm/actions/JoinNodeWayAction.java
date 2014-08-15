//License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.tools.Geometry;
import org.openstreetmap.josm.tools.MultiMap;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Action allowing to join a node to a nearby way, operating on two modes:<ul>
 * <li><b>Join Node to Way</b>: Include a node into the nearest way segments. The node does not move</li>
 * <li><b>Move Node onto Way</b>: Move the node onto the nearest way segments and include it</li>
 * </ul>
 */
public class JoinNodeWayAction extends JosmAction {

    protected final boolean joinWayToNode;

    protected JoinNodeWayAction(boolean joinWayToNode, String name, String iconName, String tooltip, Shortcut shortcut, boolean registerInToolbar) {
        super(name, iconName, tooltip, shortcut, registerInToolbar);
        this.joinWayToNode = joinWayToNode;
    }

    /**
     * Constructs a Join Node to Way action.
     * @return the Join Node to Way action
     */
    public static JoinNodeWayAction createJoinNodeToWayAction() {
        JoinNodeWayAction action = new JoinNodeWayAction(false,
                tr("Join Node to Way"), "joinnodeway", tr("Include a node into the nearest way segments"),
                Shortcut.registerShortcut("tools:joinnodeway", tr("Tool: {0}", tr("Join Node to Way")), KeyEvent.VK_J, Shortcut.DIRECT), true);
        action.putValue("help", ht("/Action/JoinNodeWay"));
        return action;
    }

    /**
     * Constructs a Move Node onto Way action.
     * @return the Move Node onto Way action
     */
    public static JoinNodeWayAction createMoveNodeOntoWayAction() {
        JoinNodeWayAction action = new JoinNodeWayAction(true,
                tr("Move Node onto Way"), "movenodeontoway", tr("Move the node onto the nearest way segments and include it"),
                Shortcut.registerShortcut("tools:movenodeontoway", tr("Tool: {0}", tr("Move Node onto Way")), KeyEvent.VK_N, Shortcut.DIRECT), true);
        return action;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!isEnabled())
            return;
        Collection<Node> selectedNodes = getCurrentDataSet().getSelectedNodes();
        Collection<Command> cmds = new LinkedList<>();
        Map<Way, MultiMap<Integer, Node>> data = new HashMap<>();

        // If the user has selected some ways, only join the node to these.
        boolean restrictToSelectedWays =
                !getCurrentDataSet().getSelectedWays().isEmpty();

        // Planning phase: decide where we'll insert the nodes and put it all in "data"
        for (Node node : selectedNodes) {
            List<WaySegment> wss = Main.map.mapView.getNearestWaySegments(
                    Main.map.mapView.getPoint(node), OsmPrimitive.isSelectablePredicate);

            MultiMap<Way, Integer> insertPoints = new MultiMap<>();
            for (WaySegment ws : wss) {
                // Maybe cleaner to pass a "isSelected" predicate to getNearestWaySegments, but this is less invasive.
                if (restrictToSelectedWays && !ws.way.isSelected()) {
                    continue;
                }

                if (ws.getFirstNode() != node && ws.getSecondNode() != node) {
                    insertPoints.put(ws.way, ws.lowerIndex);
                }
            }
            for (Map.Entry<Way, Set<Integer>> entry : insertPoints.entrySet()) {
                final Way w = entry.getKey();
                final Set<Integer> insertPointsForWay = entry.getValue();
                for (int i : pruneSuccs(insertPointsForWay)) {
                    MultiMap<Integer, Node> innerMap;
                    if (!data.containsKey(w)) {
                        innerMap = new MultiMap<>();
                    } else {
                        innerMap = data.get(w);
                    }
                    innerMap.put(i, node);
                    data.put(w, innerMap);
                }
            }
        }

        // Execute phase: traverse the structure "data" and finally put the nodes into place
        for (Map.Entry<Way, MultiMap<Integer, Node>> entry : data.entrySet()) {
            final Way w = entry.getKey();
            final MultiMap<Integer, Node> innerEntry = entry.getValue();

            List<Integer> segmentIndexes = new LinkedList<>();
            segmentIndexes.addAll(innerEntry.keySet());
            Collections.sort(segmentIndexes, Collections.reverseOrder());

            List<Node> wayNodes = w.getNodes();
            for (Integer segmentIndex : segmentIndexes) {
                final Set<Node> nodesInSegment = innerEntry.get(segmentIndex);
                if (joinWayToNode) {
                    for (Node node : nodesInSegment) {
                        EastNorth newPosition = Geometry.closestPointToSegment(w.getNode(segmentIndex).getEastNorth(),
                                                                            w.getNode(segmentIndex+1).getEastNorth(),
                                                                            node.getEastNorth());
                        cmds.add(new MoveCommand(node, Projections.inverseProject(newPosition)));
                    }
                }
                List<Node> nodesToAdd = new LinkedList<>();
                nodesToAdd.addAll(nodesInSegment);
                Collections.sort(nodesToAdd, new NodeDistanceToRefNodeComparator(w.getNode(segmentIndex), w.getNode(segmentIndex+1), !joinWayToNode));
                wayNodes.addAll(segmentIndex + 1, nodesToAdd);
            }
            Way wnew = new Way(w);
            wnew.setNodes(wayNodes);
            cmds.add(new ChangeCommand(w, wnew));
        }

        if (cmds.isEmpty()) return;
        Main.main.undoRedo.add(new SequenceCommand(getValue(NAME).toString(), cmds));
        Main.map.repaint();
    }

    private static SortedSet<Integer> pruneSuccs(Collection<Integer> is) {
        SortedSet<Integer> is2 = new TreeSet<>();
        for (int i : is) {
            if (!is2.contains(i - 1) && !is2.contains(i + 1)) {
                is2.add(i);
            }
        }
        return is2;
    }

    /**
     * Sorts collinear nodes by their distance to a common reference node.
     */
    private static class NodeDistanceToRefNodeComparator implements Comparator<Node> {
        private final EastNorth refPoint;
        private EastNorth refPoint2;
        private final boolean projectToSegment;
        NodeDistanceToRefNodeComparator(Node referenceNode, Node referenceNode2, boolean projectFirst) {
            refPoint = referenceNode.getEastNorth();
            refPoint2 = referenceNode2.getEastNorth();
            projectToSegment = projectFirst;
        }
        @Override
        public int compare(Node first, Node second) {
            EastNorth firstPosition = first.getEastNorth();
            EastNorth secondPosition = second.getEastNorth();

            if (projectToSegment) {
                firstPosition = Geometry.closestPointToSegment(refPoint, refPoint2, firstPosition);
                secondPosition = Geometry.closestPointToSegment(refPoint, refPoint2, secondPosition);
            }

            double distanceFirst = firstPosition.distance(refPoint);
            double distanceSecond = secondPosition.distance(refPoint);
            double difference =  distanceFirst - distanceSecond;

            if (difference > 0.0) return 1;
            if (difference < 0.0) return -1;
            return 0;
        }
    }

    @Override
    protected void updateEnabledState() {
        if (getCurrentDataSet() == null) {
            setEnabled(false);
        } else {
            updateEnabledState(getCurrentDataSet().getSelected());
        }
    }

    @Override
    protected void updateEnabledState(Collection<? extends OsmPrimitive> selection) {
        setEnabled(selection != null && !selection.isEmpty());
    }
}
