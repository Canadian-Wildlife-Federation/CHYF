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

package net.refractions.chyf.watershed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeTriangle;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.locationtech.jts.util.Debug;

import net.refractions.chyf.util.LineSegmentUtil;
import net.refractions.chyf.watershed.model.Region;
import net.refractions.chyf.watershed.model.WatershedEdge;
import net.refractions.chyf.watershed.model.WatershedTriangle;
import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Merges regions which are pits into adjacent regions. For any given pit region the merged region
 * may also be a pit region, but eventually all pit regions will be merged into non-pit regions. The
 * merged region is chosen to be the one with the lowest-elevation midpoint of all border edges.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class PitRegionMerger {
    public static Coordinate findEdgeMidpoint(QuadEdge e) {
        WatershedVertex v0 = (WatershedVertex) e.orig();
        WatershedVertex v1 = (WatershedVertex) e.dest();
        Coordinate p0 = v0.getCoordinateWithHeight();
        Coordinate p1 = v1.getCoordinateWithHeight();
        Coordinate midPt = LineSegmentUtil.midPoint(p0, p1);

        double hgt = Vertex.interpolateZ(midPt, p0, p1);
        midPt.setZ(hgt);
        return midPt;
    }

    private Collection<Region> pitRegions;
    private List<Coordinate> midPoints = new ArrayList<Coordinate>();

    public PitRegionMerger(Collection<Region> pitRegions) {
        this.pitRegions = pitRegions;
    }

    /**
     * Gets a list of the midpoints of all the segments on the borders of pit regions. For each pit
     * region, one of these will determine the merge region.
     * 
     * @return
     */
    public List<Coordinate> getMidPoints() {
        return midPoints;
    }

    public void merge() {
        for (Region region : pitRegions) {
            Region spillRegion = findSpillRegion(region);
            if (spillRegion != null) {
                spillRegion.merge(region);
            } else {
                System.out.println("Can't find spill region for pit region " + region.getID());
            }
        }
    }

    /**
     * Finds the spill region for a the edges around a pit. The spill region is the region adjacent
     * to the spill edge. The spill edge is the edge with the lowest minimum elevation and slope.
     * 
     * @param region the region of the pit
     * @return the spill region
     */
    private Region findSpillRegion(Region pitRegion) {
        Region spillRegion = null;
        QuadEdge spillEdge = null;
        double lowestElev = Double.MAX_VALUE;
        double lowestSlope = Double.MAX_VALUE;

        for (QuadEdge e : findBorderEdges(pitRegion)) {
            if (Debug.isDebugging()) {
                Coordinate midPt = findEdgeMidpoint(e);
                midPoints.add(midPt);
            }

            double elev = WatershedEdge.getMinimumHeight(e);
            double slope = WatershedEdge.getSlope(e);
            if (elev < lowestElev || elev == lowestElev && slope < lowestSlope) {
                lowestElev = elev;
                lowestSlope = slope;
                spillEdge = e;
            }

        }
        spillRegion = findRegionNotEqual(spillEdge, pitRegion);
        return spillRegion;
    }

    public static Region findRegionNotEqual(QuadEdge e, Region testRegion) {
        Region r0 = WatershedEdge.getRegion(e);
        if (r0 != testRegion)
            return r0;
        return WatershedEdge.getRegion(e.sym());
    }

    private List<QuadEdge> findBorderEdges(Region region) {
        List<QuadEdge> borderEdges = new ArrayList<QuadEdge>();
        List<WatershedTriangle> tris = region.getMembers();
        // not sure why this should ever happen
        if (tris == null)
            return borderEdges;

        for (WatershedTriangle tri : tris) {
            if (!tri.isLive())
                continue;

            QuadEdgeTriangle neighbTri[] = tri.getNeighbours();
            for (int j = 0; j < 3; j++) {
                WatershedTriangle nTri = (WatershedTriangle) neighbTri[j];

                if (nTri == null)
                    System.out.println(nTri);

                if (nTri != null && nTri.getRegion() != region) {
                    borderEdges.add(tri.getEdge(j));
                }
            }
        }
        return borderEdges;
    }
}