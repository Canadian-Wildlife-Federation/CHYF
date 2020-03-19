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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.algorithm.LineIntersector;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.algorithm.RobustLineIntersector;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.Triangle;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeTriangle;
import org.locationtech.jts.triangulate.quadedge.TriangleVisitor;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import net.refractions.chyf.util.VectorMath;
import net.refractions.chyf.watershed.WatershedSettings;
import net.refractions.chyf.watershed.hydrograph.HydroNode;

/**
 * A triangle used to define watersheds. The edges of the triangle is stored in CCW order, as
 * created by {@link TriangleVisitor}. This means that the interior of the triangle is on the left
 * side of its edges.
 * 
 * @author Martin Davis
 * @version 1.0
 */
@SuppressWarnings("deprecation")
public class WatershedTriangle extends QuadEdgeTriangle implements Comparable<WatershedTriangle> {
    public static void saveInitialRegionIDs(Collection<WatershedTriangle> tris) {
        for (WatershedTriangle tri : tris) {
            tri.saveInitialRegionID();
        }
    }

    public static final double HEIGHT_UNKNOWN      = -999;

    public static final int    FLOW_OUT            = 1;
    public static final int    FLOW_IN             = 2;
    public static final int    FLOW_PARALLEL       = 3;
    public static final int    FLOW_UNKNOWN        = 4;

    private WatershedTIN       watershedTIN;

    // values dependent on vertex height
    private double             minHeight;
    private double             maxHeight;
    private Coordinate         normal              = null;
    private boolean[]          isOutflowEdge;
    private Coordinate         flowVector          = null;

    // the ID of the draining hydro edge
    private Region             region              = null;
    private int                initialRegionID     = 0;

    // indicates whether this tri is adjacent to a constraint
    private boolean            isConstrained       = false;
    // indicates that the boundary edge adjacent to this triangle has been extracted
    private boolean            isBoundaryExtracted = false;

    private boolean            isConnected         = false;
    private double             trickleDistance     = 0.0;

    /**
     * Indicates whether this triangle is inside a polygonal waterbody
     */
    private boolean            isWater             = false;

    /**
     * Creates a new triangle in a Watershed TIN. Links the associated QuadEdges to itself via their
     * data references.
     * 
     * @param watershedTIN the TIN containing this triangle
     * @param edge the edges of the triangle
     */
    public WatershedTriangle(WatershedTIN watershedTIN, QuadEdge[] edge) {
        super(edge);
        this.watershedTIN = watershedTIN;
        for (int i = 0; i < 3; i++) {
            edge[i].setData(this);
        }
        updateHeight();
    }

    public void setWater(boolean isWater) {
        this.isWater = isWater;
    }
    public boolean isWater() {
        return isWater;
    }

    public boolean isConnected() {
        return isConnected;
    }
    public void setConnected(boolean isConnected) {
        this.isConnected = isConnected;
    }

    public double getTrickleDistance() {
        return trickleDistance;
    }
    public void setTrickleDistance(double distance) {
        trickleDistance = distance;
    }

    public boolean isBoundaryExtracted() {
        return isBoundaryExtracted;
    }
    public void setBoundaryExtracted(boolean isBoundaryExtracted) {
        this.isBoundaryExtracted = isBoundaryExtracted;
    }

    private double enclosingCircleDiameter() {
        double diam = 2 * Triangle.longestSideLength(getEdge(0).orig().getCoordinate(), getEdge(1)
            .orig()
            .getCoordinate(), getEdge(2).orig().getCoordinate());
        return diam;
    }

    public void updateHeight() {
        computeHeight();
        computeNormal();
        computeFlowVector();
        computeOutflowEdges();
    }

    private void computeHeight() {
        QuadEdge[] edges = getEdges();
        maxHeight = Double.MIN_VALUE;
        minHeight = Double.MAX_VALUE;
        for (int i = 0; i < edges.length; i++) {
            WatershedVertex v = (WatershedVertex) edges[i].orig();
            double h = v.getHeight();
            if (Double.isNaN(h))
                continue;
            if (h < minHeight)
                minHeight = h;
            if (h > maxHeight)
                maxHeight = h;
        }
        if (minHeight == Double.MAX_VALUE)
            minHeight = HEIGHT_UNKNOWN;
        if (maxHeight == Double.MIN_VALUE)
            maxHeight = HEIGHT_UNKNOWN;
    }

