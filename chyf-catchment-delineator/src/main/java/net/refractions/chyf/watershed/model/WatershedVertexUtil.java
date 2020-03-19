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

import java.util.Collection;

import org.locationtech.jts.algorithm.Distance;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.util.Assert;

import net.refractions.chyf.watershed.WatershedSettings;

/**
 * Utilities for working with {@link WatershedVertex}es
 * 
 * @author Martin Davis
 */
public class WatershedVertexUtil {

	/**
	 * Test whether two constraint vertices lie in the same region. The vertices are
	 * assumed to lie on constraints. Vertices which are nodes may have more than
	 * one region incident on them; this case is handled correctly.
	 * 
	 * @param v0 a vertex on a constraint
	 * @param v1 a vertex on a constraint
	 * @return true if the vertices lie in the same region
	 */
	public static boolean hasCommonRegion(WatershedVertex v0, WatershedVertex v1) {
		// interior constraint vertices - can compare constraints directly
		if (!v0.isNode() && !v1.isNode())
			return v0.getRegion() == v1.getRegion();

		/**
		 * Nodes have multiple constraints incident on them. If the other vertex is not
		 * a node, can test to ensure that they do not lie on the same constraint.
		 */
		if (v0.isNode() && !v1.isNode()) {
			return hasCommonRegionNodeVertex(v0, v1);
		}
		if (v1.isNode() && !v0.isNode()) {
			return hasCommonRegionNodeVertex(v1, v0);
		}
		if (v0.isNode() && v1.isNode()) {
			return hasCommonRegionNodeNode(v0, v1);
		}
		Assert.shouldNeverReachHere("Impossible vertex/node pair configuration");
		return false;

	}

	/**
	 * Tests whether a node is adjacent to or in the same region as a constraint
	 * vertex. This is the case if some edge incident on the node has the same
	 * region as the vertex.
	 * 
	 * @param node
	 * @param v
	 * @return true if the vertex has the same region as some edge on the node
	 */
	public static boolean hasCommonRegionNodeVertex(WatershedVertex node, WatershedVertex v) {
		int regionID = v.getRegion().getID();
		return node.getNode().isAdjacentToRegion(regionID);
	}

	/**
	 * Tests whether two constraint nodes are adjacent to or in the same region.
	 * This is the case if each node is incident on (possibly different) edges which
	 * are in the same region.
	 * 
	 * @param node0 a node
	 * @param node1 a node
	 * @return true if any two incident edges are in the same region
	 */
	public static boolean hasCommonRegionNodeNode(WatershedVertex node0, WatershedVertex node1) {
		/**
		 * This method uses a brute force n^2 test, but this is reasonable because: (a)
		 * this situation should not occur very often (b) the expected number of edges
		 * on a node is small (approx 3 on average)
		 */
		Collection<HydroEdge> edges0 = node0.getNodeEdges();
		for (HydroEdge e : edges0) {
			int regionID = e.getDrainageID().intValue();
			if (node1.getNode().isAdjacentToRegion(regionID))
				return true;
		}
		return false;
	}

	/**
	 * Tests whether two constraint nodes are adjacent to at least one different
	 * region.
	 * 
	 * @param node0 a node
	 * @param node1 a node
	 * @return true if there are two incident edges which are in different regions
	 */
	public static boolean hasDifferentRegion(WatershedVertex node0, WatershedVertex node1) {
		if (hasRegionNotAdjacentTo(node0, node1) && (hasRegionNotAdjacentTo(node1, node0)))
			return true;
		return false;
	}

	/**
	 * Tests whether a constraint node is adjacent to a region which another node is
	 * not adjacent to.
	 * 
	 * @param node0 a hydro node vertex
	 * @param node1 a hydro node vertex
	 * @return true if there is an adjacent region to node0 which is not adjacent to
	 *         node1
	 */
	public static boolean hasRegionNotAdjacentTo(WatershedVertex node0, WatershedVertex node1) {
		/**
		 * This method uses a brute force n^2 test, but this is reasonable because: (a)
		 * this situation should not occur very often (b) the expected number of edges
		 * on a node is small (approx 3 on average)
		 */
		Collection<HydroEdge> edges = node0.getNodeEdges();
		for (HydroEdge e : edges) {
			int regionID = e.getDrainageID().intValue();
			if (!node1.getNode().isAdjacentToRegion(regionID))
				return true;
		}
		return false;
	}

