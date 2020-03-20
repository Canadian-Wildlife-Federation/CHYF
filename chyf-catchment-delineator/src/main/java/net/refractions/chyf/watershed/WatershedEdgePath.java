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
import java.util.Iterator;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeUtil;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.WatershedBoundaryEdge;
import net.refractions.chyf.watershed.model.WatershedEdge;
import net.refractions.chyf.watershed.model.WatershedTriangle;
import net.refractions.chyf.watershed.model.WatershedVertex;
import net.refractions.chyf.watershed.model.WatershedVertexUtil;

/**
 * Builds a {@link WatershedBoundaryEdge} from a path through the subdivision. The path starts at a
 * given
 * 
 * @link QuadEdge}, and continues through adjacent edges which are on a watershed boundary (i.e.
 *       have different regions on each side.) The path terminates when either a (previously marked)
 *       watershed boundary node is encountered, or when the path loops back on itself. The
 *       extracted path is normalized to make the start coordinate no greater than the end
 *       coordinate. This helps to make the results more deterministic, which in turn could reduce
 *       the number of discrepancies between blocks.
 * @author Martin Davis
 * @version 1.0
 */
public class WatershedEdgePath {
    /**
     * Extracts the list of coordinates of the vertices along a list of contiguous {@link QuadEdge}s
     * 
     * @param edgeQuads a list of contiguous quadedges to extract from
     * @return the list of coordinates along the edges
     */
    private static Coordinate[] extractQuadEdgeCoordinateSequence(List<QuadEdge> edgeQuads) {
        Coordinate[] pts = new Coordinate[edgeQuads.size() + 1];
        int i = 0;
        QuadEdge qe = null;
        for (Iterator<QuadEdge> it = edgeQuads.iterator(); it.hasNext();) {
            qe = (QuadEdge) it.next();
            pts[i++] = qe.orig().getCoordinate();
        }
        pts[i] = qe.dest().getCoordinate();
        return pts;
    }

    private GeometryFactory geomFactory;
    private List<QuadEdge> edgeQuads = null;
    private boolean isHydroNodeFirst;
    private boolean isHydroNodeLast;

    /**
     * Creates a new path for the WatershedBoundaryEdge starting with the given QuadEdge.
     * 
     * @param startQE the quadedge to start at
     * @param geomFactory the geometry factory to use
     */
    public WatershedEdgePath(QuadEdge startQE, GeometryFactory geomFactory) {
        this.geomFactory = geomFactory;
        edgeQuads = computeQuads(startQE);
        isHydroNodeFirst = isHydroNode(((QuadEdge) edgeQuads.get(0)), true);
        isHydroNodeLast = isHydroNode(((QuadEdge) edgeQuads.get(edgeQuads.size() - 1)), false);
    }

    public QuadEdge getFirstEdge() {
        return (QuadEdge) edgeQuads.get(0);
    }

    public QuadEdge getLastEdge() {
        return (QuadEdge) edgeQuads.get(edgeQuads.size() - 1);
    }

    public boolean isInWater() {
        QuadEdge e0 = getFirstEdge();
        if (WatershedEdge.isAdjacentToWater(e0))
            return true;
        QuadEdge en = getLastEdge();
        if (WatershedEdge.isAdjacentToWater(en))
            return true;
        return false;
    }

    private List<QuadEdge> computeQuads(QuadEdge startQE) {
        List<QuadEdge> rawEdgeQuads = extractBoundaryEdgeQuads(startQE);
        return normalize(rawEdgeQuads);
    }

    /**
     * Normalizes a list of quads for an edge so that the start Coordinate is the same or lower than
     * the end Coordinate.
     * 
     * @param eQuads
     * @return the normalized list of edge quads
     */
    private List<QuadEdge> normalize(List<QuadEdge> eQuads) {
        int n = eQuads.size();
        Coordinate p0 = ((QuadEdge) eQuads.get(0)).orig().getCoordinate();
        Coordinate pn = ((QuadEdge) eQuads.get(n - 1)).dest().getCoordinate();
        // check if already normalized
        if (p0.compareTo(pn) <= 0)
            return eQuads;

        // flip the list and all quadedges in it

        List<QuadEdge> revQuads = new ArrayList<QuadEdge>();
        for (int i = n - 1; i >= 0; i--) {
            revQuads.add(((QuadEdge) eQuads.get(i)).sym());
        }
        return revQuads;
    }

    /**
     * Extracts the WatershedBoundaryEdge for this path.
     * 
     * @return a WatershedBoundaryEdge
     */
    public WatershedBoundaryEdge extractBoundaryEdge() {
        int[] regionID = getSideInfo((QuadEdge) edgeQuads.get(0));

        /**
         * Don't smooth if the path is simply two (or more) edges of a triangle. This prevents
         * excessive flattening of the edge.
         */
        boolean isSmoothable = !isWedge(edgeQuads);
        // boolean isSmoothable = ! isBoundingConstrainedTriangle(edgeQuads);
        // create the actual boundary edge linework and object
        Coordinate[] pts = extractQuadEdgeCoordinateSequence(edgeQuads);
        LineString line = geomFactory.createLineString(pts);

        boolean[] isVertexRespected = findRespectedVertices(edgeQuads);

        WatershedBoundaryEdge wbe = new WatershedBoundaryEdge(
            line,
            isVertexRespected,
            regionID,
            isHydroNodeFirst,
            isHydroNodeLast);
        wbe.setSmoothable(isSmoothable);
        return wbe;
    }

