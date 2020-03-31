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
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.triangulate.ConstraintVertex;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import net.refractions.chyf.watershed.WatershedSettings;
import net.refractions.chyf.watershed.hydrograph.HydroNode;

/**
 * <p>
 * Company: Vivid Solutions Inc.
 * </p>
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class WatershedVertex extends ConstraintVertex {
    /**
     * Tests whether an array of {@link WatershedVertex}es are all nodes.
     * 
     * @param vert an array of WatershedVertexes
     * @return true if all the vertices are nodes
     */
    public static boolean isAllNodes(Vertex[] vert) {
        for (int i = 0; i < vert.length; i++) {
            if (!((WatershedVertex) vert[i]).isNode())
                return false;
        }
        return true;
    }

    private boolean   isRespected      = false;
    private double    height;
    
    // EXPERIMENTAL: closestVertexFinder
    //private Coordinate closestCoordinate = null;
    private Vertex    closestVertex    = null;
    
    private HydroNode hydroNode        = null;
    // private boolean isNode = false;
    private boolean   isOnHull         = false;
    // private boolean isStartNodeAdjacent = false; // MD - not used?
    private boolean   isBoundaryNode   = false;
    private boolean   isScannedForNode = false;
    private Region    region           = null;

    // private double rndPerturb = Math.random();

    public WatershedVertex(Coordinate p) {
        super(p);
        // cache the height, in case the coordinate height is changed later
        height = p.getZ();

        // if (p.distance(new Coordinate(1269456.9353029, 1626261.41909555)) < .001) {
        // System.out.println(p);
        // }
    }

    protected void merge(ConstraintVertex v) {
        super.merge(v);
        WatershedVertex wv = (WatershedVertex) v;
        if (wv.isRespected)
            isRespected = true;
        if (region == null)
            region = wv.region;
    }

    /**
     * Tests whether this vertex should be respected by the final bounday linework.
     * 
     * @return true if the vertex should be respected
     */
    public boolean isRespected() {
        return isRespected;
    }

    public void setRespected(boolean isRespected) {
        this.isRespected = isRespected;
    }

    public double getHeight() {
        // if (getCoordinate().distance(new Coordinate(1252875.2011137602, 1630135.5023186803)) < 1)
        // System.out.println(getCoordinate());

        if (isOnConstraint())
            return 0.0;

        // DEM height
        // return getCoordinate().z;

        // medial height
        // return getBubbleBias();

        // DEM Height + Medial Height
        return WatershedSettings.BUBBLE_BIAS_FACTOR * getBubbleBias() + height;

        // random displacement
        // return getCoordinate().z + 2.0 * (Math.random() - 0.5);
    }

    private double getBubbleBias() {
        if (closestVertex == null)
            return 0.0;
        double dist = closestVertex.getCoordinate().distance(getCoordinate());
        // offset distance by one to ensure log is non-negative
        return Math.log(1.0 + dist);
    }

    // EXPERIMENTAL: closestVertexFinder
//    private double getBubbleBias() {
//        if (closestCoordinate == null)
//            return 0.0;
//        double dist = closestCoordinate.distance(getCoordinate());
//        // offset distance by one to ensure log is non-negative
//        return Math.log(1.0 + dist);
//    }

    
    public Coordinate getCoordinateWithHeight() {
        Coordinate p = getCoordinate();
        p.setZ(getHeight());
        return p;
    }

 // EXPERIMENTAL: closestVertexFinder
    public void setOnConstraint(boolean isOnConstraint) {
        super.setOnConstraint(isOnConstraint);
        if (isOnConstraint)
            closestVertex = this;
        else
            closestVertex = null;
    }

    // ===============================
    // Used for medial Axis computation

    public void setClosestVertex(Vertex v) {
        closestVertex = v;
    }

    public Vertex getClosestVertex() {
        return closestVertex;
    }

    // EXPERIMENTAL: closestVertexFinder
