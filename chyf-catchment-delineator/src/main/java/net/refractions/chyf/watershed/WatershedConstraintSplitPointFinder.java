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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.triangulate.ConstraintSplitPointFinder;
import org.locationtech.jts.triangulate.NonEncroachingSplitPointFinder;
import org.locationtech.jts.triangulate.Segment;
import org.locationtech.jts.triangulate.SplitSegment;
import org.locationtech.jts.util.Assert;
//import org.locationtech.jts.index.segmenttree.Segment;
//import org.locationtech.jts.triangulate.ConstraintSplitPointFinder;
//import org.locationtech.jts.triangulate.NonEncroachingSplitPointFinder;
//import org.locationtech.jts.triangulate.SplitSegment;

import net.refractions.chyf.watershed.debug.WatershedDebug;
import net.refractions.chyf.watershed.hydrograph.HydroNode;
import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.WatershedConstraint;
import net.refractions.chyf.watershed.model.WatershedTIN;

/**
 * A strategy for finding constraint split points for data used to build watersheds. Incorporates
 * heuristics to improve the quality of the triangulation and to prevent repeated splitting
 * (typically at narrow constraint angles). The heuristics used depend on the geometric
 * characteristics of the hydrographic linework (E.g. the angle size between constraints should not
 * be extremely small, and there are unlikely to be many narrow angles very near each other.)
 * 
 * @author mbdavis
 */
public class WatershedConstraintSplitPointFinder implements ConstraintSplitPointFinder {
    private WatershedTIN                   watershedTIN;
    private NonEncroachingSplitPointFinder neSplitFinder  = new NonEncroachingSplitPointFinder();
    private GeometryFactory gf;

    /**
     * Use a fairly small number here, since this is only detecting whether a split point lies
     * exactly on a split segment.
     */
    private static final double            ON_SEGMENT_TOL = WatershedSettings.SNAP_TOLERANCE / 10;

    public WatershedConstraintSplitPointFinder(WatershedTIN watershedTIN, GeometryFactory gf) {
        this.watershedTIN = watershedTIN;
        this.gf = gf;
    }

    /**
     * Finds a split point, taking advantage of the local geometry if possible. Ensures split point
     * matches WatershedSettings precision model.
     * 
     * @param seg the encroached segment
     * @param encroachPt the encroaching point
     * @return the point at which to split the encroached segment
     */
    public Coordinate findSplitPoint(Segment seg, Coordinate encroachPt) {
        Coordinate rawSplitPt = findRawSplitPoint(seg, encroachPt);
        gf.getPrecisionModel().makePrecise(rawSplitPt);
        return rawSplitPt;
    }

    private Coordinate findRawSplitPoint(Segment seg, Coordinate encroachPt) {
        WatershedDebug.watchPoint(encroachPt, new Coordinate(1146295.99018, 403391.70857));

        /**
         * Special cases which occur in watershed constraints and which can be optimized. If the
         * encroaching point is adjacent to both endpoints of the segment in the hydro edge graph,
         * it (probably?) forms a triangle. In this case the best thing to do is to split the
         * opposite side (the segment) at the projected point. If the encroaching point is adjacent
         * to one segment endpoint, it probably lies on an arm of a constraint wedge. The best thing
         * to do here is to split the opposite arm of the wedge (the segment) at the same distance
         * from the apex as the encroaching point (thus forming an isoceles triangle).
         */
        int position = getIndexOfAdjacentEndpoint(encroachPt, seg);
        // adjacent to both endpoints
        if (position == BOTH_ENDS_ADJACENT) {
            return NonEncroachingSplitPointFinder.projectedSplitPoint(seg, encroachPt);
        }
        // adjacent to one endpoint only
        else if (position >= 0) {
            LineSegment orientedSeg = orientedLineSegment(seg, position);
            return isoscelesSplitPoint(orientedSeg, encroachPt);
        }
        /**
         * At this point we are dealing with a situation which we do not know enough about to take
         * advantage of the surrounding geometry. All we have to work with is the encroaching point
         * and the encroached segment. The general strategy is to choose a split point which will
         * maximise the lengths of the split segments, but which will not themselves be encroached
         * upon by the same encroaching point. (This would result in cascaded splitting).
         */
        return neSplitFinder.findSplitPoint(seg, encroachPt);
    }

