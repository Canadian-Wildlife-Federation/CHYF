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
import java.util.Iterator;
import java.util.List;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.util.Debug;

import net.refractions.chyf.watershed.medialaxis.IndexedClosestConstraintVertexFinder;
import net.refractions.chyf.watershed.model.Region;
import net.refractions.chyf.watershed.model.WatershedTIN;
import net.refractions.chyf.watershed.model.WatershedTriangle;
import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Trace trickle lines (flow down lines of steepest descent) for a collection of
 * TIN triangles.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class SteepestDescentTrickler {
	public static final String TRICKLE_LINES = "TRICKLE_LINES";

	/**
	 * Currently it is found that it is not necessary to split to provide reasonable
	 * boundary linework. Not splitting does result in more disconnected areas,
	 * which need to be merged appropriately.
	 */
	private static final boolean DO_SPLITS = false;

	private WatershedTIN watershedTIN;
	private IndexedClosestConstraintVertexFinder ccf;
	private TriangleSplitter splitter;
	private Collection<WatershedTriangle> triangles;
	private List<Geometry> traceGeomList = new ArrayList<Geometry>();
	private List<Region> pitRegions = new ArrayList<Region>();

	/**
	 * Traces paths of steepest descent across a collection of triangles. Triangles
	 * may be split to increase the faceting in the TIN to better match flow
	 * patterns.
	 * 
	 * @param subdiv
	 * @param watershedTIN
	 * @param triangles
	 */
	public SteepestDescentTrickler(QuadEdgeSubdivision subdiv, WatershedTIN watershedTIN, Collection<WatershedTriangle> triangles,
			IndexedClosestConstraintVertexFinder ccf) {
		this.triangles = triangles;
		this.watershedTIN = watershedTIN;
		this.ccf = ccf;
		splitter = new TriangleSplitter(subdiv, watershedTIN);
	}

	public List<Geometry> getTraceGeometry() {
		return traceGeomList;
	}

	public List<Region> getPitRegions() {
		return pitRegions;
	}

	public void compute() {
		initEdgeAdjacentRegions(triangles);
		trace(triangles);

		computePinchSplits();

		if (DO_SPLITS)
			computeSplits();

	}

	private static void initEdgeAdjacentRegions(Collection<WatershedTriangle> triangles) {
		for (Iterator<WatershedTriangle> i = triangles.iterator(); i.hasNext();) {
			WatershedTriangle tri = i.next();
			tri.initRegion();
		}
	}

	private void trace(Collection<WatershedTriangle> triangles) {
		for (Iterator<WatershedTriangle> i = triangles.iterator(); i.hasNext();) {
			WatershedTriangle tri = i.next();

			// debugging
			// if (! tri.contains(new Coordinate(1579416.6379959013, 597093.7164891524)))
			// continue;

			if (!tri.hasRegion()) {
				traceAndAssign(tri);
			}
		}
	}

	private void traceAndAssign(WatershedTriangle tri) {
		// debugging
		// WatershedDebug.watchTriangle(tri, new Coordinate(941226.3486237368,
		// 560236.9931514971));

		TrickleTracer tracer = new TrickleTracer();
		Region destRegion = tracer.trace(tri);
		/**
		 * Notes: - this may assign a bad region in cases where a tri is adjacent to a
		 * hydro node, and the trickle ends at the node. In this case an arbitrary
		 * region around the node will be returned - which may not be a region to which
		 * the triangle is actually adjacent.
		 */

		if (Debug.isDebugging())
			traceGeomList.add(tracer.getGeometry());

		tri.setTrickleDistance(tracer.getDistance());

		// check if terminated in pit vertex
		TriangleLocation finalLoc = tracer.getTerminalLocation();
		if (finalLoc != null && finalLoc.isVertex()
		// && ! finalLoc.getVertex().isOnConstraint()
		) {
			WatershedVertex destVertex = finalLoc.getVertex();
			Region reg = getPitRegion(destVertex);
			reg.add(tri);
			return;
		}
		if (destRegion != null) {
			destRegion.add(tri);
		}

		// debugging only
		// String regionID = "NULL";
		// if (dest != null && dest.hasRegion()) regionID =
		// Integer.toString(dest.getRegion().getID());
		// DebugFeature.add(TRICKLE_LINES, tracer.getGeometry(geomFactory), regionID);

		// System.out.println(tracer);
	}

	/**
	 * Gets the pit region for a vertex which is a pit, creating a new pit region if
	 * necessary.
	 * 
	 * @param v a vertex
	 * @return the existing or new pit region for this vertex
	 */
	private Region getPitRegion(WatershedVertex v) {
		if (v.getRegion() == null) {
			Region pitRegion = watershedTIN.getRegionFactory().createRegion();
			pitRegion.setType(Region.TYPE_PIT);
			v.setRegion(pitRegion);
			pitRegions.add(pitRegion);
		}
		return v.getRegion();
	}

	private void computeSplits() {

		Collection<WatershedTriangle> splits = triangles;
		do {
			splits = split(splits);
		} while (splits.size() > 0);
	}

	private Collection<WatershedTriangle> split(Collection<WatershedTriangle> triangles) {
		List<WatershedTriangle> newSplitCandidates = new ArrayList<WatershedTriangle>();
		for (Iterator<WatershedTriangle> i = triangles.iterator(); i.hasNext();) {
			WatershedTriangle tri = i.next();
			// ignore dead triangles
			if (!tri.isLive())
				continue;

			// if triangle is constrained, region is determined immediately
			if (tri.isConstrained())
				continue;

			// this is the split condition - the triangle has outflow to two different
			// regions
			if (WatershedTIN.hasDifferentTransOutflowDestRegions(tri, WatershedTIN.FLOW_ANGLE_TOL))
				split(tri, newSplitCandidates);
		}
		return newSplitCandidates;
	}

	/**
	 * Splits triangles which are split candidates and which are adjacent to a node.
	 * This prevents watershed regions from improperly "pinching off" at hydro nodes
	 * in certain situations.
	 */
	private void computePinchSplits() {
		Collection<WatershedTriangle> splits = triangles;
		splitPinchTris(splits);
	}

	private void splitPinchTris(Collection<WatershedTriangle> triangles) {
		List<WatershedTriangle> notUsed = new ArrayList<WatershedTriangle>();
		for (Iterator<WatershedTriangle> i = triangles.iterator(); i.hasNext();) {
			WatershedTriangle tri = i.next();

			// ignore dead triangles
			if (!tri.isLive())
				continue;

			// WatershedDebug.watchTriangle(tri, new Coordinate(824657.6184363815,
			// 1259526.6291851758));
			// WatershedDebug.watchTriangle(tri, new Coordinate(840967.7928446787,
			// 1416052.305568613));

			if (!PinchTriangleFixer.isPinchTriangle(tri))
				continue;

			/**
			 * This triangle is incident on a node which does not have a region id equal to
			 * the region of the triangle computed by trickling. To fix this, split the
			 * triangle. This will increase the resolution of the TIN and hopefully allow
			 * the trickling to better reflect the hydro edge topology.
			 */
			split(tri, notUsed);
		}
	}

	// private void OLDsplitPinchTris(Collection triangles) {
	// List notUsed = new ArrayList();
	// for (Iterator i = triangles.iterator(); i.hasNext();) {
	// WatershedTriangle tri = (WatershedTriangle) i.next();
	//
	// // ignore dead triangles
	// if (!tri.isLive())
	// continue;
	//
	// WatershedDebug
	// .watchTriangle(tri, new Coordinate(824657.6184363815, 1259526.6291851758));
	// WatershedDebug.watchTriangle(tri, new Coordinate(840967.7928446787,
	// 1416052.305568613));
	//
	// // if triangle is constrained, region is determined immediately
	// if (tri.isConstrained())
	// continue;
	//
	// /**
	// * Only interested in triangles which are adjacent to hydro nodes (since this
	// is where
	// * the problem happens)
	// */
	// if (tri.getHydroNodeCount() < 1)
	// continue;
	//
	// /**
	// * Assumption #1: pinch problem is indicated by a triangle incident on a node
	// with a
	// * region which is not one of the drainage ids on the incident hydro edges.
	// Comment:
	// * this fits all cases seen so far. Assumption #2: Only one of the nodes on
	// the triangle
	// * will exhibit this problem Comment: The splitting heuristic is only
	// applicable if this
	// * is the case. No other situations have been observed. It *has* been observed
	// that a
	// * triangle may be adjacent to 2 nodes, but only one of them has the
	// inconsistent
	// * drainage ID situation.
	// */
	// Collection nodes = tri.getHydroNodes();
	// HydroNode node = HydroNode.getNodeNotAdjacentToRegion(nodes,
	// tri.getRegion().getID());
	// if (node == null)
	// continue;
	//
	// /**
	// * This triangle is incident on a node which does not have a region id equal
	// to the
	// * region of the triangle computed by trickling. To fix this, split the
	// triangle. This
	// * will increase the resolution of the TIN and hopefully allow the trickling
	// to better
	// * reflect the hydro edge topology.
	// */
	// split(tri, notUsed);
	// }
	// }

	/**
	 * Splits a trans-region outflow triangle. Triangle is assumed to have two
	 * trans-edge outflow neighbours with different regions. Adds new triangles
	 * created to a list of tris to test for further splitting
	 * 
	 * @param tri                the triangle to split
	 * @param newSplitCandidates the list of new triangles resulting from splits
	 * @return true if the triangle was split
	 */
	private boolean split(WatershedTriangle tri, List<WatershedTriangle> newSplitCandidates) {

		WatershedTriangle[] splitTri = splitter.split(tri, WatershedTIN.FLOW_ANGLE_TOL);
		// result is null if split could not happen for geometric quality reasons
		if (splitTri == null) {
			return false;
		}
		updateClosestConstraintVertex(splitTri);

		// update heights for triangles to reflect newly found closest constraint
		// vertices
		for (int j = 0; j < splitTri.length; j++) {
			if (splitTri[j] == null)
				continue;
			splitTri[j].updateHeight();
		}
		// trickle each triangle
		for (int j = 0; j < splitTri.length; j++) {
			// must re-init the region, since a new tri may be constrained
			splitTri[j].initRegion();
			if (!splitTri[j].hasRegion()) {
				// assign each new triangle
				traceAndAssign(splitTri[j]);
			}
			// add to new list for splitting
			newSplitCandidates.add(splitTri[j]);
		}
		return true;
	}

	private void updateClosestConstraintVertex(WatershedTriangle[] tris) {
		// MD - may need to add new vertices to ccf index
		Collection<QuadEdge> quadEdges = extractEdges(tris);
		ccf.assignClosest(quadEdges);
	}

	private static List<QuadEdge> extractEdges(WatershedTriangle[] tris) {
		List<QuadEdge> quadEdges = new ArrayList<QuadEdge>();
		for (int i = 0; i < tris.length; i++) {
			for (int j = 0; j < 3; j++) {
				quadEdges.add(tris[i].getEdge(j));
			}
		}
		return quadEdges;
	}

}