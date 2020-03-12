/*
 * Copyright 2019 Government of Canada
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
 */
package net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi;

import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;

/**
 * 
 * Assumes test coordinates are already known to be inside the polygon.
 * 
 * Builds an index out of all segments in the polygon, then tests if any of
 * those segements crosses the given segment. If they do then the segement is
 * not within the polygon.
 * 
 */
public class FastSegInPolygon {

	private STRtree segTree = new STRtree();

	public FastSegInPolygon(Polygon p) {

		handleRing(p.getExteriorRing());
		for (int t = 0; t < p.getNumInteriorRing(); t++) {
			handleRing(p.getInteriorRingN(t));
		}
	}

	private void handleRing(LineString ls) {
		Coordinate[] coords = ls.getCoordinates();
		for (int t = 0; t < coords.length - 1; t++) {
			LineSegment seg = new LineSegment(coords[t], coords[t + 1]);
			segTree.insert(new Envelope(seg.p0, seg.p1), seg);
		}
	}

	/**
	 * Tests to see if the segment c1->c2 inside the polygon.
	 * 
	 * c1->c2 should be "small".
	 * 
	 * Assumption: already tested and know c1 and c2 are inside the polygon
	 * 
	 * @param c1
	 * @param c2
	 */
	public boolean testSegment(Coordinate c1, Coordinate c2) {

		Envelope searchEnv = new Envelope(c1, c2);
		List<?> possibleEdges = segTree.query(searchEnv);
		if (possibleEdges.size() == 0) return true;

		LineSegment testSeg = new LineSegment(c1, c2);
		for (Object x : possibleEdges) {
			LineSegment seg = (LineSegment) x;
			if (seg.intersection(testSeg) != null)
				return false;
		}
		return true;
	}
}
