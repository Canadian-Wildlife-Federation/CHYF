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

package net.refractions.chyf.watershed.debug;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
//import org.locationtech.jts.triangulate.quadedge.QuadEdge;
//import org.locationtech.jts.triangulate.quadedge.QuadEdgeTriangle;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeTriangle;

import net.refractions.chyf.watershed.model.WatershedBoundaryEdge;
import net.refractions.chyf.watershed.model.WatershedTriangle;

/**
 * Debugging routines for Watershed Boundary Generator
 * 
 * @author Martin Davis
 */
public class WatershedDebug {
    private static final double DIST_TOL = 0.001;

    public static void watchWBEdge(WatershedBoundaryEdge e, int[] regionID) {
        if (matches(e, regionID)) {
            System.out.println(e);
        }
    }

    private static boolean matches(WatershedBoundaryEdge e, int[] regionID) {
        return matches(e, regionID, 0) || matches(e, regionID, 1);
    }

    private static boolean matches(WatershedBoundaryEdge e, int[] regionID, int baseIndex) {
        return regionID[baseIndex] == e.getRegionID(WatershedBoundaryEdge.LEFT)
                && regionID[1 - baseIndex] == e.getRegionID(WatershedBoundaryEdge.RIGHT);
    }

    public static void watchTriangle(WatershedTriangle tri, Coordinate pt) {
        watchTriangle(tri.getEdges(), pt);
    }

    public static void watchTriangle(QuadEdge[] triEdge, Coordinate pt) {
        if (QuadEdgeTriangle.contains(triEdge, pt)) {
            System.out.println(QuadEdgeTriangle.toPolygon(triEdge));
        }
    }

    public static void watchPoint(Coordinate pt, Coordinate watchPt) {
        if (matches(pt, watchPt)) {
            System.out.println(WKTWriter.toPoint(pt));
        }
    }

    public static void watchEdge(QuadEdge qe, Coordinate p0, Coordinate p1) {
        if (matches(qe, p0, p1))
            System.out.println(qe);
    }

    public static void watchEdge(Coordinate e0, Coordinate e1, Coordinate p0, Coordinate p1) {
        if (matches(e0, e1, p0, p1))
            System.out.println(WKTWriter.toLineString(e0, e1));
    }

    private static boolean matches(QuadEdge qe, Coordinate p0, Coordinate p1) {
        return matches(qe.orig().getCoordinate(), qe.dest().getCoordinate(), p0, p1);
    }

    private static boolean matches(Coordinate e0, Coordinate e1, Coordinate p0, Coordinate p1) {
        if (matches(e0, p0) && matches(e1, p1))
            return true;
        if (matches(e0, p1) && matches(e1, p0))
            return true;
        return false;
    }

    private static boolean matches(Coordinate p, Coordinate q) {
        return p.distance(q) < DIST_TOL;
    }
}
