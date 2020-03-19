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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.watershed.model.WatershedTriangle;

/**
 * Traces flow across a {@link WatershedTriangle}. An algorithm class - does not maintain state.
 * The start point for tracing is always either a point on an edge or a vertex of a triangle. The
 * flow may proceed via either the face, or along a channel edge. A channel edge occurs when the
 * flow vector from the triangles on either side of the edge points toward the same edge. Flow that
 * ends at a vertex may terminate there, if the vertex is a pit vertex (i.e. has no flow out of it).
 * The code attempts to make the flow from a vertex deterministic, basd on the local geometry of the
 * faces (e.g. not influenced by the ordering of the faces or channels around the vertex).
 * 
 * @author Martin Davis
 * @version 1.0
 */
@SuppressWarnings("deprecation")
public class TriangleTracer {
    private static Logger log = LoggerFactory.getLogger(TriangleTracer.class);

    public TriangleTracer() {}

    private int[] edgeIndexVar = new int[1];

    public TriangleLocation findCentroidFlowExit(WatershedTriangle tri) {
        Coordinate p = tri.findFlowExit(tri.getCentroid(), edgeIndexVar);
        if (p == null)
            return null;
        return new TriangleLocation(tri, edgeIndexVar[0], p);
    }

    /**
     * Traces flow from a point on an outflow edge of a triangle across the neighbouring triangle.
     * If the neighbouring triangle is flat and is constrained, the location on its edge will be
     * returned as the new location.
     * 
     * @param loc an interior point on the outflow edge of a triangle
     * @return null if the input is null, or the triangle has no flow & is not constrained
     */
    public TriangleLocation traceEdgeToNext(TriangleLocation loc) {
        log.trace("traceEdgeToNext: " + loc);
        // Assert: loc is a interior point on an outflow edge

        // flip to the neighbouring triangle
        loc.flip();
        WatershedTriangle tri = loc.getTriangle();

        // fell off edge of subdivision
        if (tri == null) {
            return null;
        }

        // MD - testing - surely if flow hits a constrained triangle we can exit?
        // um - maybe not
        /*
         * if (tri.isConstrained()) { return loc; }
         */

        /**
         * A triangle with no flow is ok if it is constrained Otherwise, return a null value
         */
        if (!tri.hasFlow()) {
            if (tri.isConstrained()) {
                return loc;
            }
            return null;
        }
        int edgeIndex = loc.getEdgeIndex();
        boolean isOutflowEdge = tri.isOutflowEdge(edgeIndex);
        Coordinate outflowPt = null;
        if (!isOutflowEdge) {
            // this may still be an outflow edge, due to precision issues
            outflowPt = tri.findFlowExit(loc.getCoordinate(), edgeIndex, edgeIndexVar);
            if (outflowPt == null)
                isOutflowEdge = true;
        }
        if (isOutflowEdge) {
            /**
             * Now both the original edge and the neighbour edge are known to be outflow edges. This
             * implies the edges must form a channel. The channel can be followed to an outflow
             * vertex. The flow vector indicates which vertex of the edge is the outflow vertex
             */
            return traceChannelToVertex(loc);
        }
        // flow transits triangle
        if (edgeIndexVar[0] < 0)
            return null;
        // MD - can or should we avoid making a new object here?
        return new TriangleLocation(tri, edgeIndexVar[0], outflowPt);
    }

    private LineSegment seg = new LineSegment();

    /**
     * Traces a channel edge to an outflow vertex. The flow vector indicates which vertex of the
     * edge is the outflow vertex. If the angle between the flow vector and the edge segment is
     * acute, the end of the edge segment is the outflow vertex. Otherwise, the start of the edge
     * segment is the outflow. Flat edges will have one end chosen arbitrarily as the outflow.
     * 
     * @param loc the start location to trace from, on an edge
     * @return the next location in the trickle (which will be a vertex)
     */
	private TriangleLocation traceChannelToVertex(TriangleLocation loc) {
        log.trace("traceChannelToNext: " + loc);
        WatershedTriangle tri = loc.getTriangle();
        tri.getEdgeSegment(loc.getEdgeIndex(), seg);

        // compute using height of vertex at either end of segment
        double vertexDist = (seg.p0.z > seg.p1.z) ? 1.0 : 0.0;

        return new TriangleLocation(tri, loc.getEdgeIndex(), vertexDist);
    }

