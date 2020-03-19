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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.Vertex;



/**
 * An algorithm class to determine which watershed boundary paths are in-water.
 * 
 * @author Martin Davis
 */
public class InWaterPathFinder {
    public static void findInWaterPaths(List<WatershedEdgePath> wbePaths) {
        // InWaterPathFinder finder =
        new InWaterPathFinder(wbePaths);
    }

    private List<WatershedEdgePath> wbePaths;

    public InWaterPathFinder(List<WatershedEdgePath> wbePaths) {
        this.wbePaths = wbePaths;
        computeInWaterPaths();
    }

    private void computeInWaterPaths() {
        Collection<QuadEdge> wbNodes = extractNodesFromPaths(wbePaths);

        /**
         * Compute the in-water topology around all path nodes. NOTE: this will not work if there
         * are in-water edges which are not adjacent to any hydro edge node (since there is no way
         * of determining their in-water topology from local information)
         */
        for (QuadEdge wbNode : wbNodes) {
            markWaterTrianglesAroundNode(wbNode);
        }

    }

    /**
     * Gets a list of all distinct nodes from the extracted paths. This can be used to mark all the
     * nodes with their water topology.
     * 
     * @param paths a list of WatershedEdgePath
     * @return a collection of distinct QuadEdges originating at the wb edge nodes
     */
    private static Collection<QuadEdge> extractNodesFromPaths(List<WatershedEdgePath> paths) {
        Map<Vertex, QuadEdge> edgeNodeMap = new HashMap<Vertex, QuadEdge>();
        for (WatershedEdgePath path : paths) {
            QuadEdge e0 = path.getFirstEdge();
            edgeNodeMap.put(e0.orig(), e0);
            QuadEdge en = path.getLastEdge();
            edgeNodeMap.put(en.dest(), en.sym());
        }
        return edgeNodeMap.values();
    }

//    private static final int LANDSTATE_UNKNOWN = 0;
//    private static final int LANDSTATE_LAND    = 1;
//    private static final int LANDSTATE_WATER   = 2;

    /**
     * Marks water triangles around the vertex at nodeQE's origin. Uses the water-side indicator on
     * the hydro edges.
     * 
     * @param nodeQE a quadedge originating at the node to mark
     */
    private void markWaterTrianglesAroundNode(QuadEdge nodeQE) {
        if (HydroNodeEdgeStar.getNode(nodeQE) != null) {
            HydroNodeEdgeStar star = new HydroNodeEdgeStar(nodeQE);
            star.markWaterTrianglesAroundNode();
        }
    }

    // /**
    // * Finds a {@link QuadEdge} which originates at the given node and is a constraint edge.
    // *
    // * @param nodeQE a quad edge originating at the desired node
    // * @return a constraint edge originating at the given node
    // * @throws IllegalArgumentException if no constraint edge can be found
    // */
    // private QuadEdge findConstraintEdge(QuadEdge nodeQE) {
    // QuadEdge qe = nodeQE;
    // HydroEdge he = null;
    // while (he == null) {
    // OrientedHydroEdge ohe = QuadedgeConstraintFinder.getOrientedHydroEdge(qe);
    // he = (ohe != null ? ohe.getEdge() : null);
    // if (he != null)
    // break;
    // qe = qe.oNext();
    // if (qe == nodeQE) {
    // return null;
    // }
    // }
    // return qe;
    // }

    // private void checkConsistentLandState(boolean isWaterExpected, int landState, Coordinate pt)
    // {
    // // can't tell anything yet
    // if (landState == LANDSTATE_UNKNOWN)
    // return;
    // // check if expected state matches actual state
    // boolean isStateWater = landState == LANDSTATE_WATER;
    // if (isWaterExpected != isStateWater) {
    // throw new IllegalStateException("Inconsistent landType state at "
    // + WKTWriter.toPoint(pt));
    // }
    // }

}
