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

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

class PolygonEdgeMatcherTest {

	@Test
	void testCompare() {
		GeometryFactory gf = new GeometryFactory();
		Coordinate[] coords = new Coordinate[] {new Coordinate(0,0), new Coordinate(0,1),new Coordinate(1,1), new Coordinate(0,0)};
		Polygon p = gf.createPolygon(coords);
		testPoly(p);
		assertEquals(0, PolygonEdgeMatcher.compare(p, coords[0], new Coordinate(5, 5)));

		Coordinate[] revCoords = new Coordinate[] {new Coordinate(0,0), new Coordinate(1,1),new Coordinate(0,1), new Coordinate(0,0)};
		Polygon rp = gf.createPolygon(revCoords);
		testPoly(rp);
		assertEquals(0, PolygonEdgeMatcher.compare(rp, coords[0], new Coordinate(5, 5)));

	}
	
	private void testPoly(Polygon p) {
		LineString ring = p.getExteriorRing();
		for(int i=0; i < ring.getNumPoints()-1; i++) {
			if(Orientation.isCCW(ring.getCoordinateSequence())) {
				assertEquals(-1, PolygonEdgeMatcher.compare(p, ring.getCoordinateN(i), ring.getCoordinateN(i+1)));
				assertEquals(1, PolygonEdgeMatcher.compare(p, ring.getCoordinateN(i+1), ring.getCoordinateN(i)));
			} else {
				assertEquals(1, PolygonEdgeMatcher.compare(p, ring.getCoordinateN(i), ring.getCoordinateN(i+1)));
				assertEquals(-1, PolygonEdgeMatcher.compare(p, ring.getCoordinateN(i+1), ring.getCoordinateN(i)));				
			}
		}
	}

}