    /**
     * Computes a split point on a LineSegment such that the distance from the encroaching point to
     * the start point of the segment is the same as the distance from the split point. If the
     * encroaching point is connected to the segment start point, this creates an isosceles triangle
     * in the TIN. This has the nice property that it won't result in the encroaching segment being
     * re-split by the new split point.
     * 
     * @param seg a line segment
     * @param encroachPt the encroaching point
     * @return a point at which to create a split
     */
    private Coordinate isoscelesSplitPoint(LineSegment seg, Coordinate encroachPt) {
        double dist = encroachPt.distance(seg.p0);
        Coordinate splitPt = seg.pointAlong(dist / seg.getLength());
        return splitPt;
    }

    private static final int BOTH_ENDS_ADJACENT   = 2;
    private static final int NEITHER_END_ADJACENT = -1;
    /**
     * Gets the index of the segment endpoint which is adjacent to the given point on the constraint
     * graph, if any.
     * 
     * @param pt a point (which may or may not be on a constraint)
     * @param seg a constraint segment
     * @return the index of the segment endpoint which is adjacent
     * @return 2 if both endpoints are adjacent
     * @return -1 if neither endpoint is adjacent
     */
    private int getIndexOfAdjacentEndpoint(Coordinate pt, Segment seg) {
        boolean isAdjacent0 = isPointAdjacent(pt, seg.getStart(), seg);
        boolean isAdjacent1 = isPointAdjacent(pt, seg.getEnd(), seg);

        if (isAdjacent0 && isAdjacent1)
            return BOTH_ENDS_ADJACENT;
        if (isAdjacent0)
            return 0;
        if (isAdjacent1)
            return 1;
        return NEITHER_END_ADJACENT;
    }

    private boolean isPointAdjacent(Coordinate testPt, Coordinate segPt, Segment seg) {
        if (isNode(segPt)) {
            return isPointAdjacentToNode(testPt, segPt);
        }
        return isPointAdjacentToVertex(testPt, segPt, seg);
    }

    /**
     * Tests if a point is adjacent in the constraint graph to the given segment endpoint which is a
     * interior vertex of a constraint. This is the case if the point is an adjacent vertex on the
     * constraint linestring, OR lies "on" an adjacent segment in the graph (since the testPt may
     * have been introduced as a split point).
     * 
     * @param segPt the segment endpoint to test
     * @param testPt the point to test
     * @param seg the constraint segment to test
     * @return true if
     */
    private boolean isPointAdjacentToVertex(Coordinate testPt, Coordinate segPt, Segment seg) {
        WatershedConstraint watershedConstraint = (WatershedConstraint) seg.getData();
        HydroEdge he = watershedConstraint.getEdge();
        LineString line = he.getLine();

        int segIndex = findIndex(segPt, line);

        // check if the test point equals the vertex before the seg pt
        if (segIndex > 0) {
            Coordinate prevPt = line.getCoordinateN(segIndex - 1);
            if (isOnSegment(testPt, prevPt, segPt, ON_SEGMENT_TOL))
                return true;
        }
        // check if the test point equals the vertex after the seg pt
        if (segIndex < line.getNumPoints() - 1) {
            Coordinate nextPt = line.getCoordinateN(segIndex + 1);
            if (isOnSegment(testPt, nextPt, segPt, ON_SEGMENT_TOL))
                return true;
        }
        return false;
    }

    // private boolean OLDisPointAdjacentToVertex(Coordinate testPt, Coordinate segPt, Segment seg)
    // {
    // WatershedConstraint watershedConstraint = (WatershedConstraint) seg.getData();
    // HydroEdge he = watershedConstraint.getEdge();
    // LineString line = he.getLine();
    //
    // int segIndex = findIndex(segPt, line);
    //
    // // check if the test point equals the vertex before the seg pt
    // if (segIndex > 0) {
    // Coordinate prevPt = line.getCoordinateN(segIndex - 1);
    // if (testPt.equals2D(prevPt))
    // return true;
    // }
    // // check if the test point equals the vertex after the seg pt
    // if (segIndex < line.getNumPoints() - 1) {
    // Coordinate nextPt = line.getCoordinateN(segIndex + 1);
    // if (testPt.equals2D(nextPt))
    // return true;
    // }
    // return false;
    // }

