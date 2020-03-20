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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

/**
 * Smooths a LineString by replacing the vertices by the endpoints and the midpoint of every
 * segment.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class SlideAverageSmoother {
    public static Geometry smooth(Geometry geom, int windowSize) {
        SlideAverageSmoother smoother = new SlideAverageSmoother(geom);
        smoother.setWindowSize(windowSize);
        return smoother.getGeometry();
    }

    private LineString   line;
    private int          windowSize = 3;
    private int          windowCentreIndex;
    private Coordinate[] window;

    public SlideAverageSmoother(Geometry geom) {
        this.line = (LineString) geom;
    }

    public void setWindowSize(int windowSize) {
        if (windowSize < 3 || (windowSize % 2) != 1)
            throw new IllegalArgumentException("Window size must be at least 3 and odd");
        this.windowSize = windowSize;
    }

    public Geometry getGeometry() {
        createWindow();

        CoordinateSequence seq = line.getCoordinateSequence();
        int nPts = seq.size();
        CoordinateSequence seq2 = line.getFactory().getCoordinateSequenceFactory().create(seq);

        int lastPt = nPts - windowSize + 1;
        for (int i = 0; i < lastPt; i++) {
            fillWindow(seq, i);

            Coordinate smoothedPt = computeSmoothed();
            seq2.setOrdinate(i + windowCentreIndex, CoordinateSequence.X, smoothedPt.x);
            seq2.setOrdinate(i + windowCentreIndex, CoordinateSequence.Y, smoothedPt.y);
        }

        return line.getFactory().createLineString(seq2);
    }

    private void createWindow() {
        windowCentreIndex = windowSize / 2;
        window = new Coordinate[windowSize];
        for (int i = 0; i < windowSize; i++) {
            window[i] = new Coordinate();
        }
    }

    private void fillWindow(CoordinateSequence seq, int index) {
        for (int i = 0; i < windowSize; i++) {
            seq.getCoordinate(index + i, window[i]);
        }
    }

    private Coordinate avgPt = new Coordinate();

    private Coordinate computeSmoothed() {
        // find average point
        avgPt.x = 0.0;
        avgPt.y = 0.0;
        for (int i = 0; i < windowSize; i++) {
            avgPt.x += window[i].x;
            avgPt.y += window[i].y;
        }
        avgPt.x /= windowSize;
        avgPt.y /= windowSize;

        avgPt.x += window[windowCentreIndex].x;
        avgPt.y += window[windowCentreIndex].y;

        avgPt.x /= 2;
        avgPt.y /= 2;

        return avgPt;
    }

}