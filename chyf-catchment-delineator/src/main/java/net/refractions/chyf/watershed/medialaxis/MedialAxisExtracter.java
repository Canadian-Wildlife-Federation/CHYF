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
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.TriangleVisitor;

import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Extracts a set of medial axis lines from a closest-vertex labelled
 * {@link QuadEdgeSubdivision}.
 * <p>
 * Company: Vivid Solutions Inc.
 * </p>
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class MedialAxisExtracter {
	static GeometryFactory fact = new GeometryFactory();

	private QuadEdgeSubdivision subdiv;
	private List<LineString> midLines = new ArrayList<LineString>();

	public MedialAxisExtracter(QuadEdgeSubdivision subdiv) {
		this.subdiv = subdiv;
	}

	public void compute() {
		MedialVertexVisitor visitor = new MedialVertexVisitor();
		subdiv.visitTriangles(visitor, false);

		midLines = convertToLines(visitor.getMidSegs());
	}

	public List<LineString> getMidLines() {
		return midLines;
	}

	private static List<LineString> convertToLines(List<Coordinate[]> linePts) {
		List<LineString> lines = new ArrayList<LineString>();
		// List triPts = cdt.getSubdivision().getTriangles(false);
		for (Coordinate[] pts : linePts) {
			lines.add(fact.createLineString(pts));
		}
		return lines;
	}

	private static class MedialVertexVisitor implements TriangleVisitor {
		private List<Coordinate[]> midSegs = new ArrayList<Coordinate[]>();

		public List<Coordinate[]> getMidSegs() {
			return midSegs;
		}

		public MedialVertexVisitor() {
		}

		public void visit(QuadEdge[] triEdges) {
			WatershedVertex[] verts = new WatershedVertex[3];
			for (int i = 0; i < 3; i++) {
				verts[i] = (WatershedVertex) triEdges[i].orig();
			}
			visit(triEdges, verts);
		}

		public void visit(QuadEdge[] triEdges, WatershedVertex[] verts) {
			if (addNodeAdjacentEdges(triEdges)) {
				return;
			}
			// for now handle pseudo-ridges only (not peaks or flats)
			int ridgeVertexIndex = findRidgeOddVertexIndex(verts);
			if (ridgeVertexIndex < 0)
				return;

			int i1 = (ridgeVertexIndex + 1) % 3;
			int i2 = (ridgeVertexIndex + 2) % 3;

			// compute a medial vertex for this triangle edge (if any)
			Coordinate mid0 = computeMedialVertex(verts[ridgeVertexIndex], verts[i1]);
			Coordinate mid1 = computeMedialVertex(verts[ridgeVertexIndex], verts[i2]);

			midSegs.add(new Coordinate[] { mid0, mid1 });
		}

		/**
		 * Finds the single vertex on one side of a pseudo-ridge.
		 * 
		 * @param verts
		 * @return the index of the single vertex on one side of the pseudo-ridge
		 * @return -1 if the triangle is not a pseudo-ridge
		 */
		private int findRidgeOddVertexIndex(WatershedVertex[] verts) {
			for (int i = 0; i < 3; i++) {
				if (isRidgeOddVertexIndex(verts, i))
					return i;
			}
			return -1;
		}

		private boolean isRidgeOddVertexIndex(WatershedVertex[] verts, int i) {
			int i1 = (i + 1) % 3;
			int i2 = (i + 2) % 3;

			if (((WatershedVertex) verts[i1].getClosestVertex())
					.getConstraint() == ((WatershedVertex) verts[i2].getClosestVertex()).getConstraint()
					&& ((WatershedVertex) verts[i].getClosestVertex())
							.getConstraint() != ((WatershedVertex) verts[i1].getClosestVertex()).getConstraint()) {
				return true;
			}
			return false;
		}

		// private Coordinate OLDcomputeMedialVertex(WatershedVertex v0, WatershedVertex
		// v1) {
		// Coordinate p0 = v0.getCoordinate();
		// Coordinate p1 = v1.getCoordinate();
		// Coordinate mid = new Coordinate((p0.x + p1.x) / 2, (p0.y + p1.y) / 2);
		// return mid;
		// }

		private Coordinate computeMedialVertex(WatershedVertex v0, WatershedVertex v1) {
			// Coordinate p0 =
			v0.getCoordinate();
			// Coordinate p1 =
			v1.getCoordinate();
			MedialLineIntersector mli = new MedialLineIntersector(v0.getCoordinate(), v1.getCoordinate(),
					v0.getClosestVertex().getCoordinate(), v1.getClosestVertex().getCoordinate());
			return mli.getMedialCoordinate();
		}

		private boolean addNodeAdjacentEdges(QuadEdge[] triEdges) {
			boolean hasNodeAdjacent = false;
			for (int i = 0; i < 3; i++) {
				WatershedVertex v0 = (WatershedVertex) triEdges[i].orig();
				WatershedVertex v1 = (WatershedVertex) triEdges[i].dest();
				if (addNodeAdjacentEdges(v0, v1))
					hasNodeAdjacent = true;
				if (addNodeAdjacentEdges(v1, v0))
					hasNodeAdjacent = true;
			}
			return hasNodeAdjacent;
		}

		// adds an edge if this is an edge from a start node to a non-constraint node
		private boolean addNodeAdjacentEdges(WatershedVertex v0, WatershedVertex v1) {
			if (v0.isNode() && !v1.isOnConstraint()) {
				midSegs.add(new Coordinate[] { v0.getCoordinate(), v1.getCoordinate() });
				return true;
			}
			return false;
		}
	}

}