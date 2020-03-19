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
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.locationtech.jts.util.Assert;

import net.refractions.chyf.util.KDTree;
import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Determines the closest constraint vertex for vertices, by using spatial
 * searches in boxes of increasing size around a vertex. A spatial index is used
 * to speed up the searching.
 * 
 * @author Martin Davis
 */
public class IndexedClosestConstraintVertexFinder {
	//private KdTree index = null; // null if no constraint vertices loaded
									// (including if none supplied)
	//private double searchBoxInitialSize = 0.001;
	//private double searchBoxSizeIncrementFactor = 2.0;
	private KDTree<Vertex> index = null; // null if no constraint vertices loaded

	public IndexedClosestConstraintVertexFinder(List<? extends Vertex> constraintVertices) {
		if (constraintVertices.size() > 0) {
//			index = new KdTree();
//			loadIndex(constraintVertices);
			index = new KDTree<Vertex>(constraintVertices, v -> v.getCoordinate());
		}
	}

//	public void setSearchParameters(double searchBoxInitialSize, double searchBoxSizeIncrementFactor) {
//		this.searchBoxInitialSize = searchBoxInitialSize;
//		this.searchBoxSizeIncrementFactor = searchBoxSizeIncrementFactor;
//	}

//	private void loadIndex(Collection<? extends Vertex> vertices) {
//		for (Vertex v : vertices) {
//			index.insert(v.getCoordinate(), v);
//		}
//	}

	public void assignClosest(Collection<QuadEdge> quadEdges) {
		if (index == null)
			return;
		for (QuadEdge qe : quadEdges) {
			assignClosest((WatershedVertex) qe.orig());
			assignClosest((WatershedVertex) qe.dest());
		}
	}

	public void assignClosest(WatershedVertex v) {
		// check if nothing to do
		if (index == null)
			return;
		if (v.getClosestVertex() != null)
			return;

		Coordinate pt = v.getCoordinate();
		Vertex closestVert = findClosest(pt);

		// assert: closestVert != null
		Assert.isTrue(closestVert != null, "Can't find a closest constraint vertex");
		v.setClosestVertex(closestVert);
	}

	private Vertex findClosest(Coordinate pt) {
		List<Vertex> closest = index.query(pt, 1, null);
		return closest.get(0);
//		Collection<KdNode> kdNodes = findNearKdNodes(pt);
//
//		double minDist = Double.MAX_VALUE;
//		Vertex closestVert = null;
//		for (KdNode node : kdNodes) {
//			Coordinate candidateClosest = node.getCoordinate();
//			double dist = pt.distance(candidateClosest);
//			if (dist < minDist) {
//				minDist = dist;
//				closestVert = (Vertex) node.getData();
//			}
//		}
//		return closestVert;
	}

	/**
	 * Searches circles of increasing size around the given pt until at least one
	 * item is found. It is <b>essential</b> to use circles as the search filter
	 * (e.g. NOT boxes), since otherwise some situations can produce a false
	 * positive result (i.e. when the search box retrieves a single point in the
	 * corner of the box - there may be one just outside the middle of a side which
	 * is closer)
	 * 
	 * @return
	 */
//	public List<KdNode> findNearKdNodes(Coordinate pt) {
//		double radius = searchBoxInitialSize / 2;
//		do {
//			List<KdNode> nearNodes = queryCircle(pt, radius);
//			if (!nearNodes.isEmpty())
//				return nearNodes;
//
//			radius *= searchBoxSizeIncrementFactor;
//		} while (true);
//	}

//	private List<KdNode> queryCircle(Coordinate pt, double radius) {
//		Envelope searchEnv = new Envelope(pt);
//		searchEnv.expandBy(2 * radius);
//		@SuppressWarnings("unchecked")
//		List<KdNode> nodes = index.query(searchEnv);
//		if (nodes.isEmpty())
//			return nodes;
//		return filterInCircle(pt, radius, nodes);
//	}
//
//	private List<KdNode> filterInCircle(Coordinate pt, double radius, List<KdNode> boxNodes) {
//		List<KdNode> result = new ArrayList<KdNode>();
//		for (KdNode node : boxNodes) {
//			Coordinate nodePt = node.getCoordinate();
//			if (nodePt.distance(pt) < radius)
//				result.add(node);
//		}
//		return result;
//	}
}
