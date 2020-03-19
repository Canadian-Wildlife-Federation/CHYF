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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import net.refractions.chyf.watershed.model.Region;
import net.refractions.chyf.watershed.model.WatershedTIN;
import net.refractions.chyf.watershed.model.WatershedTriangle;

/**
 * Floods a TIN, by computing which triangle the steepest descent of a given
 * triangle flows into. The reverse of trickling.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class SteepestDescentFlooder {
	public static double FLOW_ANGLE_TOL = 0.05; // 0.1 = 5.7 degrees

	private QuadEdgeSubdivision subdiv;
	private WatershedTIN watershedTIN;
	private Collection<WatershedTriangle> triangles;
	private TriangleRegionFinder regionFinder = new TriangleRegionFinder();

	public SteepestDescentFlooder(QuadEdgeSubdivision subdiv, WatershedTIN watershedTIN,
			Collection<WatershedTriangle> triangles) {
		this.subdiv = subdiv;
		this.watershedTIN = watershedTIN;
		this.triangles = triangles;
	}

	public void compute() {
		initEdgeAdjacentRegions(triangles);

		Collection<WatershedTriangle> front = initFront(triangles);

		int numIter = 0;
		do {
			// front = updateFront(front);
			front = updateFrontWithSplitting(front);
			numIter++;
		} while (front.size() > 0 && numIter < 300);
		System.out.println("Iterations = " + numIter);
	}

	private static void initEdgeAdjacentRegions(Collection<WatershedTriangle> triangles) {
		for (WatershedTriangle tri : triangles) {
			tri.initRegion();
		}
	}

	private Set<WatershedTriangle> initFront(Collection<WatershedTriangle> triangles) {
		Set<WatershedTriangle> front = new HashSet<WatershedTriangle>();
		for (WatershedTriangle tri : triangles) {
			if (tri.getRegion() != null) {
				addAdjacentUnassigned(tri, front);
			}
		}
		return front;
	}

	private void addAdjacentUnassigned(WatershedTriangle tri, Collection<WatershedTriangle> front) {
		for (int i = 0; i < 3; i++) {
			WatershedTriangle adjTri = (WatershedTriangle) tri.getAdjacentTriangleAcrossEdge(i);
			if (adjTri == null)
				continue;
			if (adjTri.getRegion() == null)
				front.add(adjTri);
		}
	}

	// private List updateFront(Collection front) {
	// int numChanged = 0;
	// List newFront = new ArrayList();
	// for (Iterator i = front.iterator(); i.hasNext();) {
	// WatershedTriangle tri = (WatershedTriangle) i.next();
	// if (!tri.isLive())
	// continue;
	// if (tri.hasRegion())
	// continue;
	//
	// // attempt to determine the triangle region
	// updateRegion(tri);
	//
	// /**
	// * Region may still be null after the update, since it may not be possible to
	// assign a
	// * region to the triangle
	// */
	// if (tri.getRegion() != null) {
	// addAdjacentUnassigned(tri, newFront);
	// }
	// }
	//
	// return newFront;
	// }

	private List<WatershedTriangle> updateFrontWithSplitting(Collection<WatershedTriangle> front) {
		// int numChanged = 0;
		List<WatershedTriangle> newFront = new ArrayList<WatershedTriangle>();
		for (WatershedTriangle tri : front) {
			if (!tri.isLive())
				continue;
			if (tri.hasRegion())
				continue;

			if (tri.contains(new Coordinate(1578679.6957351773, 591661.1308893904))) {
				System.out.println(tri);
			}

			// try splitting this triangle first.
			if (!tri.hasRegion() && hasDifferentTransOutflowDestRegions(tri)) {
				boolean isSplit = split(tri, newFront);
				if (isSplit)
					continue;
			}

			// attempt to determine the triangle region
			updateRegion(tri);
			if (tri.hasRegion())
				// this triangle has been updated, so advance front
				addAdjacentUnassigned(tri, newFront);

			if (trySplitNeighbour(tri, newFront))
				continue;

			// weren't able to determine region
			if (tri.getRegion() == null) {
				// keep this triangle in front
				newFront.add(tri);
				continue;
			}
		}
		return newFront;
	}

	private static void checkHasDifferentNeighbourRegion(WatershedTriangle tri) {
		boolean hasNonNullDifferentNeighbourRegion = false;
		Region reg = tri.getRegion();
		// assume reg is non-null
		Region nReg = null;
		for (int i = 0; i < 3; i++) {
			if (!tri.isOutflowEdge(i))
				continue;
			WatershedTriangle neighTri = (WatershedTriangle) tri.getAdjacentTriangleAcrossEdge(i);
			if (neighTri == null)
				continue;
			nReg = neighTri.getRegion();
			if (nReg == null)
				continue;
			if (nReg != reg) {
				hasNonNullDifferentNeighbourRegion = true;
				// System.out.println(reg.getID() + " " + nReg.getID());
			}
		}
		if (!hasNonNullDifferentNeighbourRegion)
			System.out.println(reg.getID() + "  " + nReg.getID());
	}

	/**
	 * Checks whether a triangle neighbour is splittable, and splits it if it is
	 * 
	 * @param tri
	 * @param newFront
	 * @return
	 */
	private boolean trySplitNeighbour(WatershedTriangle tri, Collection<WatershedTriangle> newFront) {
		for (int i = 0; i < 3; i++) {
			WatershedTriangle neighbTri = (WatershedTriangle) tri.getAdjacentTriangleAcrossEdge(i);
			if (neighbTri == null)
				continue;
			if (!neighbTri.hasRegion())
				continue;
			if (allTransOutflowEdgesHaveRegions(neighbTri) && neighbTri.hasInterRegionTransOutflow(FLOW_ANGLE_TOL)) {
				boolean isSplit = split(neighbTri, newFront);
				if (isSplit)
					return true;
			}
		}
		return false;
	}

	/**
	 * Updates the region for a triangle if possible based on its neighbours.
	 * 
	 * @param tri
	 * @return true if the triangle region was updated
	 */
	private boolean updateRegion(WatershedTriangle tri) {
		if (!tri.hasFlow())
			return false;

		Region region = regionFinder.computeRegion(tri);
		if (region == null)
			return false;
		region.add(tri);
		return true;
	}

	private static boolean hasDifferentTransOutflowDestRegions(WatershedTriangle tri) {
		Region outflowDestRegion = null;
		for (int i = 0; i < 3; i++) {
			WatershedTriangle adjTri = tri.getTransOutflowTriangle(i, FLOW_ANGLE_TOL);
			if (adjTri == null)
				continue;
			Region adjRegion = adjTri.getRegion();
			if (adjRegion == null)
				continue;

			if (outflowDestRegion == null)
				outflowDestRegion = adjRegion;
			else {
				if (adjRegion != outflowDestRegion)
					return true;
			}
		}
		return false;
	}

	private boolean allTransOutflowEdgesHaveRegions(WatershedTriangle tri) {
		for (int i = 0; i < 3; i++) {
			WatershedTriangle adjTri = tri.getTransOutflowTriangle(i, FLOW_ANGLE_TOL);
			if (adjTri == null)
				continue;
			if (!adjTri.hasRegion())
				return false;
		}
		return true;
	}

	// private static boolean isSplittable(WatershedTriangle tri) {
	// return tri.numOutflowEdges() >= 2;
	// }

	private static boolean isSplittable(WatershedTriangle tri, double angTol) {
		int numOutflow = 0;
		for (int i = 0; i < 3; i++) {
			if (tri.isOutflowEdge(i, angTol))
				numOutflow++;
		}
		return numOutflow >= 2;
	}

	/**
	 * Splits a transRegion flow triangle.
	 * 
	 * @param tri
	 * @return the newly created triangles
	 */
	private boolean split(WatershedTriangle origTri, Collection<WatershedTriangle> newFront) {
		checkHasDifferentNeighbourRegion(origTri);

		if (!isSplittable(origTri, FLOW_ANGLE_TOL))
			return false;

		// origTri.printFlowAngles();

		int inflowEdgeIndex = getInflowEdge(origTri);
		WatershedTriangle inflowTri = (WatershedTriangle) origTri.getAdjacentTriangleAcrossEdge(inflowEdgeIndex);

		// don't split exterior triangles
		if (inflowTri != null) {
			Region inflowRegion = inflowTri.getRegion();
			if (inflowRegion != null && inflowRegion == watershedTIN.getRegionFactory().getBorder())
				return false;
		}

		QuadEdge inflowQE = origTri.getEdge(inflowEdgeIndex);
		int outVertexIndex = (inflowEdgeIndex + 2) % 3;
		Coordinate p1 = origTri.getCoordinate(outVertexIndex);
		Coordinate p0 = origTri.getFlowOutsideStartPt(p1);
		Coordinate splitPt = origTri.findEdgeIntersection(p0, p1, inflowEdgeIndex);
		// must not be splittable for some reason (degenerate? inflow edge parallel to
		// flow?)
		if (splitPt == null)
			return false;

		System.out.println(WKTWriter.toLineString(new CoordinateArraySequence(new Coordinate[] { splitPt, p1 })));

		Coordinate edgePt0 = origTri.getEdge(inflowEdgeIndex).orig().getCoordinate();
		Coordinate edgePt1 = origTri.getEdge(inflowEdgeIndex).dest().getCoordinate();

		/**
		 * for robustness check that split pt is not too close to an existing vertex
		 */
		if (splitPt.distance(edgePt0) < 2.0 || splitPt.distance(edgePt1) < 2.0)
			return false;

		// check that splitting segment is not too short
		// MD - No! Prevents some important splits
		if (splitPt.distance(p1) < 10)
			return false;

		// assert: edge being split is not a constraint (hydro) edge
		// this is important - otherwise would have to set vertex constraint info
		splitTriangle(splitPt, edgePt0, edgePt1, inflowQE, newFront);

		// remove the original triangles
		if (inflowTri != null)
			inflowTri.kill();
		origTri.kill();
		return true;
	}

	private void splitTriangle(Coordinate splitPt, Coordinate edgePt0, Coordinate edgePt1, QuadEdge inflowQE,
			Collection<WatershedTriangle> newFront) {
		/**
		 * The inserted vertex splits one of the triangles containing the inflow edge.
		 * The inflow edge needs to be rotated to split the opposite triangle as well.
		 */
		Vertex splitVertex = watershedTIN.getVertexFactory().createVertex(splitPt, edgePt0, edgePt1);
		QuadEdge newQE = subdiv.insertSite(splitVertex);
		QuadEdge.swap(inflowQE);

		// newQE has v as dest, so flip it
		QuadEdge startQE = newQE.sym();

		QuadEdge[] triEdge = new QuadEdge[3];
		// create the triangles around the new vertex
		QuadEdge qe = startQE;
		do {
			QuadEdgeSubdivision.getTriangleEdges(qe, triEdge);

			if (!isFrameTriangle(triEdge)) {
				WatershedTriangle tri = new WatershedTriangle(watershedTIN, triEdge);
				newFront.add(tri);
				System.out.println(tri);
				// System.out.println("flow angle: " + tri.getFlowAngle());
			}
			qe = qe.oNext();
		} while (qe != startQE);
	}

	private boolean isFrameTriangle(QuadEdge[] edge) {
		for (int i = 0; i < edge.length; i++) {
			if (subdiv.isFrameEdge(edge[i]))
				return true;
		}
		return false;
	}

	private static int getInflowEdge(WatershedTriangle tri) {
		for (int i = 0; i < 3; i++) {
			if (!tri.isOutflowEdge(i))
				return i;
		}
		return -1;
	}
}