    private boolean isPointAdjacentToNode(Coordinate testPt, Coordinate nodePt) {
        HydroNode node = getNode(nodePt);
        for (HydroEdge he : node.getEdges()) {
            if (isPointAdjacentToNode(testPt, nodePt, he))
                return true;
        }
        return false;
    }

    /**
     * Tests if a point is adjacent on the constraint graph to a given segment endpoint which is a
     * node in the graph. This is the case if the point is an adjacent vertex on some other
     * constraint incident on the node.
     * 
     * @param nodePt the node to test
     * @param testPt the point to test
     * @param he the hydro edge containing the node
     * @return true if the point is adjacent to the node in the constraint graph
     */
    private boolean isPointAdjacentToNode(Coordinate testPt, Coordinate nodePt, HydroEdge he) {
        LineString line = he.getLine();
        int n = line.getNumPoints();
        if (nodePt.equals2D(line.getCoordinateN(0))) {
            return isOnSegment(testPt, nodePt, line.getCoordinateN(1), ON_SEGMENT_TOL);
            // return testPt.equals2D(line.getCoordinateN(1));
        } else if (nodePt.equals2D(line.getCoordinateN(n - 1))) {
            return isOnSegment(testPt, nodePt, line.getCoordinateN(n - 2), ON_SEGMENT_TOL);
            // return testPt.equals2D(line.getCoordinateN(n - 2));
        }
        Assert.shouldNeverReachHere("node pt is not an endpoint of the edge");
        return false;
    }

    private LineSegment seg = new LineSegment();

    /**
     * Tests if a point is on a given line segment, up to a given distance tolerance
     * 
     * @param testPt
     * @param p0
     * @param p1
     * @param distanceTolerance
     * @return
     */
    private boolean isOnSegment(Coordinate testPt, Coordinate p0, Coordinate p1,
            double distanceTolerance) {
        seg.p0 = p0;
        seg.p1 = p1;
        double dist = seg.distance(testPt);
        return dist <= distanceTolerance;
    }

    /**
     * Gets a {@link LineSegment} for the given segment, oriented with the endpoint specified by the
     * position index as the start point of the line segment.
     * 
     * @param seg the segment to orient
     * @param position the index of the endpoint which should be at the start of the oriented result
     * @return a line segment oriented appropriately
     */
    private static LineSegment orientedLineSegment(Segment seg, int position) {
        LineSegment lineSeg = new LineSegment(seg.getLineSegment());
        if (position == 1)
            lineSeg.reverse();
        return lineSeg;
    }

    private HydroNode getNode(Coordinate pt) {
        return watershedTIN.getHydroGraph().getNode(pt);
    }

    private boolean isNode(Coordinate pt) {
        if (getNode(pt) == null)
            return false;
        return true;
    }

    // private boolean isTopologicalNode(Coordinate pt) {
    // HydroNode node = watershedTIN.getHydroGraph().getNode(pt);
    // if (node == null)
    // return false;
    // return node.isTrueNode();
    // }

    /**
     * Finds the index of the occurence of the point p in the given line (if any)
     * 
     * @param p a point
     * @param line a LineString
     * @return the index of p in line
     * @return -1 if the point does not occur in the line
     */
    private static int findIndex(Coordinate p, LineString line) {
        for (int i = 0; i < line.getNumPoints(); i++) {
            if (p.equals2D(line.getCoordinateN(i))) {
                return i;
            }
        }
        return -1;
    }

}

// MD - save for testing
class OLDWatershedConstraintSplitPointFinder implements ConstraintSplitPointFinder {
    private WatershedTIN watershedTIN;

    public OLDWatershedConstraintSplitPointFinder(WatershedTIN watershedTIN) {
        this.watershedTIN = watershedTIN;
    }

