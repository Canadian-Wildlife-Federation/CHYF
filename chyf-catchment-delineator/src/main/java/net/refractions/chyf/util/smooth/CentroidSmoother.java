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

package net.refractions.chyf.util.smooth;


import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Triangle;

/**
 * Smooths a LineString by replacing the interior vertices by the centroid of the triangle subtended
 * by their angle. Allows limiting the maximum angle which is smoothed. Rings will be completely
 * smoothed (including their endpoint) by default, but smoothing the endpoint may be disabled. The
 * smoothed line contains the same number of vertices as the input line.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class CentroidSmoother {
    public static Geometry smooth(Geometry geom, PrecisionModel pm) {
        CentroidSmoother smoother = new CentroidSmoother(geom, pm);
        return smoother.getGeometry();
    }

    private PrecisionModel pm;
    private LineString     line;
    // default is smooth every angle
    private double         maxSmoothAngle  = Double.MAX_VALUE;
    private boolean[]      isVertexRespected;
    private boolean        isSmoothRingEnd = true;
    private boolean        isRing;

    public CentroidSmoother(Geometry geom, PrecisionModel pm) {
        this.line = (LineString) geom;
        this.pm = pm;
    }

    /**
     * Sets the maximum corner angle which will be smoothed (in radians). (Corners with angles
     * larger than this will not be smoothed, since they are smooth enough already).
     * 
     * @param maxClipAngle
     */
    public void setMaximumSmoothAngle(double maxSmoothAngle) {
        this.maxSmoothAngle = maxSmoothAngle;
    }

    /**
     * Sets whether the endpoint location on a ring is smoothed or not. The default is to smooth
     * ring endpoints. A situation where it might not be desireable to smooth ring endpoints is
     * where another geometry is incident on the endpoints of the ring. Smoothing would break the
     * topology of the shared endpoint.
     * 
     * @param isSmoothRingEnd true if ring endpoints are to be smoothed
     */
    public void setSmoothRingEnd(boolean isSmoothRingEnd) {
        this.isSmoothRingEnd = isSmoothRingEnd;
    }

    public void setRespectedVertices(boolean[] isVertexRespected) {
        this.isVertexRespected = isVertexRespected;
    }

    public boolean isVertexRespected(int i) {
        if (isVertexRespected == null)
            return false;
        return isVertexRespected[i];
    }

    public Geometry getGeometry() {
        CoordinateSequence seq = line.getCoordinateSequence();
        int nPts = seq.size();
        isRing = seq.getCoordinate(0).equals2D(seq.getCoordinate(nPts - 1));
        // make a new sequence to hold the output
        CoordinateSequence seqSmooth = line.getFactory().getCoordinateSequenceFactory().create(
            nPts,
            seq.getDimension());
        Coordinate p0 = new Coordinate();
        Coordinate p1 = new Coordinate();
        Coordinate p2 = new Coordinate();

        boolean smoothEndpoint = isRing && isSmoothRingEnd;

        // if not doing ring smoothing, don't change endpoints - they stay identical with the input
        int startIndex = 0;
        if (!smoothEndpoint) {
            seq.getCoordinate(0, p1);
            CoordinateSequenceUtil.setCoordinate2D(seqSmooth, 0, p1);
            seq.getCoordinate(nPts - 1, p1);
            CoordinateSequenceUtil.setCoordinate2D(seqSmooth, nPts - 1, p1);
            startIndex = 1;
        }

        for (int i = startIndex; i < nPts - 1; i++) {

            int prevIndex = i - 1;
            if (prevIndex < 0)
                prevIndex = nPts - 1;

            seq.getCoordinate(prevIndex, p0);
            seq.getCoordinate(i, p1);
            seq.getCoordinate(i + 1, p2);

            Coordinate smoothedPt = p1;
            // only smooth non-respected vertices
            if (!isVertexRespected(i)) {
                smoothedPt = getSmoothedPoint(p0, p1, p2);
                pm.makePrecise(smoothedPt);
            }
            seqSmooth.setOrdinate(i, CoordinateSequence.X, smoothedPt.x);
            seqSmooth.setOrdinate(i, CoordinateSequence.Y, smoothedPt.y);
        }

        // if endpoint was smoothed, ensure both line end ordinates are identical
        if (smoothEndpoint) {
            seqSmooth.getCoordinate(0, p1);
            CoordinateSequenceUtil.setCoordinate2D(seqSmooth, nPts - 1, p1);
        }

        return line.getFactory().createLineString(seqSmooth);
    }

    private Coordinate getSmoothedPoint(Coordinate p0, Coordinate p1, Coordinate p2) {
        double segAngle = Angle.angleBetween(p0, p1, p2);
        if (segAngle > maxSmoothAngle) {
            // don't smooth
            return p1;
        }
        Coordinate centroid = Triangle.centroid(p0, p1, p2);
        return centroid;
    }
}