    /**
     * Traces flow starting from a location which is a vertex. If the vertex is a pit, there is
     * nowhere to trace to, and this method returns <tt>null</tt>. In order to assure local
     * determinism, flow is determined by the steepest possible path exiting from the vertex.
     * 
     * @param loc the location to trace from
     * @return the next trace location, if any
     * @return null if there is no exit from this vertex
     */
    public TriangleLocation traceVertexToNext(TriangleLocation loc) {
        Coordinate startPt = loc.getCoordinate();

        log.trace("traceVertexToNext: " + loc);
        Assert.isTrue(loc.isVertex(), "expected location to be vertex");
        // WatershedDebug.watchPoint(startPt, new Coordinate(1585301.9544377453,
        // 588944.8507770868));

        // WatershedDebug.watchPoint(startPt, new Coordinate(1583621.1414373596,
        // 596863.3868938647)); // 82K001 - inconsistent with 82k002

        Collection<WatershedTriangle> adjTris = loc.findTrianglesAdjacentToVertex();

        double maxSlope = 0.0;
        TriangleLocation nextLoc = null;
        for (WatershedTriangle tri : adjTris) {
            int vertexIndex = tri.getEdgeIndex(loc.getVertex());

            // test for face flow
            Coordinate overlandExitPt = findFaceFlowExit(tri, vertexIndex);
            if (overlandExitPt != null) {
                // found face flow
                double slope = sinAng3D(startPt, overlandExitPt);
                if (slope > maxSlope) {
                    nextLoc = new TriangleLocation(
                        tri,
                        WatershedTriangle.nextIndex(vertexIndex),
                        overlandExitPt);
                    maxSlope = slope;
                }
            }

            // test for channel flow
            if (tri.isOutChannel(vertexIndex)) {
                int outVertexIndex = WatershedTriangle.nextIndex(vertexIndex);
                Coordinate outPt = tri.getCoordinate(outVertexIndex);
                double slope = sinAng3D(startPt, outPt);
                if (slope > maxSlope) {
                    nextLoc = new TriangleLocation(tri, outVertexIndex, 0.0);
                    maxSlope = slope;
                }
            }
        }

        // this will be null if there is no no exit from this vertex (it is a pit)
        return nextLoc;
    }

    /**
     * Returns the sine of the angle of elevation from the low point to the high point. This can be
     * used as a proxy for the slope.
     * 
     * @param ptHi
     * @param ptLo
     * @return
     */
    private static double sinAng3D(Coordinate ptHi, Coordinate ptLo) {
        double dx = ptHi.x - ptLo.x;
        double dy = ptHi.y - ptLo.y;
        double dz = ptHi.z - ptLo.z;
        double hyp = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double opp = dz;
        double sin = opp / hyp;
        // if (Double.isNaN(sin)) {
        // System.out.println("sin = " + sin);
        // }
        Assert.isTrue(!Double.isNaN(sin), "computed value is NaN");
        return sin;
    }

    /**
     * Finds the flow exit point on a triangle for flow which starts at a vertex of the triangle and
     * crosses the face of the triangle. (The flow can also be via a channel edge, in which case
     * this routine returns null).
     * 
     * @param tri
     * @param vertexIndex
     * @return the exit pt for facet flow
     * @return null if the flow is not via the facet or the triangle has no flow
     */
    private Coordinate findFaceFlowExit(WatershedTriangle tri, int vertexIndex) {
        if (!tri.hasFlow())
            return null;
        Coordinate vertexPt = tri.getCoordinate(vertexIndex);
        Coordinate flowVecEndPt = tri.getFlowOutsideEndPt(vertexPt);
        int oppEdgeIndex = WatershedTriangle.nextIndex(vertexIndex);
        Coordinate exitPt = tri.findEdgeIntersection(vertexPt, flowVecEndPt, oppEdgeIndex);
        return exitPt;
    }

}