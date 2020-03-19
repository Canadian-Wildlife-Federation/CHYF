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

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.util.Assert;

import net.refractions.chyf.watershed.model.WatershedTIN;
import net.refractions.chyf.watershed.model.WatershedTriangle;

import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeTriangle;
import org.locationtech.jts.triangulate.quadedge.Vertex;

/**
 * An algorithm class which allows splitting triangles in the waterhsed TIN. Triangles may be split
 * along their flow vector, or at an given point on an edge. n the case of splitting a triangle
 * along the flow vector, various heuristics and constraints are supported to control how and
 * whether a given triangle is split. When a triangle is split, the triangle opposite the split edge
 * must be split as well.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class TriangleSplitter {
    private static final double MIN_SPLIT_CROSS_TRI_DIST = 0.1;                     // 2.0;
    private static final double MIN_SPLIT_SEG_LEN        = 0.1;                     // 10.0;
    private static final double MIN_SPLIT_ANGLE          = 1.0;                     // MD - 2.0 is
    // better, but
    // need to lower
    // this to get
    // past a
    // situation

    private QuadEdgeSubdivision subdiv;
    private WatershedTIN        watershedTIN;

    private WatershedTriangle[] splitTri                 = new WatershedTriangle[4];
    // the vertex created by a split (null if split was not performed)
    private Vertex              splitVertex              = null;

    public TriangleSplitter(QuadEdgeSubdivision subdiv, WatershedTIN watershedTIN) {
        this.subdiv = subdiv;
        this.watershedTIN = watershedTIN;
    }

    /**
     * Tests whether a triangle has at least two outflow edges.
     * 
     * @param tri
     * @param angTol
     * @return
     */
    public static boolean hasAtLeastTwoOutflowEdges(WatershedTriangle tri, double angTol) {
        int numOutflow = 0;
        for (int i = 0; i < 3; i++) {
            if (tri.isOutflowEdge(i, angTol))
                numOutflow++;
        }
        return numOutflow >= 2;
    }

    /**
     * Splits a transRegion flow triangle. The triangle will not be split if the resulting geometry
     * fails tolerance constraints.
     * 
     * @param origTri the triangle to split
     * @param angTol the angle tolerance for determining outflow edges
     * @return the 4 newly created triangles
     * @return null if no split is possible due to tolerance constraints
     */
    public WatershedTriangle[] split(WatershedTriangle origTri, double flowAngTol) {
        splitVertex = null;
        // checkHasDifferentNeighbourRegion(origTri);

        if (!hasAtLeastTwoOutflowEdges(origTri, flowAngTol))
            return null;

        // origTri.printFlowAngles();

        int inflowEdgeIndex = getInflowEdge(origTri);
        WatershedTriangle inflowTri = (WatershedTriangle) origTri
            .getAdjacentTriangleAcrossEdge(inflowEdgeIndex);

        // don't split border triangles
        if (inflowTri != null) {
            // Region inflowRegion =
            inflowTri.getRegion();
            if (inflowTri.isBorder())
                return null;
        }

        QuadEdge inflowQE = origTri.getEdge(inflowEdgeIndex);
        int outVertexIndex = (inflowEdgeIndex + 2) % 3;
        // the splitting ray (from the outflow vertex to the inflow edge)
        Coordinate outFlowPt = origTri.getCoordinate(outVertexIndex);
        Coordinate flowPt = origTri.getFlowOutsideStartPt(outFlowPt);
        Coordinate splitPt = origTri.findEdgeIntersection(flowPt, outFlowPt, inflowEdgeIndex);
        // must not be splittable for some reason (degenerate? inflow edge parallel to flow?)
        if (splitPt == null)
            return null;

        // System.out.println(WKTWriter.toLineString(new CoordinateArraySequence(
        // new Coordinate[] { splitPt, p1 } )));

        // inflow edge vertices
        Coordinate edgePt0 = origTri.getEdge(inflowEdgeIndex).orig().getCoordinate();
        Coordinate edgePt1 = origTri.getEdge(inflowEdgeIndex).dest().getCoordinate();

        /**
         * for robustness check that split pt is not too close to an existing vertex
         */
        if (splitPt.distance(edgePt0) < MIN_SPLIT_CROSS_TRI_DIST
                || splitPt.distance(edgePt1) < MIN_SPLIT_CROSS_TRI_DIST)
            return null;

        // check that splitting segment is not too short
        if (splitPt.distance(outFlowPt) < MIN_SPLIT_SEG_LEN)
            return null;

        /**
         * Check that angles of split triangles are not too narrow. It is sufficient to check only
         * the angle opposite the edge containing the split point, since the angles adjacent to that
         * edge will get larger, not smaller
         */
        // the split tris for the original triangle
        double ang0 = Math.toDegrees(Angle.angleBetween(splitPt, outFlowPt, edgePt0));
        double ang1 = Math.toDegrees(Angle.angleBetween(splitPt, outFlowPt, edgePt1));
        if (ang0 < MIN_SPLIT_ANGLE || ang1 < MIN_SPLIT_ANGLE)
            return null;

        // the split tris for the neighbour triangle across the inflow edge (which also must be
        // split)
        Coordinate inApexPt = getVertexOpposite(inflowTri, inflowQE.sym());
        double angIn0 = Math.toDegrees(Angle.angleBetween(splitPt, inApexPt, edgePt0));
        double angIn1 = Math.toDegrees(Angle.angleBetween(splitPt, inApexPt, edgePt1));
        if (angIn0 < MIN_SPLIT_ANGLE || angIn1 < MIN_SPLIT_ANGLE)
            return null;

        // assert: edge being split is not a constraint (hydro) edge
        // this is important - otherwise would have to set vertex constraint info

        // now that all criteria are satified, can perform the split
        splitTriangle(splitPt, edgePt0, edgePt1, inflowQE);

        // remove the original triangles
        if (inflowTri != null)
            inflowTri.kill();
        origTri.kill();

        return splitTri;
    }

    /**
     * Gets the coordinate of the vertex opposite an edge of a SubdivisionTriangle
     * 
     * @param tri the triangle
     * @param edge the edge
     * @return the coordinate of the opposite vertex
     */
    private static Coordinate getVertexOpposite(QuadEdgeTriangle tri, QuadEdge edge) {
        int edgeIndex = tri.getEdgeIndex(edge);
        int oppVertexIndex = (edgeIndex + 2) % 3;
        return tri.getCoordinate(oppVertexIndex);
    }

    private void initSplitTriangles() {
        for (int i = 0; i < 4; i++) {
            splitTri[i] = null;
        }
    }

    /**
     * Splits the triangles on either side of the inflowQE QuadEdge at the point splitPt.
     * 
     * @param splitPt the point on the inflow edge to split at
     * @param edgePt0 an endpoint of the inflowEdge
     * @param edgePt1 the other inflow edge endpoint
     * @param inflowQE the inflow edge (which is split)
     */
    private void splitTriangle(Coordinate splitPt, Coordinate edgePt0, Coordinate edgePt1,
            QuadEdge inflowQE) {
        /**
         * The inserted vertex splits one of the triangles containing the inflow edge. The inflow
         * edge needs to be rotated to split the opposite triangle as well.
         */
        splitVertex = watershedTIN.getVertexFactory().createVertex(splitPt, edgePt0, edgePt1);
        QuadEdge newQE = subdiv.insertSite(splitVertex);
        QuadEdge.swap(inflowQE);

        // newQE has v as dest, so flip it to get edge starting at v
        QuadEdge startQE = newQE.sym();

        QuadEdge[] triEdge = new QuadEdge[3];
        // create the triangles around the new vertex
        QuadEdge qe = startQE;
        int i = 0;
        initSplitTriangles();
        do {
            QuadEdgeSubdivision.getTriangleEdges(qe, triEdge);

            if (!isFrameTriangle(triEdge)) {
                WatershedTriangle tri = new WatershedTriangle(watershedTIN, triEdge);
                splitTri[i++] = tri;
                // System.out.println(tri);
                // System.out.println("flow angle: " + tri.getFlowAngle());
            }
            qe = qe.oNext();
        } while (qe != startQE);
        Assert.isTrue(i <= 4, "too many triangles around split vertex");
    }

    private boolean isFrameTriangle(QuadEdge[] edge) {
        for (int i = 0; i < edge.length; i++) {
            if (subdiv.isFrameEdge(edge[i]))
                return true;
        }
        return false;
    }

    private static int getInflowEdge(WatershedTriangle tri) {
        for (int i = 0; i < 3; i++) {
            if (!tri.isOutflowEdge(i))
                return i;
        }
        return -1;
    }

}