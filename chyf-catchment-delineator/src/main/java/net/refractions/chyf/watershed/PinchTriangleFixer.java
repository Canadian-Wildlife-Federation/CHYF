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

import net.refractions.chyf.watershed.hydrograph.HydroNode;
import net.refractions.chyf.watershed.model.Region;
import net.refractions.chyf.watershed.model.WatershedTriangle;
import net.refractions.chyf.watershed.model.WatershedTriangleUtil;

/**
 * Fixes triangles which cause pinches along watershed boundary lines. A pinch
 * occurs where a boundary for a region (drainage area) touches a hydro node
 * which is not actually adjacent to that region. This can happen due to certain
 * surface flow situations. Other heuristics are in place to try and avoid this
 * situation (i.e. in triangle trickling), but they are not 100% effective. This
 * is a last-ditch attempt to eliminate the bad region assignment.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class PinchTriangleFixer {
	public static boolean isPinchTriangle(WatershedTriangle tri) {
		// triangles adjacent to a constraint edge cannot be merged
		if (tri.isConstrained())
			return false;

		/**
		 * Only interested in triangles which are adjacent to hydro nodes (since this is
		 * where the problem happens)
		 */
		if (tri.getHydroNodeCount() < 1)
			return false;

		/**
		 * Assumption #1: pinch problem is indicated by a triangle incident on a node
		 * with a region which is not one of the drainage ids on the incident hydro
		 * edges. Comment: this fits all cases seen so far. Assumption #2: Only one of
		 * the nodes on the triangle will exhibit this problem Comment: The splitting
		 * heuristic is only applicable if this is the case. No other situations have
		 * been observed. It *has* been observed that a triangle may be adjacent to 2
		 * nodes, but only one of them has the inconsistent drainage ID situation.
		 */
		Collection<HydroNode> nodes = tri.getHydroNodes();
		HydroNode node = HydroNode.getNodeNotAdjacentToRegion(nodes, tri.getRegion().getID());
		if (node == null)
			return false;

		return true;
	}

	private Collection<WatershedTriangle> triangles;

	public PinchTriangleFixer(Collection<WatershedTriangle> triangles) {
		this.triangles = triangles;
	}

	public void compute() {
		for (WatershedTriangle tri : triangles) {
			mergePinchTri(tri);
		}
	}

	private void mergePinchTri(WatershedTriangle tri) {
		if (!isPinchTriangle(tri))
			return;

		WatershedTriangle mergDestTri = findBestMergeNeighbour(tri, tri.getRegion().getID());
		if (mergDestTri == null)
			return;

		merge(tri, mergDestTri);
	}

	private void merge(WatershedTriangle tri, WatershedTriangle mergDestTri) {
		// WatershedDebug.watchTriangle(tri, new Coordinate(713762.5172485998,
		// 1365502.1164904584));

		Region mergeRegion = mergDestTri.getRegion();
		Region currRegion = tri.getRegion();
		currRegion.remove(tri);
		mergeRegion.add(tri);
		// System.out.println(tri);
	}

	/**
	 * Finds the best neighbour triangle to merge with. This routine incorporates a
	 * heuristic to try and avoid the pinch-off problem.
	 * 
	 * @param tri
	 * @param excludedEdgeIndex
	 * @return a neighbour triangle to merge with, if possible
	 * @return null if no valid merge candidate exists.
	 */
	private static WatershedTriangle findBestMergeNeighbour(WatershedTriangle tri, int excludedEdgeIndex) {
		int[] index = WatershedTriangleUtil.edgeIndicesSortedByEdgeLength(tri);

		for (int i = 0; i < 3; i++) {
			int candIndex = index[i];
			if (candIndex == excludedEdgeIndex)
				continue;

			WatershedTriangle candTri = (WatershedTriangle) tri.getAdjacentTriangleAcrossEdge(candIndex);
			if (isValidMergeRegion(tri, candTri.getRegion().getID())) {
				return candTri;
			}
		}

		/**
		 * No valid merge candidate was found, so just leave things alone
		 */
		return null;
	}

	/**
	 * Tests whether it is valid to merge a triangle into the given neighbour
	 * triangle. A merge is invalid if there merging would create a "pinch-off"
	 * (which occurs when a region touches a hydro node which is not actually
	 * adjacent to that region).
	 * 
	 * @param triToMerge   the triangle to merge
	 * @param candidateTri the candidate neighbour triangle to merge into
	 * @return true if this merge is valid
	 */
	private static boolean isValidMergeRegion(WatershedTriangle triToMerge, int regionID) {
		Collection<HydroNode> hydroNodes = triToMerge.getHydroNodes();
		for (HydroNode hydroNode : hydroNodes) {
			/**
			 * Test the rule that any adjacent hydrography must be in the same region.
			 */
			if (!hydroNode.isAdjacentToRegion(regionID)) {
				return false;
			}
		}
		return true;
	}

	// /**
	// * Finds the neighbour {@link WatershedTriangle} with the longest adjacent
	// edge. Note that
	// this
	// * will <i>never</i> be the spike base edge.
	// *
	// * @param tri the triangle to test
	// * @return the index of the non-null neighbour triangle with longest shared
	// edge
	// */
	// private static int longestEdgeNeighbourIndex(WatershedTriangle tri) {
	// int longestEdgeNeighbourIndex = -1;
	// double maxLen = -1;
	// // WatershedTriangle longestEdgeNeighbour = null;
	// for (int i = 0; i < 3; i++) {
	// double len = tri.getEdge(i).getLength();
	// if (len > maxLen) {
	// maxLen = len;
	// WatershedTriangle nTri = (WatershedTriangle)
	// tri.getAdjacentTriangleAcrossEdge(i);
	// if (nTri != null) {
	// // longestEdgeNeighbour = nTri;
	// longestEdgeNeighbourIndex = i;
	// }
	// }
	// }
	// return longestEdgeNeighbourIndex;
	// }

}