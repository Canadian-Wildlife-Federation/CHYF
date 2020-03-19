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

import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

import net.refractions.chyf.watershed.model.HydroEdge;

/**
 * Trims a set of {@link HydroEdge}s so they contain only segments within a
 * given convex polygon. Trimming is performed by removing line segments which
 * do not lie wholly in the convex polygon. (in other words, by removing
 * vertices). If an edge is split into more than one piece, new HydroEdges are
 * created for the resultants. HydroEdges which lie wholly in the convexPoly are
 * returned unchanged
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class HydroEdgeTrimmer {
	private Collection<HydroEdge> hydroEdges;

	private IndexedPointInAreaLocator polyLocater;

	private List<HydroEdge> trimmedEdges = new ArrayList<HydroEdge>();

	/**
	 * @param hydroEdges a Collection of {HydroEdge}s
	 * @param convexPoly
	 */
	public HydroEdgeTrimmer(Collection<HydroEdge> hydroEdges, Polygon convexPoly) {
		this.hydroEdges = hydroEdges;
		polyLocater = new IndexedPointInAreaLocator(convexPoly);
		// System.out.println(polyLocater.getPolygon());
		trim();
	}

	/**
	 * Gets the list of hydro edges, trimmed if required.
	 * 
	 * @return a list of {@link HydroEdge}s
	 */
	public List<HydroEdge> getTrimmedEdges() {
		return trimmedEdges;
	}

	private void trim() {
		for (HydroEdge he : hydroEdges) {
			trim(he);
		}
	}

	private void trim(HydroEdge he) {

		LineStringTrimmer trimmer = new LineStringTrimmer(he.getLine(), polyLocater);
		if (trimmer.isNotContained()) {
			// no part of line is contained
			return;
		} else if (trimmer.isContained()) {
			// the entire line is contained, so leave linework unchanged
			trimmedEdges.add(he);
		} else {
			List<LineString> trimmedLines = trimmer.getTrimmedLines();
			for (LineString subline : trimmedLines) {
				HydroEdge heNew = new HydroEdge(subline, he.getDrainageID(), he.getWaterSide());
				trimmedEdges.add(heNew);
			}
		}
	}
}