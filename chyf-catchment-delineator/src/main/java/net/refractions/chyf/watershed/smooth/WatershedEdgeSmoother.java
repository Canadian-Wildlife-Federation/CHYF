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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

import net.refractions.chyf.util.smooth.CentroidSmoother;
import net.refractions.chyf.watershed.WatershedSettings;

/**
 * Smoothes a single generated watershed boundary edge, applying some additional heuristics in some
 * cases. Supports repeated smoothing, which allows testing for topological constraint violation at
 * each step of the smoothing process.
 * 
 * @author Martin Davis
 */
public class WatershedEdgeSmoother {

    // private static Geometry smoothCornerClip2(Geometry g) {
    // Geometry g2 = smoothCornerClip(g);
    // return smoothCornerClip(g2);
    // }

    // private static Geometry smoothCornerClip(Geometry g) {
    // CornerClippingSmoother smoother = new CornerClippingSmoother(g);
    // smoother.setMaximumSmoothAngle(Math.toRadians(WatershedSettings.MAX_SMOOTH_ANGLE));
    // smoother.setMinimumSegmentLength(1.0);
    // smoother.setMaximumClipLength(20.0);
    // return smoother.getGeometry();
    // }

    private Geometry smoothCentroid(Geometry g, boolean[] isVertexRespected,
            boolean isRingNodeSmoothed) {
        CentroidSmoother smoother = new CentroidSmoother(
            g,
            g.getPrecisionModel());
        smoother.setMaximumSmoothAngle(Math.toRadians(WatershedSettings.MAX_SMOOTH_ANGLE));
        if (isVertexRespected != null)
            smoother.setRespectedVertices(isVertexRespected);
        smoother.setSmoothRingEnd(isRingNodeSmoothed);
        return smoother.getGeometry();
    }

    // /**
    // * Applies Centroid Smoothing twice.
    // *
    // * @param g
    // * @return
    // */
    // private Geometry smoothCentroid2(Geometry g, boolean[] isVertexRespected,
    // boolean isRingNodeSmoothed) {
    // Geometry s1 = smoothCentroid(g, isVertexRespected, isRingNodeSmoothed);
    // return smoothCentroid(s1, isVertexRespected, isRingNodeSmoothed);
    // }

    /*
     * // MD - smoothing alternatives private Geometry smoothCentroidDensifyCentroid(Geometry g) {
     * Geometry cent1 = smoothCentroid(g, null, true); Geometry den = Densifier.densify(cent1, 25.0 );
     * return smoothCentroid(den, null, true); } private static Geometry smoothMidPoint(Geometry g) {
     * return MidPointSmoother.smooth(g); } private static Geometry smoothMidDensify(Geometry g) {
     * return Densifier.densify( MidPointSmoother.smooth(g), WatershedSettings.MAX_SEG_LEN ); }
     * private static Geometry smoothMiddDensifySlide(Geometry g) { Geometry mid =
     * MidPointSmoother.smooth(g); Geometry den = Densifier.densify(mid,
     * WatershedSettings.MAX_SEG_LEN ); Geometry slide = SlideAverageSmoother.smooth(den, 3); return
     * slide; } private static Geometry smoothDensifySlide(Geometry g) { Geometry den =
     * Densifier.densify(g, WatershedSettings.MAX_SEG_LEN ); Geometry slide =
     * SlideAverageSmoother.smooth(den, 13); return slide; }
     */

    private LineString line;
    private boolean[]  isVertexRespected;
    private boolean    isIsolatedRing;

    public WatershedEdgeSmoother(LineString line, boolean[] isVertexRespected,
            boolean isIsolatedRing) {
        this.line = line;
        this.isVertexRespected = isVertexRespected;
        this.isIsolatedRing = isIsolatedRing;

    }

    /**
     * Smoothes the current smoothed line. This routine may be called as many times as necessary.
     * Calling it more than twice may result in excessive loss of character.
     * 
     * @return a smoother version of the edge line
     */
    public LineString smooth() {
        line = computeSmoothedLine(line);
        return line;
    }

    public LineString computeSmoothedLine(LineString g) {
        Geometry gSmooth = g;
        if (!isSmoothable(g))
            return g;

        // simplify first, to remove any nearly collinear vertices
        // MD - probably not very effective?
        // Geometry gSimp = DouglasPeuckerSimplifier.simplify(g, WatershedSettings.SIMPLIFY_TOL);
        // gSmooth = smoothCornerClip(gSimp);

        gSmooth =
        // g; // no smoothing!
        smoothCentroid(g, isVertexRespected, isIsolatedRing); // seems to provide best smoothing
        // smoothCentroidDensifyCentroid(g);

        // smoothCornerClip(gSimp);
        // smoothMidDensify(g);
        // smoothMiddDensifySlide(g);
        // smoothDensifySlide(g);

        return (LineString) gSmooth;
    }

    private static boolean isSmoothable(LineString g) {
        if (g.getLength() < WatershedSettings.MIN_LEN_TO_SMOOTH)
            return false;

        // don't smooth very simple lines
        // MD - no! This hurts more than it helps
        // if (g.getNumPoints() <= 3) return false;
        int nPts = g.getNumPoints();

        // a closed line with 4 pts would collapse under centroid smoothing
        if (g.isClosed() && nPts <= 4)
            return false;

        /**
         * MD - smoothing lines with 3 pts is tricky. Along ridgelines it makes sense to smooth them
         * to almost straight, but if the 3pt line defines a very small watershed, making it
         * straight will almost eliminate the watershed! Probably the right solution is to flag 3-pt
         * edges which delineate entire watersheds. (i.e. where the 3rd line of the triangle is a
         * hydro edge) and limit the amount of smoothing applied to them. This is not yet
         * implemented.
         */
        // MD - too drastic (see note above)
        // centroid-smoothing lines with <= 3 pts will simply (nearly) collapse them
        // if (nPts <= 3) return false;
        return true;
    }

}
