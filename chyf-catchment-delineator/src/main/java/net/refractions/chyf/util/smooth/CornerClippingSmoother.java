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
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;


/**
 * Smooths a LineString by clipping corners which are less than a given angle. Corners are clipped
 * by inserting a new line segment across the corner at a computed length from the apex of the
 * corner. Also ensures that output segments are no shorter than a given minimum length.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class CornerClippingSmoother {
    public static Geometry smooth(Geometry geom) {
        CornerClippingSmoother smoother = new CornerClippingSmoother(geom);
        return smoother.getGeometry();
    }

    private LineString     line;

    // default is smooth every angle
    private double         maxSmoothAngle   = Double.MAX_VALUE;
    // default is no limit on clip length
    private double         maxClipLength    = Double.MAX_VALUE;
    // default is no minimum segment length
    private double         minSegmentLength = 0.0;

    // local storage
    private CoordinateList newPts;

    public CornerClippingSmoother(Geometry geom) {
        this.line = (LineString) geom;
    }

    /**
     * Sets the maximum angle to be smoothed (in radians). (Angles larger than this will not be
     * smoothed, since they are smooth enough already).
     * 
     * @param maxClipAngle
     */
    public void setMaximumSmoothAngle(double maxSmoothAngle) {
        this.maxSmoothAngle = maxSmoothAngle;
    }

    /**
     * Sets the maximum length that will be clipped from the arms of a corner
     * 
     * @param maxClipLength the maximum length to clip
     */
    public void setMaximumClipLength(double maxClipLength) {
        this.maxClipLength = maxClipLength;
    }

    /**
     * Sets the minimum length of segments which will be present in the output.
     * 
     * @param minSegmentLength
     */
    public void setMinimumSegmentLength(double minSegmentLength) {
        this.minSegmentLength = minSegmentLength;
    }

    public Geometry getGeometry() {
        CoordinateSequence seq = line.getCoordinateSequence();
        int nPts = seq.size();

        newPts = new CoordinateList();

        addPoint(seq.getCoordinate(0));

        Coordinate[] pt = new Coordinate[3];
        pt[0] = new Coordinate();
        pt[1] = new Coordinate();
        pt[2] = new Coordinate();

        double[] segLen = new double[2];
        double[] segMidLen = new double[2];
        LineSegment splitSeg = new LineSegment();

        for (int i = 0; i < nPts - 2; i++) {
            seq.getCoordinate(i, pt[0]);
            seq.getCoordinate(i + 1, pt[1]);
            seq.getCoordinate(i + 2, pt[2]);

            double segAngle = Angle.angleBetween(pt[0], pt[1], pt[2]);
            if (segAngle > maxSmoothAngle) {
                // don't clip
                addPoint(pt[1]);
                continue;
            }

            // clip the corner
            segLen[0] = pt[0].distance(pt[1]);
            segLen[1] = pt[1].distance(pt[2]);
            int minIndex = segLen[0] < segLen[1] ? 0 : 1;
            int maxIndex = 1 - minIndex;

            double minMidLen = Math.min(segLen[minIndex] / 2, maxClipLength);
            double maxMidLenRaw = Math.min(segLen[maxIndex] / 2, minMidLen * 2);
            double maxMidLen = Math.min(maxMidLenRaw, maxClipLength);

            segMidLen[minIndex] = minMidLen;
            segMidLen[maxIndex] = maxMidLen;

            splitSeg.p0 = pt[1];
            splitSeg.p1 = pt[0];
            double frac0 = segMidLen[0] / segLen[0];
            Coordinate midPt0 = splitSeg.pointAlong(frac0);

            splitSeg.p0 = pt[1];
            splitSeg.p1 = pt[2];
            double frac1 = segMidLen[1] / segLen[1];
            Coordinate midPt1 = splitSeg.pointAlong(frac1);

            addPointCheckDistance(midPt0);
            addPointCheckDistance(midPt1);
        }

        // add last point
        addPoint(seq.getCoordinate(seq.size() - 1));

        return line.getFactory().createLineString(newPts.toCoordinateArray());
    }

    /**
     * Adds a point, unless the point is too close to the previous point
     * 
     * @param pt
     */
    private void addPointCheckDistance(Coordinate pt) {
        boolean okToAdd = newPts.size() <= 0
                || pt.distance((Coordinate) newPts.get(newPts.size() - 1)) > minSegmentLength;
        if (okToAdd) {
            newPts.add(pt.clone(), false);
        }
    }

    private void addPoint(Coordinate pt) {
        newPts.add(pt.clone(), false);
    }

}