//    public void setClosestCoordinate(Coordinate c) {
//		 closestCoordinate = c;
//	}
//	
//	public Coordinate getClosestCoordinate() {
//	  return closestCoordinate;
//	}

    public HydroNode getNode() {
        return hydroNode;
    }

    public void setNode(HydroNode hydroNode) {
        this.hydroNode = hydroNode;
    }

    public boolean isNode() {
        return hydroNode != null;
    }

    public Collection<HydroEdge> getNodeEdges() {
        return hydroNode.getEdges();
    }

    public boolean isOnHull() {
        return isOnHull;
    }

    public void setOnHull(boolean isOnHull) {
        this.isOnHull = isOnHull;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public Region getRegion() {
        return region;
    }

    /**
     * Gets the constraint {@link HydroEdge} between this node and another node (if any). Assumes
     * that this vertex is a node.
     * 
     * @param node1 the other node to test
     * @return
     * @return null if there is no constraint between this node and the other
     */
    public HydroEdge getNodeNodeConstraint(WatershedVertex node1) {
        Coordinate p0 = getCoordinate();
        Coordinate p1 = node1.getCoordinate();

        Collection<HydroEdge> hydroEdges = getNodeEdges();
        for (HydroEdge hydroEdge: hydroEdges) {
            LineString line = hydroEdge.getLine();
            if (line.getNumPoints() != 2)
                continue;

            Coordinate line0 = line.getCoordinateN(0);
            Coordinate line1 = line.getCoordinateN(1);
            if ((p0.equals2D(line0) && p1.equals2D(line1))
                    || (p0.equals2D(line1) && p1.equals2D(line0)))
                return hydroEdge;
        }
        return null;
    }

    /**
     * Determines whether this vertex has a unique constraint region. A vertex has a unique region
     * if it is a constraint vertex and either it is not a node or all hydro edges incident on the
     * node have the same ID.
     * 
     * @return true if the region for this node is uniquely determined by the constraints on it
     */
    public boolean hasUniqueConstraintRegion() {
        if (!isOnConstraint())
            return false;

        // if on a constraint and not a node, region must be unique
        if (!isNode())
            return true;

        // if this is not a boundary node the region is unique
        if (!hydroNode.isBoundaryNode())
            return true;

        return false;
    }

    /**
     * Gets the LineString for the hydro edge this vertex lies on (if any, and if it is unique).
     * Assumes that this vertex does lie on a hydro edge. If this vertex is a hydro node, a single
     * one of the incident lines will be returned.
     * 
     * @return the hydro edge line this vertex lies on, if any
     */
    public LineString getConstraintLine() {
        HydroEdge he = (HydroEdge) getConstraint();
        return he.getLine();
    }

    /**
     * Gets a Collection of all the constraints incident on this vertex (if any)
     * 
     * @return a Collection of HydroEdges
     */
    public Collection<HydroEdge> getConstraints() {
        if (isNode())
            return getNodeEdges();
        List<HydroEdge> list = new ArrayList<HydroEdge>();
        if (isOnConstraint())
            list.add((HydroEdge)getConstraint());
        return list;
    }

    // =================================
    // Methods for data to support watershed boundary extraction

    /**
     * Sets whether this vertex is a node of an extracted watershed boundary edge
     * 
     * @param isBoundaryNode
     */
    public void setBoundaryNode(boolean isBoundaryNode) {
        this.isBoundaryNode = isBoundaryNode;
    }

    /**
     * Tests whether this vertex is a node of an extracted watershed boundary edge. Nodes may be
     * Hydro Graph nodes (but not all hydro graph nodes are necessarily boundary nodes, since a
     * hydro node may have all incident edges in the same drainage area). Some vertices will be
     * boundary nodes but not Hydro nodes, at points where boundaries meet at high points on the
     * surface.
     * 
     * @return true if this is a watershed boundary node
     */
    public boolean isBoundaryNode() {
        return isBoundaryNode;
    }

    public void setScannedForBoundaryNode(boolean isScannedForNode) {
        this.isScannedForNode = isScannedForNode;
    }

    public boolean isScannedForBoundaryNode() {
        return isScannedForNode;
    }

}