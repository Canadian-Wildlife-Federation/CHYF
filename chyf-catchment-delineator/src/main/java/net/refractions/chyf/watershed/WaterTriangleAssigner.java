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
import java.util.List;

import org.locationtech.jts.triangulate.quadedge.EdgeConnectedTriangleTraversal;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeTriangle;
import org.locationtech.jts.triangulate.quadedge.TraversalVisitor;

import net.refractions.chyf.watershed.model.OrientedHydroEdge;
import net.refractions.chyf.watershed.model.WatershedTriangle;


/**
 * Determines water state for each triangle.
 * <p>
 * NOTE: Does not currently work - problems with water state "spilling" around constraint edges
 * which end before the edge of the TIN.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class WaterTriangleAssigner implements TraversalVisitor {
    /**
     * Marks edge-connected regions (starting at constrained triangles). Triangles in disconnected
     * areas will be left with a null region attribute.
     * 
     * @param triangles a collection of {@link WatershedTriangle}s
     */
    public static void compute(Collection<WatershedTriangle> triangles) {
        EdgeConnectedTriangleTraversal traversal = new EdgeConnectedTriangleTraversal();
        traversal.init(findInitialWater(triangles));
        traversal.visitAll(new WaterTriangleAssigner());
    }

    public WaterTriangleAssigner() {}

    /**
     * Gets a list of triangles which are known to be in water.
     * 
     * @param triangles
     * @return a list of known water triangles
     */
    private static List<WatershedTriangle> findInitialWater(Collection<WatershedTriangle> triangles) {
        List<WatershedTriangle> initItems = new ArrayList<WatershedTriangle>();
        /**
         * The queue is initialized with constraint triangles which are determined be in water.
         */
        for (WatershedTriangle tri : triangles) {

            // WatershedDebug.watchTriangle(tri, new Coordinate(1581071.7571953444,
            // 589086.1355841735));
            if (isWater(tri)) {
                tri.setWater(true);
                initItems.add(tri);
            }
        }
        return initItems;
    }

    public static boolean isWater(WatershedTriangle tri) {
        // WatershedDebug.watchTriangle(tri, new Coordinate(926118.2031766445, 722187.2207680711));
        for (int i = 0; i < 3; i++) {
            QuadEdge qe = tri.getEdge(i);
            OrientedHydroEdge ohe = QuadedgeConstraintFinder.getOrientedHydroEdge(qe);
            // check if this edge is on a constraint
            if (ohe == null)
                continue;
            if (ohe.isWaterLeft()) {
                // System.out.println(tri);
                return true;
            }
        }
        return false;
    }

    public boolean visit(QuadEdgeTriangle currTri, int edgeIndex, QuadEdgeTriangle neighTri) {
        WatershedTriangle currTriW = (WatershedTriangle) currTri;
        WatershedTriangle neighTriW = (WatershedTriangle) neighTri;
        // WatershedDebug.watchTriangle(neighTriW, new Coordinate(926118.2031766445,
        // 722187.2207680711));

        // don't traverse across constraints (saves having to tell if the water state changes)
        // all constrained tris are already assigned
        QuadEdge qe = currTriW.getEdge(edgeIndex);
        OrientedHydroEdge ohe = QuadedgeConstraintFinder.getOrientedHydroEdge(qe);
        if (ohe != null)
            return false;

        // don't propagate if the triangle is already set (terminating condition)
        if (neighTriW.isWater())
            return false;
        // terminate at border, to avoid water "spilling" around the end of constraints
        // (which don't always go right to the frame)
        if (neighTriW.isBorder())
            return false;
        System.out.println(qe);

        // here the neighTri is edge-adjacent across a non-constraint edge
        // ==> it has same water state
        neighTriW.setWater(true);
        // debugging - check if water assignment is bogus
        checkValidWater(neighTriW);
        return true;
    }

    private void checkValidWater(WatershedTriangle tri) {
        boolean isLand = false;
        // check if any constraints of this tri indicate non-water
        for (int i = 0; i < 3; i++) {
            QuadEdge qe = tri.getEdge(i);
            OrientedHydroEdge ohe = QuadedgeConstraintFinder.getOrientedHydroEdge(qe);
            // check if this edge is on a constraint
            if (ohe == null)
                continue;

            if (ohe.isSingleLine()) {
                // System.out.println(tri);
                isLand = true;
            }
        }
        if (isLand)
            throw new IllegalStateException("Triangle is not water");

    }

}