    /**
     * Finds a split point, taking advantage of the local geometry if possible.
     * 
     * @param seg the encroached segment
     * @param encroachPt the encroaching point
     * @return the point at which to split the encroached segment
     */
    public Coordinate findSplitPoint(Segment seg, Coordinate encroachPt) {
        // if (encroachPt.distance(new Coordinate(1630718.9337, 549945.6869)) < .00001) {
        // Debug.println(encroachPt);
        // }

        // special case for segments which originate at 3-nodes
        LineSegment singleNodeSeg = getSingleNodeOrientedSegment(seg);
        if (singleNodeSeg != null) {
            return isoscelesSplitPoint(singleNodeSeg, encroachPt);
        }

        // special case for segments split by adjacent points on the same constraint line
        int position = findClosestPositionIfAdjacentOnConstraint(encroachPt, seg);
        if (position >= 0) {
            LineSegment orientedSeg = orientedLineSegment(seg, position);
            return isoscelesSplitPoint(orientedSeg, encroachPt);
        }
        /**
         * At this point we are dealing with a situation for which we do not know enough to take
         * advantage of the surrounding geometry. All we can work with is the encroaching point and
         * the encroached segment. The general strategy is to choose a split point which will
         * maximise the lengths of the split segments, but which will not themselves be encroached
         * upon by the same encroaching point. (This would result in cascaded splitting).
         */
        return findBasicSplitPoint(seg, encroachPt);
    }

    /**
     * A basic strategy for finding split points when nothing extra is known about the geometry of
     * the situation.
     * 
     * @param seg the encroached segment
     * @param encroachPt the encroaching point
     * @return the point at which to split the encroached segment
     */
    private static Coordinate findBasicSplitPoint(Segment seg, Coordinate encroachPt) {
        LineSegment lineSeg = seg.getLineSegment();
        double segLen = lineSeg.getLength();
        double midPtLen = segLen / 2;
        SplitSegment splitSeg = new SplitSegment(lineSeg);

        Coordinate projPt = projectedSplitPoint(seg, encroachPt);
        /**
         * Compute the largest diameter (length) that will produce a split segment which is not
         * still encroached upon by the encroaching point (The length is reduced slightly by a
         * safety factor)
         */
        double nonEncroachDiam = projPt.distance(encroachPt) * 2 * 0.8; // .99;
        double maxSplitLen = nonEncroachDiam;
        if (maxSplitLen > midPtLen) {
            maxSplitLen = midPtLen;
        }
        splitSeg.setMinimumLength(maxSplitLen);

        splitSeg.splitAt(projPt);

        /**
         * Narrow angles between segments in a single constraint can cause repeated splitting if the
         * initial split point is not well-chosen. This detects that case and chooses a better split
         * point.
         */
        /*
         * // MD - not sure when this is needed? if (isConstraintInteriorNarrowAngle(lineSeg.p1,
         * seg)) return projPt;
         */

        return splitSeg.getSplitPoint();
    }

    /**
     * Computes a split point on a LineSegment such that the distance from the encroaching point to
     * the start point of the segment is the same as the distance from the split point. If the
     * encroaching point is connected to the segment start point, this creates an isosceles triangle
     * in the TIN. This has the nice property that it won't result in the encroaching segment being
     * re-split by the new split point.
     * 
     * @param seg a line segment
     * @param encroachPt the encroaching point
     * @return a point at which to create a split
     */
    private Coordinate isoscelesSplitPoint(LineSegment seg, Coordinate encroachPt) {
        double dist = encroachPt.distance(seg.p0);
        Coordinate splitPt = seg.pointAlong(dist / seg.getLength());
        return splitPt;
    }

    /**
     * Computes a split point which is the projection of the encroaching point on the segment
     * 
     * @param seg
     * @param encroachPt
     * @return
     */
    private static Coordinate projectedSplitPoint(Segment seg, Coordinate encroachPt) {
        LineSegment lineSeg = seg.getLineSegment();
        Coordinate projPt = lineSeg.project(encroachPt);
        return projPt;
    }

    /**
     * Gets a LineSegment for the given segment, oriented with the endpoint indexed by position as
     * the start point of the line segment.
     * 
     * @param seg
     * @param position
     */
    private static LineSegment orientedLineSegment(Segment seg, int position) {
        LineSegment lineSeg = new LineSegment(seg.getLineSegment());
        if (position == 1)
            lineSeg.reverse();
        return lineSeg;
    }

