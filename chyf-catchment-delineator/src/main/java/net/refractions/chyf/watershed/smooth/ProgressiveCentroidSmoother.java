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

package net.refractions.chyf.watershed.smooth;


import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.Triangle;

/**
 * Smooths a line using centroid smoothing in an progressive fashion, which allows skipping
 * smoothing some vertices. It may be necessary to skip smoothing some vertices if the smoothed line
 * violates topological conditions.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class ProgressiveCentroidSmoother {
    // default is smooth every angle
    private double             maxSmoothAngle = Double.MAX_VALUE;

    private static final int   MAX_ITER       = 2;

    private LineString         line;
    private PrecisionModel     precModel;
    private final Coordinate[] basePts;
    private Coordinate[]       smoothedPts;
    private final int          lastIndex;
    private int                currIndex      = 0;
    private int                level          = 1;

    public ProgressiveCentroidSmoother(LineString line, PrecisionModel precModel) {
        this.line = line;
        this.precModel = precModel;
        basePts = line.getCoordinates();
        // smooth interior points of line only
        currIndex = 1;
        lastIndex = basePts.length - 2;
        // make smoothed equal to original to start
        smoothedPts = new Coordinate[basePts.length];
        System.arraycopy(basePts, 0, smoothedPts, 0, basePts.length);
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

    public LineString getSmoothedLine() {
        LineString currentLine = line.getFactory().createLineString(smoothedPts);
        // System.out.println(currentLine);
        return currentLine;
    }

    public boolean hasNext() {
        if (level < MAX_ITER)
            return true;
        if (currIndex < lastIndex)
            return true;
        return false;
    }

    /**
     * Generates the next version of the smoothed line.
     */
    public void generateNext() {
        currIndex++;
        if (currIndex > lastIndex) {
            // process is finished - don't continue
            // (throw error here?)
            if (level >= MAX_ITER)
                return;
            level++;
            // save smoothed points to base to be ready for next level of smoothing
            System.arraycopy(smoothedPts, 0, basePts, 0, basePts.length);
            currIndex = 1;
        }
        if (currIndex <= lastIndex)
            smoothPoint(currIndex);
    }

    private void smoothPoint(int i) {
        smoothedPts[i] = getSmoothedPoint(basePts[i - 1], basePts[i], basePts[i + 1]);
    }

    private Coordinate getSmoothedPoint(Coordinate p0, Coordinate p1, Coordinate p2) {
        /**
         * Uses simple centroid smoothing
         */
        double segAngle = Angle.angleBetween(p0, p1, p2);
        if (segAngle > maxSmoothAngle) {
            // don't smooth
            return p1;
        }
        Coordinate centroid = Triangle.centroid(p0, p1, p2);
        precModel.makePrecise(centroid);
        return centroid;
    }

    /**
     * Retracts the previous operation, and readies the generator to proceed down a different path
     */
    public void retract() {
        smoothedPts[currIndex] = basePts[currIndex];
    }
}