	/**
	 * Finds the common region id (if any) for a list of node vertices.
	 * 
	 * @param node an array of node vertices
	 * @return the id of a common region (if any)
	 * @return -1 if no common region found
	 */
	public static int findCommonRegion(WatershedVertex[] node) {
		/**
		 * This method uses a brute force algorithm, but this is reasonable because: (a)
		 * this situation should not occur very often (b) the expected number of edges
		 * on a node is small (approx 3 on average)
		 */
		Collection<HydroEdge> edges0 = node[0].getNodeEdges();
		for (HydroEdge e : edges0) {
			int regionID = e.getDrainageID().intValue();
			if (isCommonToRest(node, regionID))
				return regionID;
		}
		return Region.NULL_ID;
	}

	private static boolean isCommonToRest(WatershedVertex[] node, int testRegionID) {
		for (int j = 1; j < node.length; j++) {
			if (!node[j].getNode().isAdjacentToRegion(testRegionID))
				return false;
		}
		return true;
	}

	public static int getRegionIDForEdgeLeavingNode(QuadEdge qe) {
		WatershedVertex v0 = (WatershedVertex) qe.orig();
		WatershedVertex v1 = (WatershedVertex) qe.dest();
		if (!v1.isNode())
			return v1.getRegion().getID();

		// HydroEdge he = getEdgeBetweenNodes(v0, v1);
		HydroEdge he = v0.getNodeNodeConstraint(v1);
		return he.getDrainageID().intValue();
	}

	/**
	 * Gets a {@link HydroEdge} connecting two nodes, if there is one. There may be
	 * more than one hydro edge between two nodes - which one is returned is
	 * unspecified.
	 * 
	 * @param node0 a node
	 * @param node1 a node
	 * @return a HydroEdge between the two nodes (if any)
	 * @return null if no HydroEdge connects the two nodes
	 */
	public static HydroEdge getEdgeBetweenNodes(WatershedVertex node0, WatershedVertex node1) {
		/**
		 * For two nodes, need to determine the connecting constraint edge spatially.
		 * The key fact is that if both are nodes, then both must be actual vertices of
		 * a constraint edge which connects them (since nodes are always vertices). In
		 * fact, they must both be endpoints of a connecting constraint edge.
		 */
		// MD - could this be done by checking if e was in the collection of hydro edges
		// for node1?
		// Maybe no quicker tho
		Collection<HydroEdge> edges0 = node0.getNodeEdges();
		for (HydroEdge e : edges0) {
			if (isEndPoint(node1.getCoordinate(), e.getLine())) {
				return e;
			}
		}
		return null;
	}

	private static boolean isEndPoint(Coordinate p, LineString line) {
		int nPts = line.getNumPoints();
		if (p.equals2D(line.getCoordinateN(0)))
			return true;
		if (p.equals2D(line.getCoordinateN(nPts - 1)))
			return true;
		return false;

	}
	/*
	 * public static boolean isEndSegment(Coordinate p0, Coordinate p1, LineString
	 * line) { int nPts = line.getNumPoints(); if (nPts < 2) return false; if
	 * (p0.equals2D(line.getCoordinateN(0)) && p1.equals2D(line.getCoordinateN(1)) )
	 * return true; if (p0.equals2D(line.getCoordinateN(nPts - 1)) &&
	 * p1.equals2D(line.getCoordinateN(nPts - 2)) ) return true; return false; }
	 */

	private static final double SEGMENT_TOLERANCE = WatershedSettings.SNAP_TOLERANCE / 100.0;

	public static boolean isOnSegment(Coordinate pt, Coordinate p0, Coordinate p1) {
		double dist = Distance.pointToSegment(pt, p0, p1);
		return dist < SEGMENT_TOLERANCE;
	}

}
