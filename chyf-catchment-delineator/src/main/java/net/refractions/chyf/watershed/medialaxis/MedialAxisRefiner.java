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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.locationtech.jts.util.Assert;

import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.WatershedTIN;
import net.refractions.chyf.watershed.model.WatershedVertex;
import net.refractions.chyf.watershed.model.WatershedVertexUtil;

/**
 * Refines a {@link Subdivision} containing constraints to cause it to better
 * reflect the medial axis. This is accomplished by splitting triangles which
 * cross the medial axis (i.e. have vertices on more than one constraint region)
 * so that the constraints are separated by at least two triangles.
 * <p>
 * A key issue is where the constructed split points are located - they should
 * be on or close to the medial axis. Various heuristics are used to ensure this
 * is the case.
 * <p>
 * A special case is all-node triangles. These are handled by splitting them in
 * their centre.
 * <p>
 * This routine is run after constraints have been enforced. It does not attempt
 * to maintain the Delaunay condition, and thus avoids performing triangle
 * flipping. This is important for two reasons:
 * <ul>
 * <li>it ensures that the topology of the triangle splits is not changed
 * <li>it ensures constraint edges are never deleted from the TIN
 * </ul>
 * In theory the non-Delaunay situation causes the risk of having subsequent TIN
 * point location queries fail, but in practice this is not observed.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class MedialAxisRefiner {
	public static void refine(WatershedTIN watershedTIN) {
		MedialAxisRefiner mar = new MedialAxisRefiner(watershedTIN);
		mar.compute();
	}

	// ============= Instance variables ==============================

	private int unfixedCrossingCount;

	private WatershedTIN watershedTIN;
	private QuadEdgeSubdivision subdiv;

	public MedialAxisRefiner(WatershedTIN watershedTIN) {
		this.watershedTIN = watershedTIN;
		this.subdiv = watershedTIN.getSubdivision();
	}

	/**
	 * Refines the triangulation to incorporate the medial axis approximation.
	 * Refining is iterated until no more changes take place.
	 */
	public void compute() {
		int iterCount = 0;
		boolean isChanged = false;
		do {
			isChanged = refineCrossMedialEdgesOnce();
			iterCount++;

			// MD - hack for testing
			if (iterCount > 10)
				break;

		} while (isChanged);

		if (unfixedCrossingCount > 0) {
			System.out.println("Count of triangles crossing medial axis: " + unfixedCrossingCount);
			Assert.shouldNeverReachHere("Some medial-crossing triangles remain unsplit: " + unfixedCrossingCount);
		}

		// refineAllNodeTris();
	}

	/**
	 * Perform one round of medial axis refinement.
	 * 
	 * @return true if the TIN was changed
	 */
	private boolean refineCrossMedialEdgesOnce() {
		int insertCount = 0;
		unfixedCrossingCount = 0;

		// copy edges collection, since it will be modified if edges are split
		@SuppressWarnings("unchecked")
		List<QuadEdge> edges = new ArrayList<QuadEdge>(subdiv.getEdges());
		/**
		 * Sort the edges, to make the changes to the TIN more repeatable for different
		 * input areas. The sort basis is somewhat arbitrary - length is used here, but
		 * it could be by minimum coordinate. The key aspect is that the sort should
		 * result in close edges having the same relative order. This helps to avoid
		 * choosing different triangulations between the medial points and the
		 * constraints. (Since the Delaunay condition is not enforced, edges can be
		 * "swapped" within a given quadrilateral. Another option would be to enforce
		 * Delaunay for non-constraint edges, but this might be more time-consuming. )
		 */
		Collections.sort(edges, new QuadEdgeLengthComparator());

		for (QuadEdge e : edges) {
			// don't bother splitting tris on the border
			if (hasBorderVertex(e))
				continue;

			boolean wasSplit = refineEdge(e);
			if (wasSplit)
				insertCount++;
		}

		boolean isTINChanged = insertCount > 0;
		return isTINChanged;
	}

	private boolean refineEdge(QuadEdge e) {
		// WatershedDebug.watchEdge(e, new Coordinate(698653.5505, 1282190.3613), new
		// Coordinate(698653.5177, 1282141.1384));

		/**
		 * Check for a triangle with a cross-medial edge. These are split where the
		 * medial axis crosses the edge.
		 */
		if (!isCrossMedial(e))
			return false;

		Coordinate splitPt = computeSplitPoint(e);
		splitPt.setZ(0.0);
		MedialAxisUtil.splitTriangleEdge(watershedTIN, splitPt, e);
		return true;
	}

	private static Coordinate computeSplitPoint(QuadEdge e) {
		WatershedVertex v0 = (WatershedVertex) e.orig();
		WatershedVertex v1 = (WatershedVertex) e.dest();

		MedialMidpointFinder mmFinder = new MedialMidpointFinder(v0.getCoordinate(), v1.getCoordinate(),
				getConstraintLines(v0), getConstraintLines(v1));
		Coordinate splitPt = mmFinder.getMedialPoint();

		// testing
		// if (v0.isNode() && v1.isNode()) {
		// System.out.println(e.toString());
		// }

		return splitPt;
	}

	private static Geometry getConstraintLines(WatershedVertex v) {
		if (v.isNode()) {
			List<LineString> lines = new ArrayList<LineString>();
			Collection<HydroEdge> hydroEdges = v.getNode().getEdges();
			for (HydroEdge e : hydroEdges) {
				lines.add(e.getLine());
			}
			return toGeometry(lines);
		}
		return v.getConstraintLine();
	}

	private static Geometry toGeometry(Collection<? extends Geometry> geoms) {
		if (geoms.size() < 1) {
			throw new IllegalArgumentException("Geometry collection must not be empty");
		}
		Geometry g = (Geometry) geoms.iterator().next();
		GeometryFactory geomFact = g.getFactory();
		return geomFact.buildGeometry(geoms);
	}

	/**
	 * Tests whether a quadedge touches a convex hull vertex
	 * 
	 * @param e
	 * @return true uf the edge tiych
	 */
	private static boolean hasBorderVertex(QuadEdge e) {
		if (isBorderVertex(e.orig()))
			return true;
		if (isBorderVertex(e.dest()))
			return true;
		return false;
	}

	private static boolean isBorderVertex(Vertex v) {
		if (!(v instanceof WatershedVertex))
			return true;
		return ((WatershedVertex) v).isOnHull();
	}

	/**
	 * Tests whether an edges crosses the (theoretical) medial axis. This is the
	 * case if the edge is between two constraint vertices, and the vertices have no
	 * different regions (or, in the case of nodes, no region in common).
	 * 
	 * @param e the quadedge to test
	 * @return true if this edge crosses the medial axis
	 */
	private boolean isCrossMedial(QuadEdge e) {
		// WatershedDebug.watchEdge(e, new Coordinate(1448202.0476, 507837.4119), new
		// Coordinate(1448231.5138, 507770.5573));

		WatershedVertex v0 = (WatershedVertex) e.orig();
		WatershedVertex v1 = (WatershedVertex) e.dest();

		// only interested in edges between constraint lines
		boolean isBothOnConstraint = v0.isOnConstraint() && v1.isOnConstraint();
		if (!isBothOnConstraint)
			return false;

		if (!WatershedVertexUtil.hasCommonRegion(v0, v1))
			return true;

		/**
		 * This handles certain pinchoff cases where two nodes have an edge in a region
		 * which is on one node, but not the other. This avoids pinch-off problems.
		 */
		if (v0.isNode() && v1.isNode() && WatershedVertexUtil.hasDifferentRegion(v0, v1)
				&& WatershedVertexUtil.getEdgeBetweenNodes(v0, v1) == null) {
			// System.out.println(e);
			return true;
		}

		return false;
	}

	private static class QuadEdgeLengthComparator implements Comparator<QuadEdge> {
		public int compare(QuadEdge e1, QuadEdge e2) {
			double len1 = e1.orig().getCoordinate().distance(e1.dest().getCoordinate());
			double len2 = e2.orig().getCoordinate().distance(e2.dest().getCoordinate());

			if (len1 < len2)
				return -1;
			if (len1 > len2)
				return 1;
			return 0;
		}
	}
}