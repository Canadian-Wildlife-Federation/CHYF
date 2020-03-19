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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Triangle;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import net.refractions.chyf.watershed.model.WatershedTIN;

/**
 * Utilities for Medial Axis Refinement
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class MedialAxisUtil {

    /**
     * Splits a TIN triangle at its incentre
     * 
     * @param watershedTIN the TIN the triangle is embedded in
     * @param triEdge
     */
    public static void splitTriangleAtCentre(WatershedTIN watershedTIN, QuadEdge[] triEdge) {
        Coordinate splitPt = Triangle.inCentre(triEdge[0].orig().getCoordinate(), triEdge[1]
            .orig()
            .getCoordinate(), triEdge[2].orig().getCoordinate());
        splitPt.setZ(0.0);
        // because this splitPt should be in the middle of an existing TIN face, can just insert the
        // site
        Vertex splitVertex = watershedTIN.getVertexFactory().createVertex(splitPt, null);
        // System.out.println(splitVertex);
        watershedTIN.getSubdivision().insertSite(splitVertex);
    }

    /**
     * Splits the {@link QuadEdge} <tt>splitEdge</tt> at the point <tt>splitPt</tt>.
     * 
     * @param watershedTIN the TIN the edge is embedded in
     * @param splitPt the point on the edge to split at
     * @param splitEdge the edge to be split
     */
    public static void splitTriangleEdge(WatershedTIN watershedTIN, Coordinate splitPt,
            QuadEdge splitEdge) {
        /**
         * The inserted point splits the split edge in half. Even though the splitPt is chosen to
         * lie "exactly" on the split edge, from the point of view of the subdivision topology it
         * lies in one or other of the triangles on either side of the split edge. When the split
         * point site is inserted, edges are added from it to the vertices of the containing
         * triangle. After this the original split edge is still in its original location, spanning
         * between the constraints. In order to change the split edge so that it no longer spans, it
         * is swapped (rotated inside its quadrilateral).
         */
        Vertex splitVertex = watershedTIN.getVertexFactory().createVertex(splitPt, null);
        watershedTIN.getSubdivision().insertSite(splitVertex);
        // rotate the cross edge so it no longer spans between the constraints
        QuadEdge.swap(splitEdge);
        // assert: the swapped splitEdge now has the splitPt as an endpoint
    }

}