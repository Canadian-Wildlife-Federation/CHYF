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

package net.refractions.chyf.watershed;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import net.refractions.chyf.watershed.debug.WatershedDebug;
import net.refractions.chyf.watershed.model.Region;
import net.refractions.chyf.watershed.model.WatershedBoundaryEdge;
import net.refractions.chyf.watershed.model.WatershedEdge;
import net.refractions.chyf.watershed.model.WatershedTriangle;
import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Extracts watershed edges from the TIN labelled with region IDs. Watershed
 * edges are determined by all triangle edges which separate triangles in
 * different regions. This extracter finds all (maximal length) edge sequences
 * for watershed boundaries.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class WatershedEdgeExtracter {
	// input data
	private QuadEdgeSubdivision subdiv;
	private GeometryFactory geomFactory;
	// computed data
	private Set<QuadEdge> extractedStartEdges = new HashSet<QuadEdge>();
	private List<WatershedEdgePath> wbPaths = new ArrayList<WatershedEdgePath>();

	/**
	 * Controls whether boundary edges lying on the border of the subdivision are
	 * extracted. Not required when merging is performed.
	 */
	private boolean isIncludingBorderEdges = false;

	public WatershedEdgeExtracter(QuadEdgeSubdivision subdiv, GeometryFactory geomFactory) {
		this.subdiv = subdiv;
		this.geomFactory = geomFactory;
	}

	/**
	 * Gets the boundary edges determined from the subdivision.
	 * 
	 * @return a list of WatershedBoundaryEdges
	 */
	public List<WatershedBoundaryEdge> getBoundaryEdges() {
		/**
		 * Boundary Edge nodes can be detected directly, and the edges incident on them
		 * extracted by tracing paths.
		 */
		extractEdgesFromNodes();
		/**
		 * Isolated Boundary edges (e.g. around isolated lakes) require a separate scan
		 * of the subdivision edges to find them
		 */
		extractIsolatedEdges();

		InWaterPathFinder.findInWaterPaths(wbPaths);
		return extractEdgesFromIncludedPaths(wbPaths);
	}

	private static List<WatershedBoundaryEdge> extractEdgesFromIncludedPaths(List<WatershedEdgePath> paths) {
		List<WatershedBoundaryEdge> edges = new ArrayList<WatershedBoundaryEdge>();
		for (WatershedEdgePath path : paths) {
			WatershedBoundaryEdge wbeTMP = path.extractBoundaryEdge();
			// System.out.println(wbeTMP);
			WatershedDebug.watchWBEdge(wbeTMP, new int[] { 2403064, 1290388 });

			if (!path.isInWater()) {
				WatershedBoundaryEdge wbe = path.extractBoundaryEdge();
				edges.add(wbe);
			}
		}
		return edges;
	}

	/**
	 * Extracts isolated boundary edges. Isolated edges are edges which do not touch
	 * any other edge (thus must form a ring). They occur around lakes and/or
	 * streams which lie completely within a larger drainage. They must be extracted
	 * separately, since their node points cannot be detected until all non-isolated
	 * edges have been extracted.
	 */
	@SuppressWarnings("unchecked")
	private void extractIsolatedEdges() {
		for (QuadEdge qe : (Collection<QuadEdge>)subdiv.getEdges()) {
			// don't process edges on border
			if (qe.getData() == null || qe.sym().getData() == null)
				continue;
			// skip frame triangle edges
			if (!isIncludedEdge(qe))
				continue;

			if (WatershedEdge.isOnBoundary(qe) && !isBoundaryExtracted(qe)) {
				extractBoundaryPath(qe, true);
				/**
				 * There is no need to add this edge to the extractedStartEdges, since we are
				 * now using the triangle marking to ensure edges are not processed twice
				 */
			}
		}
	}

	/**
	 * Tests whether an edge is a candidate to be included in a boundary line. Frame
	 * edges are never included, of course. Frame border edges may or may not be
	 * included.
	 * 
	 * @param qe a QuadEdge of the subdivision
	 * @return true if the edge may be included in a border line
	 */
	private boolean isIncludedEdge(QuadEdge qe) {
		/**
		 * frame edges are never included
		 */
		if (subdiv.isFrameEdge(qe))
			return false;
		// use all remaining edges if this is true
		if (isIncludingBorderEdges)
			return true;
		if (subdiv.isFrameBorderEdge(qe)) {
			return false;
		}
		return true;
	}

	/**
	 * Extracts the boundary edge starting along the given QuadEdge, and adds it to
	 * the list of extracted boundary edges. Marks the path as extracted.
	 * 
	 * @param qe the quadedge to start at
	 * @return the extracted path
	 */
	private WatershedEdgePath extractBoundaryPath(QuadEdge qe, boolean saveEdge) {
		/*
		 * WatershedDebug.watchEdge(qe, new Coordinate(1574326.9372, 600414.5245), new
		 * Coordinate(1574220.9084, 600423.307));
		 */

		WatershedEdgePath wep = new WatershedEdgePath(qe, geomFactory);
		// mark this path as being extracted
		wep.markEdgeTriangles();

		if (saveEdge)
			wbPaths.add(wep);
		return wep;
	}

	private static boolean isBoundaryExtracted(QuadEdge qe) {
		WatershedTriangle tri = (WatershedTriangle) qe.getData();
		if (tri == null)
			return true;
		return tri.isBoundaryExtracted();
	}

	/**
	 * Finds all boundary edge nodes in the subdivision.
	 * 
	 * @return a List of QuadEdges, one orginating at each boundary edge node
	 */
	@SuppressWarnings("unchecked")
	private List<QuadEdge> findBoundaryEdgeNodes() {
		List<QuadEdge> edgeNodes = new ArrayList<QuadEdge>();
		for (QuadEdge qe : (Collection<QuadEdge>)subdiv.getEdges()) {
			// don't bother checking non-included edges
			if (!isIncludedEdge(qe)) {
				// if (qe.orig().getCoordinate().distance(new Coordinate(1576958.9613868021,
				// 599919.128837511)) < .001)
				// Debug.println(qe.orig().getCoordinate());
				continue;
			}
			findBoundaryEdgeNode(qe, edgeNodes);
			// do sym as well, since there may be a node which only has edges pointing away
			// from it
			findBoundaryEdgeNode(qe.sym(), edgeNodes);
		}
		return edgeNodes;
	}

	/**
	 * Checks whether the given {@link QuadEdge} starts at a boundary node, and if
	 * so adds it to the given list of nodes. At most one edge is added for each
	 * boundary node.
	 * 
	 * @param qe            a quadedge to test
	 * @param boundaryNodes the list of edge nodes
	 */
	private void findBoundaryEdgeNode(QuadEdge qe, List<QuadEdge> boundaryNodeEdges) {
		// if (vert.getCoordinate().distance(new Coordinate(1579350.158603218,
		// 596589.7678357997)) <
		// .001)
		// Debug.println(vert.getCoordinate());

		Vertex vert = qe.orig();
		// skip outer triangle vertices
		if (subdiv.isFrameVertex(vert))
			return;

		WatershedVertex wv = (WatershedVertex) vert;

		if (wv.isScannedForBoundaryNode())
			return;
		wv.setScannedForBoundaryNode(true);

		if (isBoundaryNode(wv, qe)) {
			boundaryNodeEdges.add(qe);
			// System.out.println(WKTWriter.toPoint(qe.orig().getCoordinate()));
			wv.setBoundaryNode(true);
		}
	}

	/**
	 * Tests whether this vertex is a node in the watershed boundary edge graph.
	 * Vertices are nodes iff 3 or more boundary edges are incident on them.
	 * 
	 * @param v  the vertex to test
	 * @param qe a quadedge leaving the vertex
	 * @return true if this vertex is a node in the watershed boundary edge graph
	 */
	private static boolean isBoundaryNode(WatershedVertex v, QuadEdge qe) {
		// Assert: qe.orig() == v
		// WatershedDebug.watchPoint(qe.orig().getCoordinate(), new
		// Coordinate(1438626.88727,
		// 486385.25967));

		if (v.isNode())
			return true;

		int numBdyEdges = boundaryEdgeValence(qe);
		if (numBdyEdges >= 3) {
			return true;
		}
		return false;
	}

	/**
	 * Counts the number of boundary edges which are incident on the orgin vertex of
	 * the given {@link QuadEdge}. An edge is a boundary edge iff it has different
	 * regions on either side of it.
	 * 
	 * @param start a quadedge leaving the vertex around which to count
	 * @return the number of boundary edges which are incident on the vertex
	 */
	private static int boundaryEdgeValence(QuadEdge start) {
		QuadEdge qe = start;
		Region prevReg = WatershedEdge.getRegion(start.sym());
		int numBdyEdges = 0;
		do {
			Region currReg = WatershedEdge.getRegion(qe);
			if (currReg != prevReg) {
				// if regions on either side of edge are different, this is a boundary edge
				numBdyEdges += 1;
			}
			prevReg = currReg;
			qe = qe.oNext();
		} while (qe != start);
		return numBdyEdges;
	}

	private void extractEdgesFromNodes() {
		List<QuadEdge> nodes = findBoundaryEdgeNodes();
		for (QuadEdge qe : nodes) {
			extractEdgesLeavingNode(qe);
		}
	}

	/**
	 * Scans all edges leaving a node (supplied as a QuadEdge leaving the node
	 * vertex) and extracts all watershed boundary edges which start at the node (as
	 * long as they haven't already been extracted).
	 * 
	 * @param startQE an edge with the start node at its origin
	 */
	private void extractEdgesLeavingNode(QuadEdge startQE) {
		/*
		 * MD - disable for now, since it's very difficult and maybe impossible to
		 * determine which bdy edges in in-water. // for hydro nodes ONLY, mark
		 * triangles which are water, to allow avoiding generating in-water boundaries
		 * if ( ((WatershedVertex) startQE.orig()).isNode()) {
		 * markWaterTrianglesAroundNode(startQE); }
		 */
		QuadEdge qe = startQE;
		// System.out.println(WKTWriter.toPoint(qe.orig().getCoordinate()));
		do {
			// WatershedDebug.watchPoint(qe.orig().getCoordinate(), new
			// Coordinate(1438626.88727,
			// 486385.25967));

			if (WatershedEdge.isOnBoundary(qe) && WatershedEdge.isValidRegionEdge(qe) && isIncludedEdge(qe)
					&& !extractedStartEdges.contains(qe)) {

				/**
				 * If edge is in-water, extract it but don't save it, since in-water boundaries
				 * are not of interest. Extracting it ensures that the logic to prevent
				 * re-extraction still works.
				 */
				boolean saveEdge = true;
				/*
				 * MD - disable for now if (WatershedEdge.isAdjacentToWater(qe)) saveEdge =
				 * false;
				 */
				WatershedEdgePath wep = extractBoundaryPath(qe, saveEdge);

				// record terminal edges as being extracted, so edge won't be processed twice
				extractedStartEdges.add(wep.getFirstEdge());
				QuadEdge endQE = wep.getLastEdge();
				extractedStartEdges.add(endQE.sym());
			}
			qe = qe.oNext();
		} while (qe != startQE);
	}

}