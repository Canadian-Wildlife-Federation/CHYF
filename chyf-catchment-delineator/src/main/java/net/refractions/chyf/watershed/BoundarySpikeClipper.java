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
import java.util.List;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;

import net.refractions.chyf.watershed.hydrograph.HydroNode;
import net.refractions.chyf.watershed.model.Region;
import net.refractions.chyf.watershed.model.WatershedTriangle;


/**
 * Finds spikes along the border of regions and merges them with adjacent regions. To produce the
 * best quality boundary, the spike is merged with the neighbour which has the longest shared edge.
 * <p>
 * A spike is a triangle on the boundary of a region which:
 * <ul>
 * <li>is connected to the region by an edge whose length is less than twice the length of the
 * longest triangle edge.
 * <li>is NOT constrained (adjacent to a constraint segment)
 * </ul>
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class BoundarySpikeClipper {
    private Collection<WatershedTriangle> triangles;

    public BoundarySpikeClipper(Collection<WatershedTriangle> triangles) {
        this.triangles = triangles;
    }

    public void compute() {
        for (WatershedTriangle tri : triangles) {
            // triangles adjacent to a constraint edge cannot be trimmed
            if (tri.isConstrained())
                continue;

            clipIfSpike(tri);
        }
    }

    private void clipIfSpike(WatershedTriangle tri) {
        // WatershedDebug.watchTriangle(tri, new Coordinate(713762.5172485998, 1365502.1164904584));

        QuadEdge spikeBaseEdge = findSpikeBaseEdge(tri);
        int spikeBaseEdgeIndex = tri.getEdgeIndex(spikeBaseEdge);

        // found a spike triangle, so merge with longest neighbour
        if (spikeBaseEdge != null) {
            WatershedTriangle nTri = findBestMergeNeighbour(tri, spikeBaseEdgeIndex);
            if (nTri == null)
                return;
            Region mergeRegion = nTri.getRegion();
            Region currRegion = tri.getRegion();
            currRegion.remove(tri);
            mergeRegion.add(tri);
            // System.out.println(tri);
        }
    }

    /**
     * Finds a spike base edge of a triangle, if it has one. A triangle is a spike if it has only
     * one neighbour triangle with the same region, and the edge between it and that neighbour is
     * short (less than the width tolerance).
     * 
     * @param tri a triangle on the edge of a disconnected group
     * @return the Region assigned
     */
    private QuadEdge findSpikeBaseEdge(WatershedTriangle tri) {
        QuadEdge e = findUniqueSameRegionEdge(tri);
        // no single edge has same region
        if (e == null)
            return null;
        if (isSpikeShaped(e))
            return e;
        return null;
    }

    /**
     * Finds the best neighbour triangle to merge a spike with. This routine incorporates a
     * heuristic to try and avoid the pinch-off problem.
     * 
     * @param tri
     * @param spikeBaseEdgeIndex
     * @return a neighbour triangle to merge with, if possible
     * @return null if no valid merge candidate exists.
     */
    private static WatershedTriangle findBestMergeNeighbour(WatershedTriangle tri,
            int excludedEdgeIndex) {
        /**
         * First try merging with neighbour with longest edge
         */
        int longestIndex = longestEdgeNeighbourIndex(tri);
        // I doubt this can happen, but just bail if it does
        if (longestIndex < 0)
            return null;
        WatershedTriangle longestTri = (WatershedTriangle) tri
            .getAdjacentTriangleAcrossEdge(longestIndex);
        if (isValidMergeCandidate(tri, longestTri))
            return longestTri;

        /**
         * If that wasn't possible, try with the remaining neighbour (the one with the middle-length
         * edge).
         */
        int otherIndex = otherOutOf3(excludedEdgeIndex, longestIndex);
        WatershedTriangle otherTri = (WatershedTriangle) tri
            .getAdjacentTriangleAcrossEdge(otherIndex);
        if (isValidMergeCandidate(tri, otherTri))
            return otherTri;

        /**
         * No valid merge candidate was found, so just leave things alone
         */
        return null;
    }

    /**
     * Determines the integer from the set [0..2] which is not in the set {n1, n2}.
     * 
     * @param n1 an integer in [0..2]
     * @param n2 an integer in [0..2]
     * @return the integer from [0..2] which is not one of the input values
     */
    private static int otherOutOf3(int n1, int n2) {
        for (int i = 0; i < 3; i++) {
            if (i != n1 && i != n2)
                return i;
        }
        throw new IllegalArgumentException("Input values appear not to lie in range [0..2]");
    }

    /**
     * Tests whether it is valid to merge a triangle into the given neighbour triangle. A merge is
     * invalid if there merging would create a "pinch-off" (which occurs when a region touches a
     * hydro node which is not actually adjacent to that region).
     * 
     * @param triToMerge the triangle to merge
     * @param candidateTri the candidate neighbour triangle to merge into
     * @return true if this merge is valid
     */
    private static boolean isValidMergeCandidate(WatershedTriangle triToMerge,
            WatershedTriangle candidateTri) {
        int candidateMergeRegionID = candidateTri.getRegion().getID();
        List<HydroNode> hydroNodes = triToMerge.getHydroNodes();
        for (HydroNode hydroNode : hydroNodes) {
            /**
             * This test ensures that merging the triangle with the candidate region will not
             * violate the rule that any adjacent hydrography must be in the same region.
             */
            if (!hydroNode.isAdjacentToRegion(candidateMergeRegionID)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the neighbour {@link WatershedTriangle} with the longest adjacent edge. Note that this
     * will <i>never</i> be the spike base edge.
     * 
     * @param tri the triangle to test
     * @return the index of the non-null neighbour triangle with longest shared edge
     */
    private static int longestEdgeNeighbourIndex(WatershedTriangle tri) {
        int longestEdgeNeighbourIndex = -1;
        double maxLen = -1;
        // WatershedTriangle longestEdgeNeighbour = null;
        for (int i = 0; i < 3; i++) {
            double len = tri.getEdge(i).getLength();
            if (len > maxLen) {
                maxLen = len;
                WatershedTriangle nTri = (WatershedTriangle) tri.getAdjacentTriangleAcrossEdge(i);
                if (nTri != null) {
                    // longestEdgeNeighbour = nTri;
                    longestEdgeNeighbourIndex = i;
                }
            }
        }
        return longestEdgeNeighbourIndex;
    }

    /**
     * Tests whether the triangle associated with the given {@link QuadEdge} is shaped like a spike.
     * 
     * @param e the QuadEdge to test
     * @return true if the associated triangle is spike-shaped
     */
    private boolean isSpikeShaped(QuadEdge e) {
        // should this check for large areas and not clip them?

        double len = e.getLength();
        if (len > WatershedSettings.MAX_SPIKE_BASE_WIDTH)
            return false;

        WatershedTriangle tri = (WatershedTriangle) e.getData();
        int edgeIndex = tri.getEdgeIndex(e);
        int hgtVertIndex = (edgeIndex + 2) % 3;
        double spikeAngle = Math.toDegrees(Angle.angleBetween(e.orig().getCoordinate(), tri
            .getCoordinate(hgtVertIndex), e.dest().getCoordinate()));

        if (spikeAngle < WatershedSettings.MAX_SPIKE_ANGLE)
            return true;
        return false;
    }

    // /**
    // * Computes the length of the longest edge for the given triangle.
    // *
    // * @param tri a triangle
    // * @return the length of the longest edge
    // */
    // private static double computeMaximumEdgeLength(QuadEdgeTriangle tri) {
    // double maxLen = 0.0;
    // for (int i = 0; i < 3; i++) {
    // double len = tri.getEdge(i).getLength();
    // if (len > maxLen)
    // maxLen = len;
    // }
    // return maxLen;
    // }

    /**
     * Finds the edge adjacent to the unique neighbour triangle with the same region, if such a
     * triangle exists.
     * 
     * @param tri
     * @return the edge for the neighbour triangle with the same reigon
     * @return null if there is not a unique same-region neighbour
     */
    private QuadEdge findUniqueSameRegionEdge(WatershedTriangle tri) {
        Region triRegion = tri.getRegion();
        QuadEdge uniqueEdge = null;
        int sameRegionCount = 0;
        for (int i = 0; i < 3; i++) {
            WatershedTriangle nTri = (WatershedTriangle) tri.getAdjacentTriangleAcrossEdge(i);
            if (nTri == null)
                continue;
            if (triRegion == nTri.getRegion()) {
                sameRegionCount++;
                uniqueEdge = tri.getEdge(i);
            }

        }
        if (sameRegionCount == 1)
            return uniqueEdge;
        return null;
    }
}