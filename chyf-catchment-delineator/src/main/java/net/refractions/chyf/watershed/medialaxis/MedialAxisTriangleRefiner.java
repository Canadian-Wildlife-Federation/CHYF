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

package net.refractions.chyf.watershed.medialaxis;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;

import net.refractions.chyf.watershed.QuadedgeConstraintFinder;
import net.refractions.chyf.watershed.debug.WatershedDebug;
import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.OrientedHydroEdge;
import net.refractions.chyf.watershed.model.WatershedTIN;
import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Refines certain configurations of single triangles to better reflect the
 * medial axis. Cases include:
 * <ul>
 * <li>Triangles with all vertices being nodes. These triangles will be flat and
 * be unable to be region-assigned. They can be split at their incentre to allow
 * them to be trickled. *
 * </ul>
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class MedialAxisTriangleRefiner {
	public static void refine(WatershedTIN watershedTIN) {
		MedialAxisTriangleRefiner mar = new MedialAxisTriangleRefiner(watershedTIN);
		mar.compute();
	}

	// ============= Instance variables ==============================

	private WatershedTIN watershedTIN;
	private QuadEdgeSubdivision subdiv;

	public MedialAxisTriangleRefiner(WatershedTIN watershedTIN) {
		this.watershedTIN = watershedTIN;
		this.subdiv = watershedTIN.getSubdivision();
	}

	/**
	 * Refines any triangles which have specifiic configurations.
	 */
	private void compute() {
		@SuppressWarnings("unchecked")
		List<QuadEdge[]> tris = subdiv.getTriangleEdges(false);
		for (QuadEdge[] triEdge : tris) {
			refine(triEdge);
		}
	}

	private void refine(QuadEdge[] triEdge) {
		WatershedDebug.watchTriangle(triEdge, new Coordinate(1231550.825774939, 1306269.231518659));

		// don't bother splitting tris on the border
		if (hasBorderVertex(triEdge))
			return;

		int nodeCount = nodeCount(triEdge);

		boolean split = false;
		/**
		 * Check for a triangle with nodes at all vertices. These triangles will be flat
		 * and be unable to be region-assigned. They can be split at their incentre to
		 * allow them to be trickled.
		 */
		if (nodeCount == 3)
			split = true;

		// check for (rare) constraint-vertex case
		if (nodeCount == 2 && isCrossesBetweenConstraintAndOtherRegion(triEdge)) {
			split = true;
			// System.out.println("isCrossesBetweenConstraintAndOtherRegion: " +
			// QuadEdgeTriangle.toPolygon(triEdge));
		}

		if (split) {
			MedialAxisUtil.splitTriangleAtCentre(watershedTIN, triEdge);
		}
	}

	private static int nodeCount(QuadEdge[] triEdge) {
		int count = 0;
		for (int i = 0; i < 3; i++) {
			WatershedVertex wv = (WatershedVertex) triEdge[i].orig();
			if (wv.isNode())
				count++;
		}
		return count;
	}

	/**
	 * Tests whether any of the quadedges touch a convex hull vertex
	 * 
	 * @param e
	 * @return
	 */
	private static boolean hasBorderVertex(QuadEdge[] e) {
		for (int i = 0; i < 3; i++) {
			WatershedVertex v = (WatershedVertex) e[i].orig();
			if (v.isOnHull())
				return true;
		}
		return false;
	}

	/**
	 * Test for a triangle which has a constraint as one edge and has the remaining
	 * vertex in a different region than the edge. Assumes that the triangle has
	 * been determined to contain exactly 2 nodes.
	 * 
	 * @param triEdge the triangle to check
	 * @return true if the triangle crosses between a constraint and a vertex in
	 *         another region
	 */
	private boolean isCrossesBetweenConstraintAndOtherRegion(QuadEdge[] triEdge) {
		QuadEdge constraintQE = findNodeNodeEdge(triEdge);
		WatershedVertex v = findNonNodeVertex(triEdge);
		if (v == null)
			return false;

		OrientedHydroEdge ohe = QuadedgeConstraintFinder.getOrientedHydroEdge(constraintQE);
		// node-node edge is not be a constraint
		if (ohe == null)
			return false;

		HydroEdge he = ohe.getEdge();
		int constraintDrainID = he.getDrainageID().intValue();

		if (v.getRegion().getID() != constraintDrainID)
			return true;
		return false;
	}

	private static QuadEdge findNodeNodeEdge(QuadEdge[] triEdge) {
		for (int i = 0; i < 3; i++) {
			WatershedVertex v0 = (WatershedVertex) triEdge[i].orig();
			WatershedVertex v1 = (WatershedVertex) triEdge[i].dest();
			if (v0.isNode() && v1.isNode())
				return triEdge[i];
		}
		return null;
	}

	private static WatershedVertex findNonNodeVertex(QuadEdge[] e) {
		for (int i = 0; i < e.length; i++) {
			WatershedVertex v = (WatershedVertex) e[i].orig();
			if (!v.isNode() && v.isOnConstraint())
				return v;
		}
		return null;
	}
}