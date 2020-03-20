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
import java.util.Collections;
import java.util.List;

import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.triangulate.quadedge.EdgeConnectedTriangleTraversal;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeTriangle;
import org.locationtech.jts.triangulate.quadedge.TraversalVisitor;

import net.refractions.chyf.watershed.model.Region;
import net.refractions.chyf.watershed.model.WatershedTriangle;

/**
 * Processes all triangles identified as being disconnected from their primary region, and merges
 * them into an adjacent region. Triangles are merged in order of their distance to their original
 * region constraint. This ensures that the triangles at the "mouth" of a disconnected area are used
 * to choose the region the area is assigned to. All connected triangles in a disconnected area are
 * merged into the same adjacent region.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class NullRegionMerger {
    private Collection<WatershedTriangle> triangles;

    public NullRegionMerger(Collection<WatershedTriangle> triangles) {
        this.triangles = triangles;
    }

    public void merge() {
        List<WatershedTriangle> discTris = findNullRegionTriangles();
        // this isn't really necessary
        Collections.sort(discTris, new WatershedTriangle.TrickleDistanceComparator());

        for(WatershedTriangle tri : discTris) {
            // only interested in disconnected (null-region) triangles
            if (tri.hasRegion())
                continue;

            Region mergeRegion = findMaxEdgeLenRegion(tri);
            if (mergeRegion == null)
                continue;
            mergeRegion.add(tri);
            NullRegionAssigner.compute(tri, mergeRegion);
        }
    }

    private List<WatershedTriangle> findNullRegionTriangles() {
        List<WatershedTriangle> nullRegionTris = new ArrayList<WatershedTriangle>();
        for(WatershedTriangle tri : triangles) {
            if (!tri.hasRegion()) {
                nullRegionTris.add(tri);
            }
        }
        return nullRegionTris;
    }

    private LineSegment seg = new LineSegment();

    /**
     * Given a disconnected triangle, chooses the best region from its neighbours. The triangle is
     * assumed to be on the edge of a disconnected group, so that it has at least one neighbour with
     * an assigned region.
     * 
     * @param tri a triangle on the edge of a disconnected group
     * @return the Region assigned
     */
    private Region findMaxEdgeLenRegion(WatershedTriangle tri) {
        // System.out.println(tri);
        // if (tri.contains(new Coordinate(1254399.329554995, 1630773.9076582645)))
        // System.out.println(tri);

        double maxEdgeLen = 0.0;
        Region mergeRegion = null;
        for (int i = 0; i < 3; i++) {
            tri.getEdgeSegment(i, seg);
            double edgeLen = seg.getLength();
            WatershedTriangle neighTri = (WatershedTriangle) tri.getAdjacentTriangleAcrossEdge(i);

            // MD - why would this happen? Is it a problem?
            if (neighTri == null)
                continue;

            Region region = neighTri.getRegion();
            if (region == null)
                continue;
            if (edgeLen > maxEdgeLen) {
                maxEdgeLen = edgeLen;
                mergeRegion = region;
            }
        }
        return mergeRegion;
    }
}

/**
 * An algorithm class to add all disconnected (null-region) triangles connected to a given triangle
 * to a given region.
 * 
 * @author Martin Davis
 * @version 1.0
 */
class NullRegionAssigner implements TraversalVisitor {
    public static void compute(WatershedTriangle startTri, Region region) {
        EdgeConnectedTriangleTraversal traversal = new EdgeConnectedTriangleTraversal();
        traversal.init(startTri);
        traversal.visitAll(new NullRegionAssigner(region));
    }

    private Region region;

    public NullRegionAssigner(Region region) {
        this.region = region;
    }

    public boolean visit(QuadEdgeTriangle currTri, int edgeIndex, QuadEdgeTriangle neighTri) {
        WatershedTriangle tri = (WatershedTriangle) neighTri;
        // stop searching when we hit the edge of the null-region set
        if (tri.hasRegion())
            return false;

        region.add(tri);
        return true;
    }
}
