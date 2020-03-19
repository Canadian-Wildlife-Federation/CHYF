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
import java.util.Collection;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.triangulate.Segment;

import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Filters DEM vertices to eliminate any which are within a given distance
 * tolerance of the constraint segments.
 * 
 * @author Martin Davis
 */
public class DEMProximityFilter {
	private Collection<WatershedVertex> demVertices;
	// private Collection segments;
	private STRtree index = new STRtree();

	public DEMProximityFilter(Collection<WatershedVertex> demVertices, Collection<Segment> segments) {
		this.demVertices = demVertices;
		// this.segments = segments;
		indexSegments(segments);
	}

	private void indexSegments(Collection<Segment> segments) {
		for (Segment seg : segments) {
			index.insert(getEnvelope(seg), seg);
		}
	}

	private static Envelope getEnvelope(Segment seg) {
		LineSegment lineSeg = seg.getLineSegment();
		return new Envelope(lineSeg.p0, lineSeg.p1);
	}

	public List<WatershedVertex> getFilteredVertices(double distance) {
		ArrayList<WatershedVertex> accepted = new ArrayList<WatershedVertex>();
		accepted.ensureCapacity(demVertices.size());
		for (WatershedVertex v : demVertices) {
			Envelope env = new Envelope(v.getCoordinate());
			env.expandBy(distance);
			@SuppressWarnings("unchecked")
			List<Segment> items = index.query(env);
			if (!isWithinDistance(v.getCoordinate(), items, distance)) {
				accepted.add(v);
			}

		}
		return accepted;
	}

	private static boolean isWithinDistance(Coordinate pt, Collection<Segment> segs, double distance) {
		for (Segment seg : segs) {
			if (seg.getLineSegment().distance(pt) < distance)
				return true;
		}
		return false;
	}

}