    private LineSegment getSingleNodeOrientedSegment(Segment seg) {
        int nodeIndex = getSingleNodeIndex(seg);
        if (nodeIndex < 0)
            return null;
        LineSegment lineSeg = new LineSegment(seg.getLineSegment());
        if (nodeIndex == 1)
            lineSeg.reverse();
        return lineSeg;
    }

    /**
     * Finds the index of the single endpoint of the Segment which is a node (if any)
     * 
     * @param seg
     * @return the index of the single endpoint which is a node
     * @return -1 if neither or both endpoints are nodes
     */
    private int getSingleNodeIndex(Segment seg) {
        boolean isNodeAtEnd0 = isTopologicalNode(seg.getStart());
        boolean isNodeAtEnd1 = isTopologicalNode(seg.getEnd());

        int nodeCount = 0;
        int nodeIndex = -1;
        if (isNodeAtEnd0) {
            nodeCount++;
            nodeIndex = 0;
        }
        if (isNodeAtEnd1) {
            nodeCount++;
            nodeIndex = 1;
        }

        if (nodeCount == 1)
            return nodeIndex;
        return -1;
    }

    private boolean isTopologicalNode(Coordinate pt) {
        HydroNode node = watershedTIN.getHydroGraph().getNode(pt);
        if (node == null)
            return false;
        return node.isTrueNode();
    }

    // /**
    // * Tests whether a point p is an endpoint on a multi-segment constraint line for a segment.
    // This
    // * method is Watershed-specific, since it makes use of the constraint data on the segment.
    // *
    // * @param p
    // * @param seg
    // * @return true if the point p is an endpoint of a multi-segment constraint line
    // */
    // private static boolean isConstraintEndpoint(Coordinate p, Segment seg) {
    // WatershedConstraint watershedConstraint = (WatershedConstraint) seg.getData();
    // HydroEdge he = watershedConstraint.getEdge();
    // LineString line = he.getLine();
    // if (line.getNumPoints() <= 2)
    // return false;
    // if (isEndPoint(p, line))
    // return true;
    // return false;
    // }

    // private static boolean isEndPoint(Coordinate p, LineString line) {
    // if (p.equals2D(line.getCoordinateN(0)))
    // return true;
    // if (p.equals2D(line.getCoordinateN(line.getNumPoints() - 1)))
    // return true;
    // return false;
    // }

    /**
     * If the given point is a vertex on the same constraint as the segment and adjacent to it, gets
     * the positional index of the closest point of the segment.
     * 
     * @param testPt the point to test
     * @param the constraint segment to test
     * @return the positional index of the closest segment point (0 or 1)
     * @return -1 if the point is not an adjacent constraint vertex
     */
    private static int findClosestPositionIfAdjacentOnConstraint(Coordinate testPt, Segment seg) {
        WatershedConstraint watershedConstraint = (WatershedConstraint) seg.getData();
        HydroEdge he = watershedConstraint.getEdge();
        LineString line = he.getLine();
        int nPts = line.getNumPoints();
        if (nPts <= 2)
            return -1;

        int segIndex = findLastIndex(seg.getStart(), line);
        // Assert: the segment is given in the same orientation as the linestring

        // check if the test point equals the vertex before the seg
        if (segIndex > 0) {
            Coordinate prevPt = line.getCoordinateN(segIndex - 1);
            if (testPt.equals2D(prevPt))
                return 0;
        }
        // check if the test point equals the vertex after the seg
        if (segIndex < nPts - 2) {
            Coordinate nextPt = line.getCoordinateN(segIndex + 2);
            if (testPt.equals2D(nextPt))
                return 1;
        }

        return -1;
    }

    /**
     * Finds the index of the last occurence of the point p in the given line (if any)
     * 
     * @param p
     * @param line
     * @return the last index of p in line
     * @return -1 if the point does not occur in the line
     */
    private static int findLastIndex(Coordinate p, LineString line) {
        int index = -1;
        for (int i = 0; i < line.getNumPoints(); i++) {
            if (p.equals2D(line.getCoordinateN(i))) {
                index = i;
            }
        }
        return index;
    }

}
