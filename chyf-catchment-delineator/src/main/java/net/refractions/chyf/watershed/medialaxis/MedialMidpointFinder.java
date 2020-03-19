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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;

import net.refractions.chyf.util.LineSegmentUtil;
import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Given a segment defined by two {@link WatershedVertex}es which lie on constraint edges,
 * determines a point on the segment which lies on or very close to the medial axis between the
 * constraint edges. This will handle vertices which lie on multiple constraint edges (i.e. hydro
 * graph nodes).
 * <p>
 * It will also handle vertices which lie on the same constraint. In this case the midpoint (up to
 * tolerance) of the connecting segment will be returned. However, in this situation determining the
 * medial point is probably not the correct strategy.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class MedialMidpointFinder {
    private double       DIST_TOL_FACTOR = 100;

    private double       distanceTolerance;
    private Geometry[]   geom            = new Geometry[2];
    private Coordinate[] endPt           = new Coordinate[2];

    /**
     * Creates a new finder for a segment lying between two constraint vertices on different
     * constraints. The vertices must have unique constraints (ie they must not be nodes)
     * 
     * @param v0 a vertex with a unique constraint line
     * @param v1 a vertex with a unique constraint line
     */
    public MedialMidpointFinder(Coordinate p0, Coordinate p1, Geometry geom0, Geometry geom1) {
        endPt[0] = p0;
        endPt[1] = p1;
        geom[0] = geom0;
        geom[1] = geom1;
    }

    public Coordinate getMedialPoint() {
        double[] geomEndPtDist = new double[2];

        double segLen = endPt[0].distance(endPt[1]);
        distanceTolerance = segLen / DIST_TOL_FACTOR;

        // double distDiff = Double.MAX_VALUE;
        double midSegLen = Double.MAX_VALUE;
        Coordinate midPt = null;
        do {
            midPt = LineSegmentUtil.midPoint(endPt[0], endPt[1]);
            geomEndPtDist[0] = distance(geom[0], midPt);
            geomEndPtDist[1] = distance(geom[1], midPt);

            if (geomEndPtDist[0] < geomEndPtDist[1]) {
                endPt[0] = midPt;
            } else {
                endPt[1] = midPt;
            }

            // iterate until the candidate subsegment length is smaller than the tolerance
            midSegLen = endPt[0].distance(endPt[1]);
        } while (midSegLen > distanceTolerance);

        return midPt;
    }

    /**
     * Computes the distance from a {@link Geometry} to a point.
     * 
     * @param geom the geometry
     * @param pt the Coordinate
     * @return the shortest distance between the Geometry and the point
     */
    private static double distance(Geometry geom, Coordinate pt) {
        Point p = geom.getFactory().createPoint(pt);
        return geom.distance(p);
    }
}