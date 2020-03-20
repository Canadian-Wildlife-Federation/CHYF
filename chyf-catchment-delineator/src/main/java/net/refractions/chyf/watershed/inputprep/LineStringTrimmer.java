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

package net.refractions.chyf.watershed.inputprep;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Location;

/**
 * Trims a {@link LineString} into one or more sublines which are wholely contained in a given
 * polygon. Lines are trimmed on a whole-segment basis - only segments which are completely inside
 * the polygon are retained. The directionality of the input line is preserved.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class LineStringTrimmer {
    private LineString           line;
    private IndexedPointInAreaLocator polyLocater;

    private Coordinate[]         pts;
    private boolean[]            isSegIncluded;

    public LineStringTrimmer(LineString line, IndexedPointInAreaLocator polyLocater) {
        this.line = line;
        this.polyLocater = polyLocater;

        pts = line.getCoordinates();
        isSegIncluded = new boolean[pts.length];
        scan();
    }

    private void scan() {
        // determine included segs
        for (int i = 0; i < pts.length - 1; i++) {
            if (polyLocater.locate(pts[i]) == Location.INTERIOR 
            		&& polyLocater.locate(pts[i + 1]) == Location.INTERIOR) {
                isSegIncluded[i] = true;
            }
        }
    }

    public boolean isContained() {
        for (int i = 0; i < isSegIncluded.length - 1; i++) {
            if (!isSegIncluded[i])
                return false;
        }
        return true;
    }

    public boolean isNotContained() {
        for (int i = 0; i < isSegIncluded.length - 1; i++) {
            if (isSegIncluded[i])
                return false;
        }
        return true;
    }

    public List<LineString> getTrimmedLines() {
        List<LineString> trimmedLines = new ArrayList<LineString>();
        int startIndex = findNext(0, true);
        while (startIndex < isSegIncluded.length) {
            int endIndex = findNext(startIndex, false);
            trimmedLines.add(extractLineString(startIndex, endIndex + 1));
            startIndex = findNext(endIndex, true);
        }
        return trimmedLines;
    }

    private int findNext(int startIndex, boolean searchVal) {
        int index = startIndex;
        while (index < isSegIncluded.length && isSegIncluded[index] != searchVal) {
            index++;
        }
        return index;
    }

    public LineString extractLineString(int startIndex, int endIndex) {
        Coordinate[] extPts = new Coordinate[endIndex - startIndex];
        int index = 0;
        for (int i = startIndex; i < endIndex; i++) {
            extPts[index++] = pts[i];
        }
        LineString result = line.getFactory().createLineString(extPts);

        return result;
    }

}