    private void computeOutflowEdges() {
        // System.out.println(getGeometry(new GeometryFactory()));
        Coordinate flowDir = getFlowDirection();
        if (flowDir == null)
            return;
        isOutflowEdge = new boolean[3];
        for (int i = 0; i < 3; i++) {
            isOutflowEdge[i] = isOutflowEdge(i, flowDir);
        }
    }

    private boolean isOutflowEdge(int edgeIndex, Coordinate flowDir) {
        Coordinate edgePt0 = getEdge(edgeIndex).orig().getCoordinate();
        Coordinate edgePt1 = getEdge(edgeIndex).dest().getCoordinate();
        double len = edgePt0.distance(edgePt1);
        Coordinate flowPt = new Coordinate(edgePt0.x + len * flowDir.x, edgePt0.y + len * flowDir.y);
        // since tri edges are CCW, the flow vector flows out iff it is CW of an edge
        //boolean isOutflow = CGAlgorithms.computeOrientation(edgePt0, edgePt1, flowPt) == CGAlgorithms.CLOCKWISE;
        boolean isOutflow = Orientation.index(edgePt0, edgePt1, flowPt) == Orientation.CLOCKWISE;

        return isOutflow;
    }

    public boolean isOutflowEdge(int edgeIndex) {
        return isOutflowEdge[edgeIndex];
    }

    public boolean isOutflowEdge(int edgeIndex, double angleTolerance) {
        boolean isOutflow = (getFlowType(edgeIndex, angleTolerance) == FLOW_OUT);
        // if (isOutflow)
        // Assert.isTrue(isOutflowEdge(edgeIndex));
        return isOutflow;
    }

    public boolean isInflowEdge(int edgeIndex, double angleTolerance) {
        return getFlowType(edgeIndex, angleTolerance) == FLOW_IN;
    }

    public int getFlowType(int edgeIndex, double angleTolerance) {
        Coordinate flowDir = getFlowDirection();
        if (flowDir == null)
            return FLOW_UNKNOWN;

        Coordinate edgePt0 = getEdge(edgeIndex).orig().getCoordinate();
        Coordinate edgePt1 = getEdge(edgeIndex).dest().getCoordinate();
        double len = edgePt0.distance(edgePt1);
        Coordinate flowDest = new Coordinate(edgePt0.x + len * flowDir.x, edgePt0.y + len
                * flowDir.y);
        // since tri edges are CCW, the flow vector flows out iff it is CW of an edge
        boolean isOutflow = Orientation.index(edgePt0, edgePt1, flowDest) == Orientation.CLOCKWISE;
        int flowType = isOutflow ? FLOW_OUT : FLOW_IN;

        double angBetween = Angle.angleBetween(edgePt0, edgePt1, flowDest);
        if (angBetween < angleTolerance || (angBetween + angleTolerance) > Math.PI)
            flowType = FLOW_PARALLEL;
        return flowType;
    }

