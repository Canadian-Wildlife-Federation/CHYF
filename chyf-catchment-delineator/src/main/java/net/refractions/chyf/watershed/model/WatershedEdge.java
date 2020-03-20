/*******************************************************************************
 * Copyright 2020 Government of Canada
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *******************************************************************************/

package net.refractions.chyf.watershed.model;

import org.locationtech.jts.triangulate.quadedge.QuadEdge;

/**
 * Methods for processing QuadEdges which are the edges of {@link WatershedTriangle}s.
 * 
 * @author Martin Davis
 */
public class WatershedEdge {
    public static int getRegionID(QuadEdge e) {
        Region region = getRegion(e);
        if (region != null)
            return region.getID();
        return Region.NULL_ID;
    }

    public static Region getRegion(QuadEdge e) {
        WatershedTriangle tri = (WatershedTriangle) e.getData();
        if (tri == null)
            return null;
        return tri.getRegion();
    }

    public static double getMinimumHeight(QuadEdge e) {
        return Math.min(((WatershedVertex) e.orig()).getHeight(), ((WatershedVertex) e.dest())
            .getHeight());
    }

    public static double getSlope(QuadEdge e) {
        double dx = e.orig().getCoordinate().distance(e.dest().getCoordinate());
        double dy = Math.abs(((WatershedVertex) e.orig()).getHeight()
                - ((WatershedVertex) e.dest()).getHeight());
        return Math.atan2(dy, dx);
    }

    public static boolean isAdjacentToWater(QuadEdge qe) {
        WatershedTriangle tri = (WatershedTriangle) qe.getData();
        if (tri == null)
            return false;

        // if (tri.isWater())
        // System.out.println(tri);

        return tri.isWater();
    }

    /**
     * Tests whether the given quadedge lies on the boundary between two different {@link Region}s.
     * This is the case if the regions on either side of the edge are different. If this is the
     * case, then this edge will appear as a segment in a boundary edge linestring.
     * 
     * @param qe a quadedge
     * @return true if the quadedge lies on the boundary between two different regions
     */
    public static boolean isOnBoundary(QuadEdge qe) {
        Region rL = getRegion(qe);
        Region rR = getRegion(qe.sym());
        boolean isOnBoundary = (rL != rR);
        return isOnBoundary;
    }

    /**
     * Tests whether both regions for this edge are valid (i.e non-null)
     * 
     * @param qe the quadedge to test
     * @return true if both neighbour regions are valid
     */
    public static boolean isValidRegionEdge(QuadEdge qe) {
        Region rL = getRegion(qe);
        Region rR = getRegion(qe.sym());
        return isValidRegion(rL) || isValidRegion(rR);
    }

    /**
     * Tests whether a region is non-null and corresponds to an actual drainage area.
     * 
     * @param r a Region
     * @return
     */
    private static boolean isValidRegion(Region r) {
        if (r == null)
            return false;
        return r.getID() > 0;
    }

}
