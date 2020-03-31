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

package net.refractions.chyf.watershed.model;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.triangulate.ConstraintVertex;
import org.locationtech.jts.triangulate.ConstraintVertexFactory;
import org.locationtech.jts.triangulate.Segment;
import org.locationtech.jts.triangulate.quadedge.Vertex;

/**
 * @author Martin Davis
 * @version 1.0
 */
public class WatershedVertexFactory implements ConstraintVertexFactory {

	public WatershedVertexFactory() {
	}

	/**
	 * Creates a new vertex on the segment p0-p1. The vertex height is interpolated.
	 * 
	 * @param p
	 * @param p0
	 * @param p1
	 * @return
	 */
	public ConstraintVertex createVertex(Coordinate p, Coordinate p0, Coordinate p1) {
		// interpolate height if not already present
		if (Double.isNaN(p.getZ())) {
			p.setZ(Vertex.interpolateZ(p, p0, p1));
		}
		WatershedVertex v = new WatershedVertex(p);
		return v;
	}

	/**
	 * Creates a new vertex, which may split a segment.
	 * 
	 * @param p             the point at which to create the vertex
	 * @param constraintSeg the containing segment, or null
	 * @return the new vertex
	 */
	public ConstraintVertex createVertex(Coordinate p, Segment constraintSeg) {

		// if (p.distance(new Coordinate(1269456.9353029, 1626261.41909555)) < .001) {
		// System.out.println(p);
		// }

		if (constraintSeg != null)
			return createVertexOnSegment(p, constraintSeg);

		return new WatershedVertex(p);
	}

	/**
	 * Creates a new vertex on a segment. The vertex is tagged with the data from
	 * the constraint segment.
	 * 
	 * @param p             the point at which to create the vertex
	 * @param constraintSeg the segment containing the vertex
	 * @return the new vertex
	 */
	public ConstraintVertex createVertexOnSegment(Coordinate p, Segment constraintSeg) {
		WatershedVertex v = (WatershedVertex) createVertex(p, constraintSeg.getStart(), constraintSeg.getEnd());
		WatershedConstraint watershedConstraint = (WatershedConstraint) constraintSeg.getData();
		v.setRegion(watershedConstraint.getRegion());
		if (watershedConstraint.getRegion() == null)
			System.out.println("Found null region!");
		// if line is non-null, this is a hydro line constraint
		// set the constraint data appropriately.
		if (watershedConstraint != null) {
			v.setConstraint(watershedConstraint.getEdge());
			v.setClosestVertex(v);
			// EXPERIMENTAL: closestVertexFinder
			//v.setClosestCoordinate(v.getCoordinate());
		}
		return v;
	}
}