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

import java.util.Collection;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
//import org.locationtech.jts.triangulate.quadedge.QuadEdge;
//import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
//import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Traverses a constrained triangulation to determine a medial axis. Uses a
 * "simulated annealing" technique of repeatedly updating vertices with
 * closest-constraint-vertex information, until the entire set of vertices is in
 * a stable state. This algorithm produces results that are *almost* correct,
 * but can fail to locate the closest vertex in some cases.
 * {@link IndexedClosestConstraintFinder} is more accurate.
 * <p>
 * Company: Vivid Solutions Inc.
 * </p>
 * 
 * @author Martin Davis
 * @version 1.0
 * @see IndexedClosestConstraintFinder
 */
public class ClosestConstraintVertexFinder {
	// private QuadEdgeSubdivision subdiv;
	private Collection<QuadEdge> edgesToRun;

	@SuppressWarnings("unchecked")
	public ClosestConstraintVertexFinder(QuadEdgeSubdivision subdiv) {
		// this.subdiv = subdiv;
		edgesToRun = subdiv.getPrimaryEdges(false);
	}

	public ClosestConstraintVertexFinder(Collection<QuadEdge> quadEdges) {
		edgesToRun = quadEdges;
	}

	public void compute() {
		boolean isUpdated = true;
		do {
			isUpdated = propagateClosestVertex(edgesToRun);
		} while (isUpdated);
	}

	private boolean propagateClosestVertex(Collection<QuadEdge> quadEdges) {
		boolean isUpdated = false;
		for (QuadEdge q : quadEdges) {
			Vertex v0 = q.orig();
			Vertex v1 = q.dest();

			if (!(v0 instanceof WatershedVertex))
				continue;
			if (!(v1 instanceof WatershedVertex))
				continue;

			isUpdated |= propagateClosestEitherDirection((WatershedVertex) v0, (WatershedVertex) v1);
		}
		return isUpdated;
	}

	/**
	 * Propagates a closest vertex from v0 to v1 or from v1 to v0, if possible.
	 * 
	 * @param v0
	 * @param v1
	 * @return true if a vertex was modified
	 */
	private boolean propagateClosestEitherDirection(WatershedVertex v0, WatershedVertex v1) {
		if (propagateClosestVertex(v0, v1))
			return true;
		if (propagateClosestVertex(v1, v0))
			return true;
		return false;
	}

	// private static Coordinate watchCoord = new Coordinate(1584172.8547276363,
	// 595781.9241884575);

	/**
	 * Propagates the closest vertex from a labelled vertex v0 to a non-constraint
	 * vertex v1.
	 * 
	 * @param v0
	 * @param v1
	 * @return true if v1 was updated
	 */
	private boolean propagateClosestVertex(WatershedVertex v0, WatershedVertex v1) {
		// don't overwrite constraint info
		if (v1.isOnConstraint())
			return false;
		// nothing to propagate
		if (v0.getClosestVertex() == null)
			return false;

		boolean doUpdate = false;
		if (v1.getClosestVertex() == null) {
			doUpdate = true;
		} else {
			Coordinate p = v1.getCoordinate();
			Coordinate currClosestPt = v1.getClosestVertex().getCoordinate();
			Coordinate candClosestPt = v0.getClosestVertex().getCoordinate();

			double candDist = p.distance(candClosestPt);
			double currDist = p.distance(currClosestPt);
			if (candDist < currDist) {
				doUpdate = true;
			}
		}
		if (doUpdate) {
			v1.setClosestVertex(v0.getClosestVertex());
			/*
			 * if (v1.getCoordinate().distance(new Coordinate(1583621.1414373596,
			 * 596863.3868938647)) < .000001) {
			 * System.out.println(WKTWriter.toLineString(v1.getCoordinate(),
			 * v1.getClosestVertex().getCoordinate())); }
			 */
		}

		return doUpdate;
	}

}