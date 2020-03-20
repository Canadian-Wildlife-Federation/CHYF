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

import java.util.Collection;

import org.locationtech.jts.algorithm.Distance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.linearref.LinearLocation;
import org.locationtech.jts.linearref.LocationIndexedLine;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;

import net.refractions.chyf.util.LineSegmentUtil;
import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.OrientedHydroEdge;
import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Provides methods for determining the underlying constraint edges from the {@link QuadEdge}s in
 * the TIN.
 * <p>
 * Quadedges which lie on constraint edges are termed <i>constrained quadedges</i> There is no
 * direct link between quadedges and the underlying constraint edges. The only linkage is via the
 * vertices on the end of the quadedge. Furthermore, due to constraint splitting, the vertices of a
 * constrainted quadedge are not necessarily vertices of the constraint edge itself. However,
 * constrained quadedge vertices <i>will</i> lie (approximately) on a line segment of the
 * constraint edge.
 * 
 * @author Martin Davis
 */
public class QuadedgeConstraintFinder {
    /**
     * Tests whether this edge is a (or is part of a) constraint edge which starts at a constraint
     * node.
     * 
     * @param e the edge to test
     * @return true if the edge is a constraint edge with a constraint node as its from vertex
     */
    public static boolean isConstraintFromNode(QuadEdge e) {
        WatershedVertex v0 = null;
        WatershedVertex v1 = null;
        if (e.orig() instanceof WatershedVertex)
            v0 = (WatershedVertex) e.orig();
        if (e.dest() instanceof WatershedVertex)
            v1 = (WatershedVertex) e.dest();

        if (v0 == null || v1 == null)
            return false;

        // now we know we have two vertices on constraints - but they may be different constraints
        if (!v0.isNode())
            return false;
        if (!v1.isOnConstraint())
            return false;

        /**
         * Can't simply check that node and vertex on same constraint, because nodes only record one
         * of the constraints lines they are on. Need to use a more precise check: that there is a
         * constraint line containing both vertices on a single segment. If the to-vertex is a node,
         * it will be part of many constraint lines, so need to check them all.
         */
        if (v1.isNode()) {
            HydroEdge he = v0.getNodeNodeConstraint(v1);
            return he != null;
        }
        return isNodeVertexOnSameConstraintSegment(v0, v1);
    }

    private static boolean isNodeVertexOnSameConstraintSegment(WatershedVertex node,
            WatershedVertex v) {
        Coordinate nodePt = node.getCoordinate();
        Coordinate toPt = v.getCoordinate();

        LineString line = v.getConstraintLine();
        return isCoincidentWithEndSeg(nodePt, toPt, line);
    }

    private static boolean isCoincidentWithEndSeg(Coordinate nodePt, Coordinate toPt,
            LineString line) {
        LineSegment endSeg = getEndSeg(line, nodePt);
        if (endSeg == null)
            return false;
        if (isCoincident(nodePt, toPt, endSeg))
            return true;
        return false;
    }

    /*
     * private static boolean hasEndpoint(LineString line, Coordinate pt) { Coordinate p0 =
     * line.getCoordinateN(0); if (p0.equals2D(pt)) return true; Coordinate p1 =
     * line.getCoordinateN(line.getNumPoints() - 1); if (p1.equals2D(pt)) return true; return false; }
     */

    private static final LineSegment endSeg = new LineSegment();

    /**
     * Gets the end segment which starts at point pt (if any)
     * 
     * @param line
     * @param pt
     * @return the end segment
     * @return null, if neither end segment terminates in pt
     */
    private static LineSegment getEndSeg(LineString line, Coordinate pt) {
        Coordinate p0 = line.getCoordinateN(0);
        if (p0.equals2D(pt)) {
            endSeg.setCoordinates(p0, line.getCoordinateN(1));
            return endSeg;
        }
        Coordinate pn = line.getCoordinateN(line.getNumPoints() - 1);
        if (pn.equals2D(pt)) {
            endSeg.setCoordinates(pn, line.getCoordinateN(line.getNumPoints() - 2));
            return endSeg;
        }
        return null;
    }