    /**
     * Tests whether the orig or dest of a quadedge is a hydro node
     * 
     * @param qe
     * @param orig
     * @return
     */
    private static boolean isHydroNode(QuadEdge qe, boolean orig) {
        Vertex v;
        if (orig)
            v = qe.orig();
        else
            v = qe.dest();
        return ((WatershedVertex) v).isNode();
    }

    /**
     * Extracts all quad edges in path. Terminates when a boundary node is encountered, or when path
     * loops back on itself.
     * 
     * @param startQE the quadedge to start at
     * @return a list of the QuadEdges in the path
     */
    private List<QuadEdge> extractBoundaryEdgeQuads(QuadEdge startQE) {
        List<QuadEdge> edgeQuads = new ArrayList<QuadEdge>();
        QuadEdge currQE = startQE;
        edgeQuads.add(startQE);
        while (true) {
            Vertex v = currQE.dest();
            if (v instanceof WatershedVertex) {
                if (((WatershedVertex) v).isBoundaryNode()) {
                    break;
                }
            }
            QuadEdge nextQE = findNextEdgeOnBoundary(currQE);
            if (nextQE == null || nextQE == startQE)
                break;
            edgeQuads.add(nextQE);
            currQE = nextQE;
        }
        return edgeQuads;
    }

    private int[] getSideInfo(QuadEdge qe) {
        int[] regionID = new int[2];
        regionID[WatershedBoundaryEdge.LEFT] = WatershedEdge.getRegionID(qe);
        regionID[WatershedBoundaryEdge.RIGHT] = WatershedEdge.getRegionID(qe.sym());
        return regionID;
    }

    /**
     * Finds the next edge along the boundary containing the given edge.
     * 
     * @param qe
     * @return the next edge on the boundary
     * @return null if there is no further edge on a boundary
     */
    private QuadEdge findNextEdgeOnBoundary(QuadEdge qe) {
        QuadEdge sym = qe.sym();
        QuadEdge nextEdge = sym.oNext();
        while (nextEdge != sym) {
            if (WatershedEdge.isOnBoundary(nextEdge)) {
                return nextEdge;
            }
            nextEdge = nextEdge.oNext();
        }
        return null;
    }

    /**
     * Marks the triangles adjacent to the path as being extracted for a boundary. Path must first
     * be extracted. This allows avoiding including an edge in two different paths.
     */
    public void markEdgeTriangles() {
        for (QuadEdge qe: edgeQuads) {
            markEdgeTriangle(qe);
            markEdgeTriangle(qe.sym());
        }
    }

    private void markEdgeTriangle(QuadEdge qe) {
        WatershedTriangle tri = (WatershedTriangle) qe.getData();
        if (tri != null)
            tri.setBoundaryExtracted(true);
    }

    /**
     * Tests if the given edge path form "wedge" watershed. A "wedge" watershed polygon is one which
     * has exactly 2 line segments which are boundary edges, and the other portion of the boundary
     * is a constraint line. This case should not be smoothed, since smoothing may cause the
     * triangular ws boundary edge to be flipped across the constraint edge, thus changing the
     * topology of the situation. The path is a wedge if:
     * <ul>
     * <li>the list contains exactly 2 quadedges
     * <li>the endpoints of the path are hydro nodes
     * <li>the edges are incident on the same constraint edge
     * </ul>
     * 
     * @param quadedge the list of edges
     * @return true if these are the edges of a wedge
     */
    private static boolean isWedge(List<QuadEdge> quadedge) {
        if (quadedge.size() != 2)
            return false;

        QuadEdge qe0 = quadedge.get(0);
        QuadEdge qe1 = quadedge.get(1);

        WatershedVertex wv0 = (WatershedVertex) qe0.orig();
        WatershedVertex wv1 = (WatershedVertex) qe1.dest();

        if (!wv0.isNode())
            return false;
        if (!wv1.isNode())
            return false;

        /**
         * We don't care about the actual value of the hydro edge - we only care if there is a
         * constraint edge connecting the two nodes at the end of the path.
         */
        HydroEdge he = WatershedVertexUtil.getEdgeBetweenNodes(wv0, wv1);
        if (he != null)
            return true;
        return false;

    }

