// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.projection;

import java.util.Collection;
import java.util.HashMap;

import org.openstreetmap.josm.gui.preferences.projection.ProjectionChoice;
import org.openstreetmap.josm.gui.preferences.projection.ProjectionPreference;

public class ProjectionInfo {
    private static HashMap<String, ProjectionChoice> allCodesPC;
    private static HashMap<String, Projection> allCodes;

    static {
        allCodes = new HashMap<String, Projection>();
        for (ProjectionChoice pc : ProjectionPreference.getProjectionChoices()) {
            for (String code : pc.allCodes()) {
                allCodesPC.put(code, pc);
            }
        }
    }

    public static Projection getProjectionByCode(String code) {
        Projection p = allCodes.get(code);
        if (p != null) return p;
        ProjectionChoice pc = allCodesPC.get(code);
        if (pc == null) return null;
        Collection<String> pref = pc.getPreferencesFromCode(code);
        pc.setPreferences(pref);
        p = pc.getProjection();
        allCodes.put(code, p);
        return p;
    }
}
