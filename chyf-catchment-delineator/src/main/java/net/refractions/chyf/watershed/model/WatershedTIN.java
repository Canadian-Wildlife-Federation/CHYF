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

package net.refractions.chyf.watershed.model;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import net.refractions.chyf.watershed.hydrograph.HydroGraph;


/**
 * Methods for computing various information about {@link WatershedTriangle}s in a TIN.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class WatershedTIN {

    public static double FLOW_ANGLE_TOL = 0.05; // 0.1 = 5.7 degrees

    /**
     * Tests whether a triangle has more than one trans-edge outflow regions that are different.
     * 
     * @param tri
     * @param angTol
     * @return
     */
    public static boolean hasDifferentTransOutflowDestRegions(WatershedTriangle tri, double angTol) {
        Region outflowDestRegion = null;
        for (int i = 0; i < 3; i++) {
            WatershedTriangle adjTri = tri.getTransOutflowTriangle(i, angTol);
            if (adjTri == null)
                continue;
            Region adjRegion = adjTri.getRegion();
            if (adjRegion == null)
                continue;

            if (outflowDestRegion == null)
                outflowDestRegion = adjRegion;
            else {
                // already found one trans outflow dest region - test whether this one is different
                if (adjRegion != outflowDestRegion)
                    return true;
            }
        }
        return false;
    }

    private HydroGraph             hydroGraph;
    private WatershedVertexFactory vertexfactory;
    private RegionFactory          regionFactory;
    private QuadEdgeSubdivision    subdiv;

    public WatershedTIN() {
        vertexfactory = new WatershedVertexFactory();
        regionFactory = new RegionFactory();
    }

    public void setHydroGraph(HydroGraph hydroGraph) {
        this.hydroGraph = hydroGraph;
    }

    public HydroGraph getHydroGraph() {
        return hydroGraph;
    }

    public QuadEdgeSubdivision getSubdivision() {
        return subdiv;
    }

    public void setSubdivision(QuadEdgeSubdivision subdiv) {
        this.subdiv = subdiv;
    }

    public WatershedVertexFactory getVertexFactory() {
        return vertexfactory;
    }

    public RegionFactory getRegionFactory() {
        return regionFactory;
    }

    /**
     * Gets the vertices which are hydro (constraint) vertices and which are on the border of the
     * subdivision. Vertices are on the border of the subdivision if there is an edge between them
     * and one of the outer triangle vertices.
     * 
     * @return a List of Coordinates on the border
     */
	public List<Coordinate> getBorderHydroCoords() {
        List<Coordinate> borderCoords = new ArrayList<Coordinate>();
        @SuppressWarnings("unchecked")
		List<QuadEdge> quadEdges = subdiv.getPrimaryEdges(true);
        for (QuadEdge qe : quadEdges) {
            if (!subdiv.isFrameEdge(qe))
                continue;

            // only one vertex of an edge touching an outer triangle vertex can be on the border
            Vertex v0 = getBorderHydroVertex(qe.orig());
            if (v0 != null) {
                borderCoords.add(v0.getCoordinate());
            }
            Vertex v1 = getBorderHydroVertex(qe.dest());
            if (v1 != null) {
                borderCoords.add(v1.getCoordinate());
            }
        }
        return borderCoords;
    }

    private Vertex getBorderHydroVertex(Vertex v) {
        if (subdiv.isFrameVertex(v))
            return null;
        if (!(v instanceof WatershedVertex))
            return null;
        if (!((WatershedVertex) v).isOnConstraint())
            return null;
        return v;
    }
}