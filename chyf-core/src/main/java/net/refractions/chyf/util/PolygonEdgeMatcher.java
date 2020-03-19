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

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

public class PolygonEdgeMatcher {

	/**
	 * Determines if the given line segment lies exactly on part of the edge of the polygon
	 * and if it does, returns -1 if the interior of the polygon is on the left of the line, 
	 * 1 if it is on the right, and 0 if it doesn't lie on the polygon edge.
	 * Requires exact coordinate equality in 2D. 
	 * 
	 * @param p Polygon to compare to
	 * @param c0 initial coordinate of line segment
	 * @param c1 final coordinate of line segment
	 * @return -1 if the interior of the polygon is on the left of the line from c0 to c1, 
	 * 		1 if it is on the right, and 0 if it doesn't lie on the polygon edge.
	 */
	public static int compare(Polygon p, Coordinate c0, Coordinate c1) {
		for(int ringIdx = 0; ringIdx < p.getNumInteriorRing(); ringIdx ++) {
			LineString ring = p.getInteriorRingN(ringIdx);
			int result = compareRing(ring, c0, c1, true);
			if(result != 0) {
				return result;
			}
		}
		return compareRing(p.getExteriorRing(), c0, c1, false);
	}

	private static int compareRing(LineString ring, Coordinate c0, Coordinate c1, boolean interior) {
		int numPoints = ring.getNumPoints();
		for(int coordIdx = 0; coordIdx < numPoints; coordIdx ++) {
			if(ring.getCoordinateN(coordIdx).equals2D(c0)) {
				if(coordIdx + 1 < numPoints && ring.getCoordinateN(coordIdx+1).equals(c1)) {
					// forward match
					boolean ccw = Orientation.isCCW(ring.getCoordinateSequence());
					if(ccw ^ interior ) {
						return -1;
					} else {
						return 1;
					}
				} else if(coordIdx > 0 && ring.getCoordinateN(coordIdx-1).equals(c1)) {
					// reverse match
					boolean ccw = Orientation.isCCW(ring.getCoordinateSequence());
					if(ccw ^ interior) {
						return 1;
					} else {
						return -1;
					}
				}
			}
		}
		return 0;
	}
}
