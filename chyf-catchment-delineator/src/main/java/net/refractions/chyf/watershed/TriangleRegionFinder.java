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

import java.util.List;

import net.refractions.chyf.watershed.model.Region;
import net.refractions.chyf.watershed.model.WatershedTriangle;

/**
 * Finds the region for a triangle based on the regions of adjacent triangles
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class TriangleRegionFinder {

	public TriangleRegionFinder() {
	}

	public Region computeRegion(WatershedTriangle tri) {
		Region region;

		// region = findCentroidOutflowRegion(tri);
		region = findOutflowUniqueAssignedRegion(tri);
		if (region != null)
			return region;

		region = findUniqueLowerRegion(tri);
		// region = findChannelOutflowRegion(tri);
		if (region != null)
			return region;

		return null;
	}

	// /**
	// * Finds the triangle and region that the centroid flows to (if any - the
	// region for the
	// outflow
	// * destination triangle may not yet be defined). If the centroid flows to a
	// channel, the
	// region
	// * will probably not yet be known.
	// *
	// * @param tri
	// * @return the centroid outflow neighbour region, if any
	// * @return null if the outflow region cannot be determined
	// */
	// private static Region findCentroidOutflowRegion(WatershedTriangle tri) {
	// int[] centroidOutEdgeIndexVar = new int[1];
	// tri.findFlowExit(tri.getCentroid(), centroidOutEdgeIndexVar);
	// if (centroidOutEdgeIndexVar[0] < 0) {
	// System.out.println(tri);
	// }
	// WatershedTriangle adjTri = (WatershedTriangle) tri
	// .getAdjacentTriangleAcrossEdge(centroidOutEdgeIndexVar[0]);
	// // System.out.println("adj = " + adjTri);
	// if (adjTri == null)
	// return null;
	// Region adjRegion = adjTri.getRegion();
	// return adjRegion;
	// }

	// /**
	// * If the centroid flows to a channel edge, finds the outflow vertex for the
	// channel and
	// returns
	// * a region of a lower triangle adjacent to the vertex.
	// *
	// * @param tri
	// * @return the outflow neighbour region, if any
	// * @return null if the outflow region cannot be determined
	// */
	// private static Region findChannelOutflowRegion(WatershedTriangle tri) {
	// // if (tri.contains(new Coordinate(1578936.1831368792, 591571.4879912633))) {
	// // System.out.println(tri);
	// // }
	//
	// int[] centroidOutEdgeIndexVar = new int[1];
	// tri.findFlowExit(tri.getCentroid(), centroidOutEdgeIndexVar);
	// if (!tri.isChannel(centroidOutEdgeIndexVar[0]))
	// return null;
	//
	// int lowVertexIndex = tri.getLowVertexIndex(centroidOutEdgeIndexVar[0]);
	//
	// double triHgt = tri.getHeight();
	// List adjTris = tri.getTrianglesAdjacentToVertex(lowVertexIndex);
	// boolean foundNullLowerRegion = false;
	// Region lowerRegion = null;
	// for (Iterator it = adjTris.iterator(); it.hasNext();) {
	// WatershedTriangle adjTri = (WatershedTriangle) it.next();
	// if (adjTri == tri)
	// continue;
	// double adjHgt = adjTri.getHeight();
	// if (adjHgt <= triHgt) {
	// Region adjRegion = adjTri.getRegion();
	// if (adjRegion != null)
	// lowerRegion = adjRegion;
	// else
	// foundNullLowerRegion = true;
	// }
	// }
	// if (foundNullLowerRegion)
	// return null;
	// return lowerRegion;
	// }

	/**
	 * If the triangle has an outflow vertex, and if all lower triangles around that
	 * vertex are assigned and have the same region, return that region.
	 * 
	 * @param tri
	 * @return the outflow neighbour region, if any
	 * @return null if the outflow region cannot be determined
	 */
	private static Region findUniqueLowerRegion(WatershedTriangle tri) {
		// if (tri.contains(new Coordinate(1579430.8193566396, 592258.1686626246))) {
		// System.out.println(tri);
		// }

		int lowVertexIndex = tri.getLowestVertexIndex();

		@SuppressWarnings("unchecked")
		List<WatershedTriangle> adjTris = tri.getTrianglesAdjacentToVertex(lowVertexIndex);
		boolean foundNullLowerRegion = false;
		Region lowerRegion = null;
		for (WatershedTriangle adjTri : adjTris) {
			if (adjTri == tri)
				continue;
			double adjMaxHgt = adjTri.getMaxHeight();
			if (adjMaxHgt <= tri.getMaxHeight()) {
				Region adjRegion = adjTri.getRegion();
				if (adjRegion != null)
					lowerRegion = adjRegion;
				else
					foundNullLowerRegion = true;
			}
		}
		if (foundNullLowerRegion)
			return null;
		return lowerRegion;
	}

	// /**
	// * Finds the unique outflow region for a triangle, if one exists. This is the
	// case if all
	// * outflow edges are adjacent to triangles with assigned regions, and the
	// region is the same
	// for
	// * all
	// *
	// * @param tri
	// * @return the unique outflow neighbour region, if any
	// * @return null if the neighbour regions are not unique or not assigned
	// */
	// private static Region findUniqueOutflowRegion(WatershedTriangle tri, boolean
	// requireValidRegions) {
	// Region outflowRegion = null;
	// boolean isRegionUnique = true;
	//
	// for (int i = 0; i < 3; i++) {
	// if (!tri.isOutflowEdge(i, SteepestDescentFlooder.FLOW_ANGLE_TOL))
	// continue;
	// WatershedTriangle adjTri = (WatershedTriangle)
	// tri.getAdjacentTriangleAcrossEdge(i);
	// // may be null e.g. if on edge of area
	// if (adjTri == null)
	// continue;
	// Region adjRegion = adjTri.getRegion();
	//
	// // no region information - can't update
	// if (adjRegion == null) {
	// if (requireValidRegions)
	// return null;
	// continue;
	// }
	// if (outflowRegion == null)
	// outflowRegion = adjRegion;
	// else {
	// if (outflowRegion != adjRegion)
	// isRegionUnique = false;
	// }
	// }
	// if (!isRegionUnique)
	// return null;
	// return outflowRegion;
	// }

	/**
	 * Finds the unique outflow region for a triangle, if one exists. This is the
	 * case if all outflow edges are adjacent to triangles with assigned regions,
	 * and the region is the same for all
	 * 
	 * @param tri
	 * @return the unique outflow neighbour region, if any
	 * @return null if the neighbour regions are not unique or not assigned
	 */
	private static Region findOutflowUniqueAssignedRegion(WatershedTriangle tri) {
		Region outflowRegion = null;

		for (int i = 0; i < 3; i++) {
			if (!tri.isOutflowEdge(i, SteepestDescentFlooder.FLOW_ANGLE_TOL))
				continue;

			WatershedTriangle adjTri = (WatershedTriangle) tri.getAdjacentTriangleAcrossEdge(i);
			// adj triangle may be null e.g. if on edge of area
			if (adjTri == null)
				continue;
			Region adjRegion = adjTri.getRegion();

			// no region information - can't update
			if (adjRegion == null) {
				return null;
			}
			if (outflowRegion == null)
				outflowRegion = adjRegion;
			else
			// check for uniqueness
			if (outflowRegion != adjRegion)
				return null;
		}
		return outflowRegion;
	}

	// /**
	// * Computes a region for a triangle which is one side of a channel flowing
	// into a known unique
	// * region.
	// *
	// * @param tri
	// * @return
	// */
	// private static Region findChannelUniqueRegion(WatershedTriangle tri) {
	// Region triOutRegion = findUniqueOutflowRegion(tri, false);
	// if (triOutRegion == null)
	// return null;
	//
	// WatershedTriangle channelTri = findChannelNeighbour(tri);
	// if (channelTri == null)
	// return null;
	// Region chTriOutRegion = findUniqueOutflowRegion(channelTri, false);
	// if (chTriOutRegion == null)
	// return null;
	// if (triOutRegion != chTriOutRegion)
	// return null;
	//
	// // success! The drainage region is uniquely determined
	// return triOutRegion;
	// }

	// private static WatershedTriangle findChannelNeighbour(WatershedTriangle tri)
	// {
	// for (int i = 0; i < 3; i++) {
	// if (!tri.isOutflowEdge(i))
	// continue;
	// QuadEdge edge = tri.getEdge(i);
	// QuadEdge sym = edge.sym();
	// WatershedTriangle adjTri = (WatershedTriangle) sym.getData();
	// if (adjTri == null)
	// return null;
	// if (!adjTri.hasFlow())
	// return null;
	// int adjTriEdgeIndex = adjTri.getEdgeIndex(sym);
	// if (!adjTri.isOutflowEdge(adjTriEdgeIndex))
	// continue;
	//
	// // success! found a channel neighbour
	// return adjTri;
	// }
	// return null;
	// }
}