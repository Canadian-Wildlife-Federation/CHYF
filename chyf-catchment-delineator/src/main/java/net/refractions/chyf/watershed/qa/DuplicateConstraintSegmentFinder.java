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

package net.refractions.chyf.watershed.qa;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;

import net.refractions.chyf.watershed.model.HydroEdge;

/**
 * Finds duplicate segments in a collection of {@link HydroEdge} constraints
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class DuplicateConstraintSegmentFinder {

	private Set<LineSegment> segmentSet = new TreeSet<LineSegment>();
	private Set<LineSegment> dupSegs = new TreeSet<LineSegment>();

	public DuplicateConstraintSegmentFinder(Collection<HydroEdge> hydroEdges) {
		// this.hydroEdges = hydroEdges;
		addEdges(hydroEdges);
	}

	public boolean hasDuplicates() {
		return dupSegs.size() > 0;
	}

	/**
	 * Gets the collection of duplicate {@link LineSegment}s
	 * 
	 * @return Collection<LineSegment>
	 */
	public Collection<LineSegment> getDuplicates() {
		return dupSegs;
	}

	private void addEdges(Collection<HydroEdge> hydroEdges) {
		for (HydroEdge edge : hydroEdges) {
			addSegments(edge.getLine());
		}
	}

	public void addSegments(LineString line) {
		Coordinate[] pts = line.getCoordinates();
		for (int i = 0; i < pts.length - 1; i++) {
			LineSegment seg = new LineSegment(pts[i], pts[i + 1]);
			seg.normalize();
			if (segmentSet.contains(seg)) {
				dupSegs.add(seg);
			} else {
				segmentSet.add(seg);
			}
		}
	}

}