    /**
     * Tests if a quadedge is a constrained edge. I.e. tests if the edge lies exactly on a
     * constraint edge.
     * 
     * @param e a quadedge
     * @return true if the edge is constrained
     */
    public static boolean isConstrained(QuadEdge e) {
        // this is a bit inefficient, but perhaps it doesn't matter that much
        return getOrientedHydroEdge(e) != null;
    }

    /**
     * Gets the HydroEdge that a quadedge lies on (if any).
     * 
     * @param qe the quadedge to use
     * @return the hydro edge for the quadedge (if any)
     * @return null if the quadedge does not lie on a hydro edge
     */
    public static OrientedHydroEdge getOrientedHydroEdge(QuadEdge qe) {
        // WatershedDebug.watchPoint(qe.dest().getCoordinate(), new Coordinate(1586004.8433,
        // 588952.5324));
        /**
         * Note that an edge may lie between two constraint vertices, and yet still not be on the
         * constraint.
         */
        if (!(qe.orig() instanceof WatershedVertex))
            return null;
        if (!(qe.dest() instanceof WatershedVertex))
            return null;

        WatershedVertex v0 = (WatershedVertex) qe.orig();
        WatershedVertex v1 = (WatershedVertex) qe.dest();

        // both vertices must be on constraints
        if (!v0.isOnConstraint())
            return null;
        if (!v1.isOnConstraint())
            return null;

        boolean isNode0 = v0.isNode();
        boolean isNode1 = v1.isNode();

        // if both are nodes, need to extract common hydro edge (if any)
        if (isNode0 && isNode1)
            return getNodeNodeOrientedHydroEdge(v0, v1);

        if (isNode0) {
            return getNodeVertexOrientedHydroEdge(v0, v1);
        }
        if (isNode1) {
            // find the node-vertex edge (which will have reverse orientation)
            OrientedHydroEdge he = getNodeVertexOrientedHydroEdge(v1, v0);
            if (he == null)
                return null;
            // flip the edge before returning it
            return new OrientedHydroEdge(he.getEdge(), !he.isForward());
        }

        // Both are vertices (not nodes)
        return getVertexVertexOrientedHydroEdge(v0, v1);
    }

    /**
     * Gets the constraint {@link HydroEdge} which is a single segment between two nodes (if any),
     * with its orientation relative to the nodes.
     * 
     * @param node0 a node to test
     * @param node1 the other node to test
     * @return the oriented hydro edge connecting these nodes (if any)
     * @return null if there is no constraint between this node and the other
     */
    public static OrientedHydroEdge getNodeNodeOrientedHydroEdge(WatershedVertex node0,
            WatershedVertex node1) {
        Coordinate p0 = node0.getCoordinate();
        Coordinate p1 = node1.getCoordinate();

		Collection<HydroEdge> hydroEdges = node0.getNodeEdges();
        for (HydroEdge hydroEdge : hydroEdges) {
            LineString line = hydroEdge.getLine();

            if (line.getNumPoints() != 2)
                continue;

            Coordinate linePt0 = line.getCoordinateN(0);
            Coordinate linePt1 = line.getCoordinateN(1);
            if (p0.equals2D(linePt0) && p1.equals2D(linePt1))
                return new OrientedHydroEdge(hydroEdge, true);
            if (p0.equals2D(linePt1) && p1.equals2D(linePt0))
                return new OrientedHydroEdge(hydroEdge, false);
        }
        return null;
    }