    // /**
    // * Tests if the given edges bound a single triangle in which the remaining edge is a
    // constraint.
    // * This is the case if:
    // * <ul>
    // * <li>the list contains exactly 2 edges
    // * <li>the edges bound the same triangle
    // * <li>the third edge of the triangle is a constraint edge
    // * </ul>
    // *
    // * @param quadedge the list of edges
    // * @return true if these are the edges of a constrained triangle
    // */
    // private static boolean isBoundingConstrainedTriangle(List quadedge) {
    // if (quadedge.size() != 2)
    // return false;
    //
    // QuadEdge qe0 = (QuadEdge) quadedge.get(0);
    // QuadEdge qe1 = (QuadEdge) quadedge.get(1);
    // WatershedTriangle commonTri = getCommonTriangle(qe0, qe1);
    // if (commonTri == null)
    // return false;
    //
    // QuadEdge qe3 = getOtherEdge(commonTri, qe0, qe1);
    //
    // if (QuadedgeConstraintFinder.isConstraintFromNode(qe3)) {
    // // System.out.println("isBoundingConstrainedTriangle: " + qe3);
    // return true;
    // }
    //
    // return false;
    // }

    // private static WatershedTriangle getCommonTriangle(QuadEdge qe0, QuadEdge qe1) {
    // WatershedTriangle tri0L = (WatershedTriangle) qe0.getData();
    // WatershedTriangle tri0R = (WatershedTriangle) qe0.sym().getData();
    //
    // WatershedTriangle tri1L = (WatershedTriangle) qe1.getData();
    // if (tri1L == tri0L)
    // return tri1L;
    // if (tri1L == tri0R)
    // return tri1L;
    //
    // WatershedTriangle tri1R = (WatershedTriangle) qe1.sym().getData();
    // if (tri1R == tri0L)
    // return tri1R;
    // if (tri1R == tri0R)
    // return tri1R;
    //
    // return null;
    // }

    // private static QuadEdge getOtherEdge(WatershedTriangle tri, QuadEdge qe0, QuadEdge qe1) {
    // QuadEdge[] edge = tri.getEdges();
    // for (int i = 0; i < 3; i++) {
    // if (edge[i].equalsNonOriented(qe0))
    // continue;
    // if (edge[i].equalsNonOriented(qe1))
    // continue;
    // return edge[i];
    // }
    // Assert.shouldNeverReachHere("Could not find other edge");
    // return null;
    // }

    /**
     * Finds vertices in the interior of the path defined by the list of QuadEdges which should be
     * respected (i.e. not altered by smoothing).
     * 
     * @param edgeQuads a list of QuadEdges defining the path
     * @return an array of booleans indicating which vertices should be respected
     */
    private boolean[] findRespectedVertices(List<QuadEdge> edgeQuads) {
        boolean[] isRespected = new boolean[edgeQuads.size() + 1];
        QuadEdge qe = null;
        int index = 0;
        for (Iterator<QuadEdge> it = edgeQuads.iterator(); it.hasNext();) {
            qe = (QuadEdge) it.next();
            // skip first edge, since its origin is not in the interior of the path
            if (index == 0) {
                index++;
                continue;
            }

            // check if the origin vertex is a local maxima
            if (isRespected(qe)) {
                // System.out.println(WKTWriter.toPoint(qe.orig().getCoordinate()));
                isRespected[index] = true;
            }
            index++;
        }

        // MD - testing - don't alter vertices of segment ajacent to a hydro constraint
        // MD - Nope, this is too much of a constraint. It leaves too many jagged edges.
        /*
         * if (isRespected.length >= 3) { if (isHydroNodeFirst) { isRespected[1] = true; } if
         * (isHydroNodeLast) { isRespected[isRespected.length - 2] = true; } }
         */
        return isRespected;
    }

    /**
     * Tests if the vertex at the origin of a given QuadEdge should be respected. (I.e. not altered
     * by smoothing)
     * 
     * @param qe
     */
    private static boolean isRespected(QuadEdge qe) {
        @SuppressWarnings("unchecked")
		List<QuadEdge> adjQE = QuadEdgeUtil.findEdgesIncidentOnOrigin(qe);
        if (!isLocalMaximum(adjQE))
            return false;

        /**
         * Given that the vertex is a local maximum, check that it is not adjacent to a constraint
         * (This is because DEM vertices adjacent to constraints tend to create false local maxima)
         */
        if (isAdjacentToConstraint(adjQE))
            return false;
        return true;
    }

    private static boolean isAdjacentToConstraint(List<QuadEdge> adjQE) {
        for (QuadEdge qe : adjQE) {
            Vertex v = qe.dest();
            if (v instanceof WatershedVertex) {
                WatershedVertex wv = (WatershedVertex) v;
                if (wv.isOnConstraint())
                    return true;
            }
        }
        return false;
    }

    private static boolean isLocalMaximum(List<QuadEdge> adjQE) {
        // WatershedDebug.watchPoint(qe.orig().getCoordinate(), new Coordinate(1573022.87397,
        // 600925.50443));

        for (QuadEdge qe : adjQE) {
            if (!isDownhill(qe))
                return false;
        }
        return true;
    }

    private static boolean isDownhill(QuadEdge qe) {
        double origHgt = qe.orig().getZ();
        double destHgt = qe.dest().getZ();
        if (origHgt > destHgt)
            return true;
        return false;
    }
}