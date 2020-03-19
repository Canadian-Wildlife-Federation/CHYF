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

import java.util.*;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.*;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;

import net.refractions.chyf.watershed.model.*;

/**
 * Represents a precise location on a WatershedTriangle.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class TriangleLocation {
    private WatershedTriangle tri;
    private int               edgeIndex;
    private Coordinate        pt;
    private double            dist;     // the fractional distance along the edge (a value in [0.0,
                                        // 1.0])

    public TriangleLocation(WatershedTriangle tri, int edgeIndex, Coordinate p) {
        this.tri = tri;
        this.edgeIndex = edgeIndex;
        pt = p;
        dist = -1;
    }

    /**
     * @param tri
     * @param edgeIndex
     * @param dist the fractional distance along the edge (a value in [0.0, 1.0] )
     */
    public TriangleLocation(WatershedTriangle tri, int edgeIndex, double dist) {
        this.tri = tri;
        this.edgeIndex = edgeIndex;
        this.dist = dist;
        pt = null;
        normalize();
    }

    public Coordinate getCoordinate() {
        if (pt == null) {
            // getting the orginal vertex coordinate has the advantage of providing Z as well
            if (dist <= 0.0) {
                return tri.getCoordinate(edgeIndex);
            } else if (dist >= 1.0) {
                return tri.getCoordinate(WatershedTriangle.nextIndex(edgeIndex));
            } else {
                LineSegment seg = new LineSegment();
                tri.getEdgeSegment(edgeIndex, seg);
                return seg.pointAlong(dist);
            }
        }
        return pt;
    }

    public WatershedVertex getVertex() {
        return (WatershedVertex) tri.getEdge(edgeIndex).orig();
    }

    /**
     * Normalizes a location referring to a vertex so that the vertex is the origin of the
     * location's edge
     */
    public void normalize() {
        if (dist == 1.0) {
            edgeIndex = WatershedTriangle.nextIndex(edgeIndex);
            dist = 0.0;
        }
    }

    public QuadEdge getEdge() {
        return tri.getEdge(edgeIndex);
    }

    /**
     * Flips a location to be based on the sym of its current edge. The associated triangle changes
     * to the neighbour triangle (if any).
     */
    public void flip() {
        QuadEdge qe = getEdge().sym();
        tri = (WatershedTriangle) qe.getData();
        if (tri != null)
            edgeIndex = tri.getEdgeIndex(qe);
        if (dist > 0)
            dist = 1 - dist;
        normalize();
    }

    public WatershedTriangle getTriangle() {
        return tri;
    }

    public int getEdgeIndex() {
        return edgeIndex;
    }

    public boolean isVertex() {
        return dist == 0.0 || dist == 1.0;
    }

    /**
     * Returns the collection of {@link WatershedTriangle}s adjacent to this vertex location.
     * 
     * @return a collection of adjacent WatershedTriangles
     */
    public Collection<WatershedTriangle> findTrianglesAdjacentToVertex() {
        // Assert: isVertex
        List<WatershedTriangle> adj = new ArrayList<WatershedTriangle>();

        QuadEdge start = getEdge();
        QuadEdge qe = start;
        do {
            WatershedTriangle adjTri = (WatershedTriangle) qe.getData();
            if (adjTri != null) {
                adj.add(adjTri);
            }
            qe = qe.oNext();
        } while (qe != start);

        return adj;
    }

    public String toString() {
        return WKTWriter.toPoint(getCoordinate());
    }
}