    /**
     * Gets the constraint {@link HydroEdge} between a node and a constraint vertex (if any), with
     * its orientation relative to the nodes.
     * 
     * @param node a node to test
     * @param vert a constraint vertex to test
     * @return the oriented hydro edge connecting these vertices (if any)
     * @return null if there is no constraint between the vertices
     */
    public static OrientedHydroEdge getNodeVertexOrientedHydroEdge(WatershedVertex node,
            WatershedVertex vert) {
        Coordinate nPt = node.getCoordinate();
        Coordinate vPt = vert.getCoordinate();

        HydroEdge he = (HydroEdge) vert.getConstraint();

        /**
         * Now check to see if the node lies at one end or the other and the vertex lies on the same
         * segment
         */

        LineString line = he.getLine();
        int nPts = line.getNumPoints();
        Coordinate linePt0 = line.getCoordinateN(0);
        Coordinate linePtN = line.getCoordinateN(nPts - 1);

        if (nPt.equals2D(linePt0)) {
            if (isOnSegment(vPt, nPt, line.getCoordinateN(1))) {
                return new OrientedHydroEdge(he, true);
            }
        } else if (nPt.equals2D(linePtN)) {
            if (isOnSegment(vPt, line.getCoordinateN(nPts - 2), nPt)) {
                return new OrientedHydroEdge(he, false);
            }
        }
        // node is not an endoint of the vertex constraint, so they must not be connected
        return null;
    }

    /**
     * Gets the constraint {@link HydroEdge} between two vertices which are not nodes (if any), with
     * its orientation relative to the nodes.
     * 
     * @param v0 a vertex
     * @param v1 another vertex
     * @return the oriented hydro edge connecting these vertices (if any)
     * @return null if there is no constraint between the vertices
     */
    public static OrientedHydroEdge getVertexVertexOrientedHydroEdge(WatershedVertex v0,
            WatershedVertex v1) {
        if (v0.getConstraint() != v1.getConstraint())
            return null;

        Coordinate p0 = v0.getCoordinate();
        Coordinate p1 = v1.getCoordinate();

        HydroEdge he = (HydroEdge) v0.getConstraint();

        /**
         * Compute segments for vertices, and test if they are the same
         */
        LineString line = he.getLine();
        LocationIndexedLine indexedLine = new LocationIndexedLine(line);
        LinearLocation loc0 = indexedLine.indexOf(p0);
        LinearLocation loc1 = indexedLine.indexOf(p1);

        // test if the vertices are on the same segment
        if (isOnSameSegment(loc0, loc1)) {
            // on same segment - test relative orientation
            if (loc0.compareTo(loc1) < 0) {
                return new OrientedHydroEdge(he, true);
            } else {
                return new OrientedHydroEdge(he, false);
            }
        }
        return null;
    }

    /**
     * Tests if two {@link LinearLocation}s lie on the same line segment.
     * 
     * @param loc0 a LinearLocation
     * @param loc1 a LinearLocation
     * @return true if the locations lie on the same line segment
     */
    public static boolean isOnSameSegment(LinearLocation loc0, LinearLocation loc1) {
        int seg0 = loc0.getSegmentIndex();
        int seg1 = loc1.getSegmentIndex();
        if (seg0 == seg1)
            return true;
        if (seg1 - seg0 == 1 && loc1.getSegmentFraction() == 0.0)
            return true;
        if (seg0 - seg1 == 1 && loc0.getSegmentFraction() == 0.0)
            return true;
        return false;
    }

    private static final double SEGMENT_TOLERANCE = WatershedSettings.SNAP_TOLERANCE / 100.0;

    /**
     * Tests if a point lies on a line segment {p0, p1) up to a fixed distance tolerance.
     * 
     * @param pt the point to test
     * @param p0 an endpoint of the line segment
     * @param p1 an endpoint of the line segment
     * @return true if the point lies on the segment (within the offset distance tolerance)
     */
    public static boolean isOnSegment(Coordinate pt, Coordinate p0, Coordinate p1) {
        double dist = Distance.pointToSegment(pt, p0, p1);
        return dist < SEGMENT_TOLERANCE;
    }

    /**
     * Tests if a segment lying between two points is coincident with the given segment
     * 
     * @param p0 an endpoint of the test segment
     * @param p1 an endpoint of the test segment
     * @param seg the segment to compare with
     * @return true if the points segment is coincident with the given segment
     */
    private static boolean isCoincident(Coordinate p0, Coordinate p1, LineSegment seg) {
        Coordinate midPt = LineSegmentUtil.midPoint(p0, p1);
        double midDist = seg.distance(midPt);
        return midDist < SEGMENT_TOLERANCE;
    }

}
