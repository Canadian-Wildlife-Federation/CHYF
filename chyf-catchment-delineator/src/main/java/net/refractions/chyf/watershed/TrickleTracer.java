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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.util.Debug;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.watershed.debug.WatershedDebug;
import net.refractions.chyf.watershed.model.Region;
import net.refractions.chyf.watershed.model.WatershedTriangle;
import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Traces a trickle across triangulated terrain. The trickle is traced from a triangle centroid
 * until it winds up at a constraint edge, a pit point, or the edge of the subdivision.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class TrickleTracer {
    private static final int       MAX_TRACED_TRIS   = 1000;

    public static final int        TERMINUS_NONE     = 0;
    public static final int        TERMINUS_TRIANGLE = 1;
    public static final int        TERMINUS_VERTEX   = 2;

    private static GeometryFactory geomFactory       = new GeometryFactory();

    private static Logger          log               = LoggerFactory.getLogger(TrickleTracer.class);

    private CoordinateList         coordList         = new CoordinateList();
    private TriangleTracer         tracer            = new TriangleTracer();
    private Region                 constraintRegion  = null;
    private LineString             trickleGeom       = null;
    private int                    terminusType      = TERMINUS_NONE;
    private WatershedVertex        terminalVertex    = null;
    private WatershedTriangle      terminalTri       = null;
    private TriangleLocation       currLoc           = null;

    public TrickleTracer() {}

    public int getTerminusType() {
        return terminusType;
    }

    public WatershedVertex getTerminalVertex() {
        return terminalVertex;
    }

    public WatershedTriangle getTerminalTriangle() {
        return terminalTri;
    }

    public TriangleLocation getTerminalLocation() {
        return currLoc;
    }

    public Geometry getGeometry() {
        if (trickleGeom == null) {
            Coordinate[] pts = coordList.toCoordinateArray();
            if (pts.length < 2)
                return geomFactory.createPoint(pts[0]);
            trickleGeom = geomFactory.createLineString(coordList.toCoordinateArray());
        }
        return trickleGeom;
    }

    public Region getRegion() {
        return constraintRegion;
    }

    public String toString() {
        return getGeometry().toString();
    }

    public double getDistance() {
        return getGeometry().getLength();
    }

    /**
     * Trace a trickle from the centroid of the given triangle until it terminates in a constrained
     * triangle or in a pit.
     * 
     * @param tri the triangle to trace from
     * @return the terminating triangle
     * @return null if the trickle terminates in a pit, or has no flow across it
     */
    public Region trace(WatershedTriangle tri) {
        if (log.isTraceEnabled())
            log.trace("trace triangle: " + tri);

        WatershedDebug.watchTriangle(tri, new Coordinate(1266754.624185992, 843134.4002655299));

        addCoordinate(tri.getCentroid());
        currLoc = tracer.findCentroidFlowExit(tri);
        // possibly because triangle is flat?
        if (currLoc == null)
            return null;

        addCoordinate(currLoc.getCoordinate());

        constraintRegion = null;
        while (constraintRegion == null
        // && coordList.size() < 100
        ) {
            TriangleLocation nextLoc = null;
            if (currLoc.isVertex()) {
                nextLoc = tracer.traceVertexToNext(currLoc);
            } else {
                nextLoc = tracer.traceEdgeToNext(currLoc);
                // debugging
                if (nextLoc == null) {
                    System.out.println("traceEdgeToNext terminates in NULL : " + getGeometry());
                    TriangleLocation testLoc = tracer.findCentroidFlowExit(tri);
                    tracer.traceEdgeToNext(testLoc);
                }
            }
            // nowhere else to go, so exit
            if (nextLoc == null)
                return constraintRegion;
            currLoc = nextLoc;

            Coordinate pt = currLoc.getCoordinate();
            addCoordinate(pt);
            constraintRegion = getConstraintRegion(currLoc);
        }
        if (coordList.size() >= MAX_TRACED_TRIS) {
            Debug.println("Trickle trace length excessive: " + coordList.size() + " dest tri: "
                    + currLoc.getTriangle());
        }
        return constraintRegion;
    }

    private Region getConstraintRegion(TriangleLocation loc) {
        if (loc.isVertex()) {
            WatershedVertex wv = loc.getVertex();
            if (wv.isNode()) {
                // System.out.println("Trickle ended at node: " + wv + " edge: " + loc.getEdge());
                /**
                 * Node vertices need special handling, since they will have multiple regions
                 * incident on them Heuristic: use the region of the location triangle, if it is
                 * constrained
                 */
                if (loc.getTriangle().isConstrained())
                    return loc.getTriangle().getRegion();
            }
            // region will be null unless this is a hydro edge vertex
            return wv.getRegion();
        }
        if (loc.getTriangle().isConstrained())
            return loc.getTriangle().getRegion();
        return null;
    }

    private void addCoordinate(Coordinate pt) {
        coordList.add(pt, false);
    }

}