    public int numOutflowEdges() {
        int outflowCount = 0;
        for (int i = 0; i < 3; i++) {
            if (isOutflowEdge[i])
                outflowCount++;
        }
        return outflowCount;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public Region getRegion() {
        return region;
    }

    public int getInitialRegionID() {
        return initialRegionID;
    }

    public void saveInitialRegionID() {
        if (region != null)
            initialRegionID = region.getID();
        else
            initialRegionID = Region.NULL_ID;
    }

    public double getHeight() {
        return minHeight;
    }

    public double getMaxHeight() {
        return maxHeight;
    }

    public double getMinHeight() {
        return minHeight;
    }

    public static boolean isValidHeight(double h) {
        return h > HEIGHT_UNKNOWN;
    }

    /**
     * Tests whether this triangle is flat and at the constraint height (in other words, a triangle
     * which is completely constrained by its vertices so that it will never be "bubbled").
     * 
     * @return true if the triangle is flat and at the constraint height.
     */
    public boolean isFlatAtConstraintHeight() {
        if (minHeight != WatershedSettings.CONSTRAINT_HEIGHT)
            return false;
        if (maxHeight != WatershedSettings.CONSTRAINT_HEIGHT)
            return false;
        return true;
    }

    public boolean isDrained() {
        if (region == null)
            return false;
        return region.isDrained();
    }

    public boolean hasRegion() {
        return region != null;
    }

    public int compareTo(WatershedTriangle tri) {
        if (tri.minHeight > minHeight)
            return -1;
        if (tri.minHeight < minHeight)
            return 1;
        return 0;
    }

    /**
     * Determines the triangle region based on the regions of the vertices (if possible). If not
     * possible to assign, region is left as null.
     */
    public void initRegion() {
        // don't overwrite an existing region
        if (region != null)
            return;
        Region constraintRegion = findRegion();
        if (constraintRegion != null) {
            isConstrained = true;
            region = constraintRegion;
            region.add(this);
        }
    }

    public boolean isConstrained() {
        return isConstrained;
    }

    /**
     * Finds the constraint region for this triangle, if it has one. A triangle has a constraint
     * region if:
     * <ul>
     * <li>It is edge-ajacent to a hydro constraint
     * <li>It is edge-adjacent to the border of the TIN
     * <li>The region can be determined uniquely from the vertex constraints. This is the case if
     * all the constraints are nodes and they all have the single same region for all the incident
     * edges. (This case can happen for triangles which lie in water areas)
     * </ul>
     * 
     * @return the region of the constraint, if this triangle is constrained
     * @return null if this triangle is not constrained
     */
    private Region findRegion() {
        // if (contains(new Coordinate(1585481.2182355395, 587664.1823320311)))
        // Debug.println(this);

        Region reg = findConstraintRegion();
        if (reg != null)
            return reg;

        // if region is still null, and triangle is on edge of subdivision, set Region to EXTERIOR
        if (isBorder())
            return watershedTIN.getRegionFactory().getBorder();
        return null;
    }

    /**
     * Finds the constraint region for this triangle, if any. The constraint region is determined by
     * examining the vertices of the triangle. Certain configurations of regions on the vertices
     * determine the region of this triangle directly.
     * 
     * @return the Region of an adjacent constraint
     * @return null if this triangle's region cannot be determined directly
     */
    private Region findConstraintRegion() {
        for (int i = 0; i < 3; i++) {
            WatershedVertex v = (WatershedVertex) getEdge(i).orig();
            WatershedVertex v2 = (WatershedVertex) getEdge(i).dest();

            /**
             * A constraint vertex with a unique region determines the region directly
             */
            if (v.hasUniqueConstraintRegion())
                return v.getRegion();

            /**
             * If the two endpoints of a side are both nodes and there is a constraint edge that
             * links them, can get the constraint region from it
             */
            if (v.isNode() && v2.isNode()) {
                HydroEdge e = v.getNodeNodeConstraint(v2);
                if (e != null)
                    return watershedTIN.getRegionFactory().getRegion(e.getDrainageID());
            }
        }
        /**
         * It can happen that all vertices are nodes, but there is no edge of the triangle which is
         * a constraint edge. (E.g. between an island at the mouth of a bay and the bay edges) If
         * the nodes all have the same region, the triange region is determined.
         */
        Region commonNodeRegion = findNodeUniqueCommonRegion();
        if (commonNodeRegion != null)
            return commonNodeRegion;

        return null;
    }

    private WatershedVertex[] getWatershedVertices() {
        Vertex[] vert = getVertices();
        WatershedVertex[] wv = new WatershedVertex[3];
        for (int i = 0; i < 3; i++) {
            wv[i] = (WatershedVertex) vert[i];
        }
        return wv;
    }

    /**
     * If the vertices of this triangle are all nodes, all have a single region, and it is the same
     * region for all vertices, return the region.
     * 
     * @return the unique common region
     * @return null if some vertex has more than one region, or two vertices do not share a common
     *         region
     */
    private Region findNodeUniqueCommonRegion() {
        WatershedVertex[] wv = getWatershedVertices();

        /**
         * Can only determine region for triangle where all vertices are constrained
         */
        if (!WatershedVertex.isAllNodes(wv)) {
            return null;
            // Assert.shouldNeverReachHere("Flat triangle with non-node vertices found: " + tri);
        }
        Region commonRegion = findUniqueCommonRegion(wv);
        return commonRegion;
    }

    /**
     * If the given vertices all have a single region and it is common to all, return the region.
     * 
     * @param wv
     * @return the unique common region
     * @return null if some vertex has more than one region, or two vertices do not share a common
     *         region
     */
    private Region findUniqueCommonRegion(WatershedVertex[] wv) {
        int commonRegionID = WatershedVertexUtil.findCommonRegion(wv);
        if (commonRegionID == Region.NULL_ID)
            return null;
        return watershedTIN.getRegionFactory().getRegion(commonRegionID);
    }

    /**
     * Gets the region for the edge indicated by edgeIndex, if it is a constraint edge.
     * 
     * @param edgeIndex
     * @return the constraint region
     * @return null if this is not a constraint edge
     */
    public Region getConstraintRegion(int edgeIndex) {
        // check if edge is a border edge
        if (isBorder(edgeIndex))
            return watershedTIN.getRegionFactory().getBorder();

        WatershedVertex vo = (WatershedVertex) getEdge(edgeIndex).orig();
        Region region = vo.getRegion();

        WatershedVertex vd = (WatershedVertex) getEdge(edgeIndex).orig();
        Region vdReg = vd.getRegion();
        if (vdReg != null) {
            if (region == null || !vd.isNode())
                region = vdReg;
        }
        return region;
    }

    public Coordinate getCentroid() {
        return Triangle.centroid(getCoordinate(0), getCoordinate(1), getCoordinate(2));
    }

    /**
     * Computes the normal vector to a triangle with Z values. The projection of the normal vector
     * on the XY plane gives the direction of steepest descent.
     * 
     * @return the normal vector (as a Coordinate)
     * @return null if the triangle does not have full Z information
     */
    public void computeNormal() {
        normal = VectorMath.normalToTriangle(
            ((WatershedVertex) getVertex(0)).getCoordinateWithHeight(),
            ((WatershedVertex) getVertex(1)).getCoordinateWithHeight(),
            ((WatershedVertex) getVertex(2)).getCoordinateWithHeight());
        if (Double.isNaN(normal.x))
            normal = null;
    }

    // NW, 60 degrees
    // private static final Coordinate sunAngle = new Coordinate(-0.35355, 0.35355, 0.866);

    // 45 degrees up in the NW
    private static final Coordinate sunAngle = new Coordinate(-0.5, 0.5, 0.7071);
    // private static final Coordinate sunAngle = new Coordinate(0, 0.7071, 0.7071);
    // private static final Coordinate sunAngle2D = new Coordinate(0, 1, 0.0);

    public double getHillshadeIndex() {
        return hillshadeIndex(normal);
    }

    private static double hillshadeIndex(Coordinate normal) {
        if (normal == null)
            return 0;

        double dotProd = VectorMath.dotProduct(normal, sunAngle);

        // double dotProd2D = sunAngle.x * normal.x + sunAngle.y * normal.y;
        // double index = Math.acos(dotProd2D);

        // dotprod has range [-1, 1] - map to [0.0, 1]
        double index = 1 - ((dotProd + 1) / 2);
        return index;
    }

    // private static double testIndex3 = hillshadeIndex(sunAngle);
    // private static double testIndex2 = hillshadeIndex(new Coordinate(0, -0.7071, 0.7071));
    // private static double testIndex = hillshadeIndex(new Coordinate(0.7071, 0, 0.7071));

    /**
     * Gets the vector in the direction of flow with length 1.
     * 
     * @return the flow direction vector
     * @return null if flow direction is undefined
     */
    public Coordinate getFlowDirection() {
        return flowVector;
    }

	private void computeFlowVector() {
        if (normal == null)
            return;

        double projLen = Math.sqrt(normal.x * normal.x + normal.y * normal.y);
        // triangle is flat
        if (projLen == 0.0)
            return;

        flowVector = new Coordinate(normal);
        flowVector.x /= projLen;
        flowVector.y /= projLen;
        // if (Double.isNaN(flowVector.x)) {
        // Coordinate n = getNormal();
        // System.out.println(flowVector);
        // }
        flowVector.z = 0.0;
    }

    public double getFlowAngle() {
        return Angle.angle(new Coordinate(0, 0), getFlowDirection());
    }

    public boolean hasFlow() {
        return getFlowDirection() != null;
    }

    public WatershedTriangle getTransOutflowTriangle(int i) {
        if (!isOutflowEdge(i))
            return null;

        QuadEdge qe = getEdge(i);
        QuadEdge sym = qe.sym();
        WatershedTriangle adjTri = (WatershedTriangle) sym.getData();
        if (adjTri == null)
            return null;
        if (!adjTri.hasFlow())
            return null;

        int adjIndex = adjTri.getEdgeIndex(sym);
        // adj edge must be inflow
        if (adjTri.isOutflowEdge(adjIndex))
            return null;
        return adjTri;
    }

    /**
     * If edge i has flow across it (e.g. outflow from this triangle and inflow into the adjacent
     * one), finds the adjacent triangle.
     * 
     * @param i the edge index
     * @param angleTol the angle tolerance
     * @return the adjacent triangle
     * @return null if the edge is not outflow or no neighbour exists
     */
    public WatershedTriangle getTransOutflowTriangle(int i, double angleTol) {
        if (!isOutflowEdge(i, angleTol))
            return null;

        QuadEdge qe = getEdge(i);
        QuadEdge sym = qe.sym();
        WatershedTriangle adjTri = (WatershedTriangle) sym.getData();
        if (adjTri == null)
            return null;
        if (!adjTri.hasFlow())
            return null;

        int adjIndex = adjTri.getEdgeIndex(sym);
        // adj edge must be inflow
        if (adjTri.isInflowEdge(adjIndex, angleTol))
            return adjTri;
        return null;
    }

    /**
     * Tests whether a triangle has flow from one region to another. This happens across an outflow
     * edge when the triangle adjacent to the that edge has an inflow edge and has a different
     * region. The triangle region must be assigned. This can only be the case for one edge of the
     * triangle.
     * 
     * @return true if the triangle has flow from one region to another
     */
    public boolean hasInterRegionTransOutflow(double angTol) {
        if (!hasRegion())
            return false;
        for (int i = 0; i < 3; i++) {
            if (hasInterRegionTransOutflow(i, angTol))
                return true;
        }
        return false;
    }

    public boolean hasInterRegionTransOutflow(int i, double angTol) {
        WatershedTriangle adjTri = getTransOutflowTriangle(i, angTol);
        if (adjTri == null)
            return false;
        if (getRegion() == adjTri.getRegion())
            return false;
        return true;
    }

    /**
     * Gets the endpoint of a flow ray which is guaranteed to terminate outside outside the
     * triangle.
     * 
     * @param startPt
     * @return
     */
    public Coordinate getFlowOutsideEndPt(Coordinate startPt) {
        Coordinate flowVec = getFlowDirection();
        if (flowVec == null)
            return null;
        double rayLen = enclosingCircleDiameter();
        Coordinate endPt = new Coordinate(startPt.x + rayLen * flowVec.x, startPt.y + rayLen
                * flowVec.y);
        return endPt;
    }

    /**
     * Gets the endpoint of a flow ray which is guaranteed to be outside the triangle.
     * 
     * @param startPt
     * @return
     */
    public Coordinate getFlowOutsideStartPt(Coordinate endPt) {
        Coordinate flowVec = getFlowDirection();
        if (flowVec == null)
            return null;
        double rayLen = enclosingCircleDiameter();
        Coordinate startPt = new Coordinate(endPt.x - rayLen * flowVec.x, endPt.y - rayLen
                * flowVec.y);
        return startPt;
    }

    public Geometry getSimpleFlowIndicator(GeometryFactory fact) {
        Coordinate centroid = getCentroid();
        double scale = 20;
        Coordinate centroidNormal = new Coordinate(centroid.x + scale * normal.x, centroid.y
                + scale * normal.y);

        if (Double.isNaN(centroidNormal.x))
            return null;
        return fact.createLineString(new Coordinate[]{centroid, centroidNormal});
    }

    public Geometry getCentroidEdgeFlowIndicator(GeometryFactory fact) {
        Coordinate centroid = getCentroid();
        int[] edgeIndex = new int[1];
        Coordinate exitPt = findFlowExit(centroid, edgeIndex);

        // can happen if triangle does not have Z info
        if (exitPt == null)
            return null;

        return fact.createLineString(new Coordinate[]{centroid, exitPt});
    }

    /**
     * Finds the exit edge and point for an arbitrary coordinate. If the coordinate is known to be
     * on an edge, the other findFlowExit method should be used.
     * 
     * @param startPt the point to start trickling from
     * @param outEdgeIndex a var param to receive the index of the exit edge (if any; -1 otherwise)
     * @return the exit point on the given
     * @return null if no exit edge could be found (can happen if triangle does not have a defined
     *         flow direction)
     * @return
     */
    public Coordinate findFlowExit(Coordinate startPt, int[] outEdgeIndex) {
        return findFlowExit(startPt, -1, outEdgeIndex);
    }

    /**
     * Finds the exit edge and point for a trickle starting at startPt. StartPt may be on an edge,
     * or somewhere else.
     * <p>
     * This routine may return <code>null</code> for the exit point if
     * <ul>
     * <li>the triangle does not have a defined flow direction
     * <li>the flow is parallel to an edge
     * <li>the start point is already an exit point (this will happen if it lies on an outflow
     * edge, and due to precision issues may also happen if it lies slightly outside a non-outflow
     * edge)
     * </ul>
     * Clients must be prepared to handle spurious null exits caused by precision issues.
     * 
     * @param startPt the point to start trickling from
     * @param startEdgeIndex the index of the edge the start point is on (or -1 if point is not on
     *            an edge)
     * @param outEdgeIndex a var param to receive the index of the exit edge (if any; -1 otherwise)
     * @return the exit point on the given
     * @return null if no exit edge could be found (can happen if triangle does not have a defined
     *         flow direction)
     */
    public Coordinate findFlowExit(Coordinate startPt, int startEdgeIndex, int[] outEdgeIndex) {
        Coordinate endPt = getFlowOutsideEndPt(startPt);
        outEdgeIndex[0] = -1;
        if (endPt == null)
            return null;

        // System.out.println(WKTWriter.toLineString(startPt, endPt ));

        for (int i = 0; i < 3; i++) {
            if (i == startEdgeIndex)
                continue;
            outEdgeIndex[0] = i;
            Coordinate exitPt = findEdgeIntersection(startPt, endPt, i);
            if (exitPt != null) {
                return exitPt;
            }
        }
        // System.out.println("Tri: " + this);
        outEdgeIndex[0] = -1;
        return null;
    }

    /**
     * Gets the intersection of a flow ray and a given triangle edge, if any. The flow ray may or
     * may not cross the edge. The order of the ray points is not significant. The intersection
     * point has the Z-value computed.
     * 
     * @param p0
     * @param p1
     * @param edgeIndex
     * @return the coordinate of the intersection point, if the segment crosses the edge
     * @return null if the segment does not cross the edge
     */
    public Coordinate findEdgeIntersection(Coordinate p0, Coordinate p1, int edgeIndex) {
        Coordinate edgePt0 = getEdge(edgeIndex).orig().getCoordinate();
        Coordinate edgePt1 = getEdge(edgeIndex).dest().getCoordinate();
        LineIntersector li = new RobustLineIntersector();
        li.computeIntersection(p0, p1, edgePt0, edgePt1);
        if (!li.hasIntersection())
            return null;
        if (li.getIntersectionNum() > 1)
        	return null;
            //Assert.shouldNeverReachHere("more than one intersection!");
        Coordinate intPt = li.getIntersection(0);
        intPt.z = Vertex.interpolateZ(intPt, edgePt0, edgePt1);
        return intPt;
    }

    /**
     * Tests whether the given edge is an channel.
     * 
     * @param edgeIndex
     * @return
     */
    public boolean isChannel(int edgeIndex) {
        QuadEdge adjEdge = getEdge(edgeIndex).sym();
        WatershedTriangle adjTri = (WatershedTriangle) adjEdge.getData();
        if (!hasFlow() || adjTri == null || !adjTri.hasFlow())
            return false;
        boolean isAdjOutflowEdge = adjTri.isOutflowEdge(adjTri.getEdgeIndex(adjEdge));
        boolean isOutflowEdge = isOutflowEdge(edgeIndex);
        return isOutflowEdge && isAdjOutflowEdge;
    }

    /**
     * Determines whether an edge is a channel with outflow in the direction of the edge (in this
     * triangle).
     * 
     * @param edgeIndex
     * @return true if the edge is a channel with outflow in the triangle edge direction
     */
    public boolean isOutChannel(int edgeIndex) {
        if (!isChannel(edgeIndex))
            return false;
        return isFlowInEdgeDirection(edgeIndex);
    }

    private LineSegment segVar = new LineSegment();

    private boolean isFlowInEdgeDirection(int edgeIndex) {
        if (isFlat(edgeIndex))
            return false;
        Coordinate flow = getFlowDirection();
        getEdgeSegment(edgeIndex, segVar);
        Coordinate flowSeg1 = new Coordinate(segVar.p0.x + flow.x, segVar.p0.y + flow.y);

        boolean isAcute = Angle.isAcute(flowSeg1, segVar.p0, segVar.p1);

        return isAcute;
    }

    public boolean isFlat(int edgeIndex) {
        double h0 = ((WatershedVertex) getVertex(edgeIndex)).getHeight();
        double h1 = ((WatershedVertex) getVertex(nextIndex(edgeIndex))).getHeight();
        return h0 == h1;
    }

    public int getLowVertexIndex(int edgeIndex) {
        int nextEdgeIndex = nextIndex(edgeIndex);
        double h0 = ((WatershedVertex) getVertex(edgeIndex)).getHeight();
        double h1 = ((WatershedVertex) getVertex(nextEdgeIndex)).getHeight();
        if (h0 < h1)
            return edgeIndex;
        return nextEdgeIndex;
    }

    public int getLowestVertexIndex() {
        double lowHgt = Double.MAX_VALUE;
        int lowIndex = -1;
        for (int i = 0; i < 3; i++) {
            double hgt = ((WatershedVertex) getVertex(i)).getHeight();
            if (hgt < lowHgt) {
                lowHgt = hgt;
                lowIndex = i;
            }
        }
        return lowIndex;
    }

    /**
     * Gets the count of {@link HydroNode}s adjacent to this triangle.
     * 
     * @return the number of HydroNodes adjacent to this triangle
     */
    public int getHydroNodeCount() {
        int count = 0;
        for (int i = 0; i < 3; i++) {
            if (((WatershedVertex) getVertex(i)).isNode())
                count++;
        }
        return count;
    }

    /**
     * Gets a {@link HydroNode} adjacent to this triangle, if any. If more than one is adjacent, the
     * first one found is returned.
     * 
     * @return a HydroNode adjacent to this triangle
     * @return null if no hydro nodes are adjacent
     */
    public HydroNode getHydroNode() {
        for (int i = 0; i < 3; i++) {
            WatershedVertex wv = (WatershedVertex) getVertex(i);
            if (wv.isNode())
                return wv.getNode();
        }
        return null;
    }

    /**
     * Gets all {@link HydroNode}s adjacent to this triangle, if any. If more than one is adjacent,
     * the first one found is returned.
     * 
     * @return a list of HydroNodes adjacent to this triangle. The list is empty if no hydro nodes
     *         are adjacent
     */
    public List<HydroNode> getHydroNodes() {
        List<HydroNode> nodes = new ArrayList<HydroNode>();
        for (int i = 0; i < 3; i++) {
            WatershedVertex wv = (WatershedVertex) getVertex(i);
            if (wv.isNode())
                nodes.add(wv.getNode());
        }
        return nodes;
    }

    public void printFlowAngles() {
        for (int i = 0; i < 3; i++) {
            Coordinate p0 = getEdge(i).orig().getCoordinate();
            Coordinate p1 = getEdge(i).dest().getCoordinate();
            Coordinate flowDir = getFlowDirection();
            Coordinate flowPt = new Coordinate(p0);
            flowPt.x += flowDir.x;
            flowPt.y += flowDir.y;
            double radAng = Angle.angleBetween(p0, p1, flowPt);
            double ang = Math.toDegrees(radAng);
            System.out.print(ang + "   ");

            if (isOutflowEdge(i) && (ang < 2 || ang > 178))
                System.out.println(" outflow angle unstable ");
        }
        System.out.println(" ");
    }

    public static class TrickleDistanceComparator implements Comparator<WatershedTriangle> {
        public int compare(WatershedTriangle tri1, WatershedTriangle tri2) {
            double dist1 = tri1.getTrickleDistance();
            double dist2 = tri2.getTrickleDistance();
            if (dist1 < dist2)
                return -1;
            if (dist1 > dist2)
                return 1;
            return 0;
        }
    }

    public static class MinHeightComparator implements Comparator<WatershedTriangle> {
        public int compare(WatershedTriangle tri1, WatershedTriangle tri2) {
            double hgt1 = tri1.getMinHeight();
            double hgt2 = tri2.getMinHeight();
            if (hgt1 < hgt2)
                return -1;
            if (hgt1 > hgt2)
                return 1;
            return 0;
        }
    }
}