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
import java.util.List;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Triangle;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.util.Assert;

import net.refractions.chyf.watershed.QuadedgeConstraintFinder;
import net.refractions.chyf.watershed.debug.WatershedDebug;
import net.refractions.chyf.watershed.model.WatershedTIN;
import net.refractions.chyf.watershed.model.WatershedVertexUtil;

/**
 * Refines the triangulation to split triangles which lie in corners at a point
 * which is on the medial axis. Corners are triangles in which at least two
 * edges lie on constraints, and the constraints are in different regions.
 * (Often, but not always, these are stream confluences.) This code handles the
 * pathological case where three constraint segments form a triangle facet of
 * the subdivision. In this case the triangle is still split, but the split
 * point is the <b>inCentre</b> of the triangle.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class MedialAxisCornerRefiner {
	public static void refineMedial(WatershedTIN watershedTIN) {
		MedialAxisCornerRefiner mar = new MedialAxisCornerRefiner(watershedTIN);
		mar.compute();
	}

	private WatershedTIN watershedTIN;
	private QuadEdgeSubdivision subdiv;

	public MedialAxisCornerRefiner(WatershedTIN watershedTIN) {
		this.watershedTIN = watershedTIN;
		this.subdiv = watershedTIN.getSubdivision();
	}

	/**
	 * Refines the triangulation to incorporate the medial axis approximation.
	 * Refining is iterated until no more changes take place.
	 */
	public void compute() {
		List<CornerTriangle> cornerTris = getCornerTriangles();

		/**
		 * Sort corners by angle size, in order to make splitting "locally
		 * deterministic". There are situations where two corners are close and thus can
		 * cause the same triangle to be split in different ways, depending on the
		 * ordering of the corners. This in turn can cause different region assignment
		 * and different boundary lines. The ordering strategy makes this situation more
		 * deterministic. I.e. if two different blocks contain the same corners, the
		 * splitting should happen in the same way.
		 */
		Collections.sort(cornerTris);

		for (CornerTriangle cornerTri : cornerTris) {
			// check and see if corner triangle has already been modified
			if (!cornerTri.isStillExisting())
				continue;
			QuadEdge[] triEdge = cornerTri.getTriangle();

			WatershedDebug.watchTriangle(triEdge, new Coordinate(1164157.730506466, 1309253.2224712637));
			/**
			 * Check if the corner is in a triangle which has all edges as constraint edges.
			 * In this case, the triangle must be split at the centre point.
			 */
			QuadEdge openEdge = triEdge[1];
			if (QuadedgeConstraintFinder.isConstrained(openEdge)) {
				MedialAxisUtil.splitTriangleAtCentre(watershedTIN, triEdge);
			} else {
				splitOpenCornerTriangle(triEdge);
			}
		}
	}

	/**
	 * Splits an open corner triangle. An open corner triangle is a corner where the
	 * opposite side is NOT itself a constraint segment.
	 * 
	 * @param triEdge the corner to split
	 */
	private void splitOpenCornerTriangle(QuadEdge[] triEdge) {
		Coordinate splitPt = angleBisectorSplitPoint(triEdge[0], triEdge[2].sym());
		splitPt.setZ(0.0);
		// System.out.println(WKTWriter.toPoint(splitPt));
		// the middle edge is the one to split
		MedialAxisUtil.splitTriangleEdge(watershedTIN, splitPt, triEdge[1]);
	}

	private QuadEdge[] triEdge = new QuadEdge[3];

	private List<CornerTriangle> getCornerTriangles() {
		List<CornerTriangle> cornerTris = new ArrayList<CornerTriangle>();

		@SuppressWarnings("unchecked")
		Collection<QuadEdge> edges = subdiv.getEdges();
		for (QuadEdge e : edges) {
			// WatershedDebug.watchEdge(e, new Coordinate(698643.4999, 1282140.5585), new
			// Coordinate(698653.5177, 1282141.1384));

			// if (e.orig().getCoordinate().distance(testPt) < .01)
			// System.out.println("found");

			// to ensure every triangle is scanned, check both the edge and its sym
			addIfCornerTri(e, cornerTris);
			addIfCornerTri(e.sym(), cornerTris);
		}
		return cornerTris;
	}

	private void addIfCornerTri(QuadEdge e, List<CornerTriangle> cornerTris) {
		if (QuadedgeConstraintFinder.isConstraintFromNode(e)) {
			// get the triangle on the left of this edge
			QuadEdgeSubdivision.getTriangleEdges(e, triEdge);
			// WatershedDebug.watchTriangle(triEdge, new Coordinate(698650.4814451429,
			// 1282138.202721548));

			// since the tri is CCW, the sym of the 3rd edge is the other edge leaving from
			// the base
			// node
			QuadEdge oppEdge = triEdge[2].sym();

			// sanity check
			boolean isSameBaseNode = oppEdge.orig() == e.orig();
			Assert.isTrue(isSameBaseNode,
					"Inconsistent triangle topology - side and opposite have different start nodes");

			/**
			 * check if this triangle is a corner: - both edges are constraints originating
			 * at a common node - the two edges are in different regions
			 */
			if (QuadedgeConstraintFinder.isConstraintFromNode(oppEdge)) {
				// only need to split the triangle if the two sides are in different regions
				int region0 = WatershedVertexUtil.getRegionIDForEdgeLeavingNode(e);
				int region1 = WatershedVertexUtil.getRegionIDForEdgeLeavingNode(oppEdge);
				boolean isInDifferentRegion = region0 != region1;

				// if in different regions, this is a corner
				if (isInDifferentRegion) {
					double angle = Angle.angleBetween(e.dest().getCoordinate(), e.orig().getCoordinate(),
							oppEdge.dest().getCoordinate());
					cornerTris.add(new CornerTriangle((QuadEdge[]) triEdge.clone(), angle));
				}
			}
		}
	}

	private static Coordinate angleBisectorSplitPoint(QuadEdge e1, QuadEdge e2) {
		return Triangle.angleBisector(e1.dest().getCoordinate(), e1.orig().getCoordinate(), e2.dest().getCoordinate());
	}

	private static class CornerTriangle implements Comparable<CornerTriangle> {
		private static QuadEdge[] testTriEdge = new QuadEdge[3];

		private QuadEdge[] tri;
		private double angle;

		public CornerTriangle(QuadEdge[] tri, double angle) {
			this.tri = tri;
			this.angle = angle;
		}

		public QuadEdge[] getTriangle() {
			return tri;
		}

		public int compareTo(CornerTriangle ct) {
			if (angle < ct.angle)
				return -1;
			if (angle > ct.angle)
				return 1;
			return 0;
		}

		/**
		 * Tests if this triangle still exists in the subdivision
		 * 
		 * @return true if this triangle still exists in the subdivision
		 */
		public boolean isStillExisting() {
			QuadEdgeSubdivision.getTriangleEdges(tri[0], testTriEdge);
			if (tri[1] != testTriEdge[1] || tri[2] != testTriEdge[2])
				return false;
			return true;
		}
	}
}