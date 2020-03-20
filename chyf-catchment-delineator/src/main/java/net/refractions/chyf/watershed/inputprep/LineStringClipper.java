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

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.linearref.LengthIndexedLine;

/**
 * Clips a {@link LineString} into one or more sublines which are wholely contained in a given
 * polygon. Clipped lines may be cleaned by having very short end segments caused by the clipping to
 * be removed. The directionality of the input line is preserved. Note: this class only works for
 * fairly well-behaved linestrings:
 * <ul>
 * <li>line must not self-intersect in the interior
 * <li>rings are supported
 * <ul>
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class LineStringClipper {
    public static List<LineString> clip(LineString inputLine, Polygon clipPoly) {
        LineStringClipper clipper = new LineStringClipper(clipPoly);
        return clipper.clip(inputLine);
    }

    private Polygon clipPoly;

    public LineStringClipper(Polygon clipPoly) {
        this.clipPoly = clipPoly;
    }

    public List<LineString> clip(LineString inputLine) {
        Geometry raw = clipPoly.intersection(inputLine);

        List<LineString> clippedLines = new ArrayList<LineString>();
        for (int i = 0; i < raw.getNumGeometries(); i++) {
            Geometry comp = raw.getGeometryN(i);
            if (!(comp instanceof LineString) || comp.isEmpty())
                continue;
            LineString rectLine = rectify((LineString) comp, inputLine);
            if (rectLine != null)
                clippedLines.add(rectLine);
        }
        return clippedLines;
    }

    private LineString rectify(LineString line, LineString inputLine) {
        // ensure orientation is the same as input
        boolean isForward = relativeOrientation(line, inputLine);
        LineString orientLine = line;
        if (!isForward) {
            orientLine = (LineString)line.reverse();
        }

        // remove short end segments

        return orientLine;
    }

    private boolean relativeOrientation(LineString line, LineString inputLine) {
        LengthIndexedLine refLine = new LengthIndexedLine(inputLine);
        double len1 = refLine.indexOf(line.getCoordinateN(0));
        double len2 = refLine.indexOf(line.getCoordinateN(line.getNumPoints() - 1));
        return len1 < len2;

        // TODO: handle loops
    }
}
