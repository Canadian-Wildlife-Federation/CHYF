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

package net.refractions.chyf.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;

/**
 * @author Martin Davis
 * @version 1.0
 */
public class LineSegmentUtil {

    public LineSegmentUtil() {}

    public static Coordinate midPoint(Coordinate p0, Coordinate p1) {
        return new Coordinate((p0.x + p1.x) / 2, (p0.y + p1.y) / 2);
    }

    public static LineSegment[] toSegments(Coordinate[] coords) {
        LineSegment[] segs = new LineSegment[coords.length - 1];
        for (int i = 0; i < coords.length - 1; i++) {
            segs[i] = new LineSegment(coords[0], coords[i + 1]);
        }
        return segs;
    }
}