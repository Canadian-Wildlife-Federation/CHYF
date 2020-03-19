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

package net.refractions.chyf.watershed.medialaxis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import net.refractions.chyf.watershed.model.WatershedVertex;

public class ClosestConstraintGeometryBuilder {

	private static GeometryFactory geomFact = new GeometryFactory();

	private QuadEdgeSubdivision subdiv;

	public ClosestConstraintGeometryBuilder(QuadEdgeSubdivision subdiv) {
		this.subdiv = subdiv;
	}

	public List<LineString> getGeometry() {
		Set<WatershedVertex> nonConstraintVerts = findNonConstraintVertices();
		return getGeometry(nonConstraintVerts);
	}

	@SuppressWarnings("unchecked")
	private Set<WatershedVertex> findNonConstraintVertices() {
		Set<WatershedVertex> verts = new HashSet<WatershedVertex>();

		for (QuadEdge q : (Collection<QuadEdge>) subdiv.getEdges()) {
			Vertex v = q.orig();
			if (v instanceof WatershedVertex)
				verts.add((WatershedVertex) v);
		}
		return verts;
	}

	private List<LineString> getGeometry(Collection<? extends Vertex> vertices) {
		List<LineString> lines = new ArrayList<LineString>();
		for (Vertex v : vertices) {
			if (v instanceof WatershedVertex) {
				Coordinate p = v.getCoordinate();
				Vertex wv = ((WatershedVertex) v).getClosestVertex();
				if (wv != null) {
					Coordinate closestPt = wv.getCoordinate();
					LineString line = geomFact.createLineString(new Coordinate[] { closestPt, p });
					lines.add(line);
				}
			}
		}
		return lines;
	}

}
