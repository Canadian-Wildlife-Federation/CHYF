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
import java.util.List;

import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.util.Assert;
import org.locationtech.jts.util.AssertionFailedException;

import net.refractions.chyf.watershed.hydrograph.HydroNode;
import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.OrientedHydroEdge;
import net.refractions.chyf.watershed.model.WatershedTriangle;
import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Models the TIN edges ({@link QuadEdge}s) and facets incident on a hydro node.
 * Supports determining the constraint edges for all quadedges which have them.
 * This allows:
 * <ul>
 * <li>Determining the in-water state for each TIN facet around the node
 * <li>Checking that each hydro edge incident on the node can be traced to a
 * quadedge. If this is not the case, this indicates that the TIN is not
 * faithful to the hydro edge geometry, which may indicate a constraint
 * enforcement failure (possibly due to snapping). If not all hydro edges are
 * matched to quadedges, the in-water assignment will be incorrect, which can
 * cause further errors (e.g. some boundary edges may be incorrectly determined
 * to be in-water, and thus omitted from the output).
 * </ul>
 * 
 * @author Martin Davis
 */
public class HydroNodeEdgeStar {
	public static HydroNode getNode(QuadEdge nodeQE) {
		WatershedVertex v = (WatershedVertex) nodeQE.orig();
		HydroNode node = v.getNode();
		return node;
	}

	private static final int LANDSTATE_UNKNOWN = 0;
	private static final int LANDSTATE_LAND = 1;
	private static final int LANDSTATE_WATER = 2;

	private QuadEdge nodeQE;
	private HydroNode hydroNode;
	private List<EdgeInfo> quadEdgeInfo = new ArrayList<EdgeInfo>();
	private int quadEdgeCount = 0;
	private int hydroEdgeCount = 0;

	public HydroNodeEdgeStar(QuadEdge nodeQE) {
		this.nodeQE = nodeQE;
		hydroNode = getNode(nodeQE);
		extractEdges(nodeQE);
		quadEdgeCount = quadEdgeInfo.size();
	}

	private void extractEdges(QuadEdge startQE) {
		QuadEdge qe = startQE;
		do {
			OrientedHydroEdge ohe = QuadedgeConstraintFinder.getOrientedHydroEdge(qe);
			if (ohe != null)
				hydroEdgeCount++;
			quadEdgeInfo.add(new EdgeInfo(qe, ohe));

			// get the next edge CCW
			qe = qe.oNext();
		} while (qe != startQE);
	}

	private void checkHydroEdgeCountConsistent() {
		// Should have every hydro edge matched to a quad edge
		Assert.isTrue(hydroNode.getDegree() == hydroEdgeCount,
				"Cannot determine hydro edge for TIN edge (possible short segment) at node "
						+ WKTWriter.toPoint(nodeQE.orig().getCoordinate()));
	}

	class EdgeInfo {
		QuadEdge qe;
		OrientedHydroEdge ohe;
		int landStateRight = LANDSTATE_UNKNOWN;

		EdgeInfo(QuadEdge qe, OrientedHydroEdge ohe) {
			this.qe = qe;
			this.ohe = ohe;
		}
	}

	public OrientedHydroEdge getOrientedHydroEdge(int i) {
		int ii = i % quadEdgeCount;
		return quadEdgeInfo.get(ii).ohe;
	}

	public QuadEdge getQuadEdge(int i) {
		int ii = i % quadEdgeCount;
		return quadEdgeInfo.get(ii).qe;
	}

	/**
	 * Tests whether there are any water triangles around this node This is only the
	 * case if OrientedHydroEdge.isSingleLine() is true for all edges around the
	 * node.
	 * 
	 * @return true if there are any water triangles around node
	 */
	public boolean hasWaterTriangles() {
		for (HydroEdge he : hydroNode.getEdges()) {
			if (!he.isSingleLine())
				return true;
		}
		return false;
	}

	private void markAllTrianglesAsLand() {
		for (int i = 0; i < quadEdgeCount; i++) {
			QuadEdge qe = getQuadEdge(i);
			WatershedTriangle tri = (WatershedTriangle) qe.getData();
			if (tri != null) {
				tri.setWater(false);
			}
		}
	}

	/**
	 * Marks water triangles around the vertex at this node. Uses the water-side
	 * indicator on the hydro edges.
	 */
	public void markWaterTrianglesAroundNode() {
		// short-circuit if no water triangles
		if (!hasWaterTriangles()) {
			markAllTrianglesAsLand();
			return;
		}
		try {
			checkHydroEdgeCountConsistent();
		} catch(AssertionFailedException afe) {
			afe.printStackTrace();
		}

		// Coordinate nodePt =
		nodeQE.orig().getCoordinate();
		// WatershedDebug.watchPoint(nodePt, new Coordinate(926118.491652294,
		// 722187.340153072));

		int startIndex = findNextConstraintEdgeIndex(0);

		// traverse CCW thru all edge/triangles around node, marking them appropriately
		// the landState is the state of the triangle to the R of the current edge
		HydroEdge he = null;
		int i = startIndex;
		// System.out.println("markWaterTrianglesAroundNode at " + startQE);
		int landState = LANDSTATE_UNKNOWN;
		do {
			// System.out.println(qe);
			// WatershedDebug.watchEdge(qe, new Coordinate(926118.491652294,
			// 722187.340153072), new
			// Coordinate(926117.5399, 722187.3545));
			QuadEdge qe = getQuadEdge(i);
			OrientedHydroEdge currohe = getOrientedHydroEdge(i);
			he = currohe != null ? currohe.getEdge() : null;
			// if this is a constraint edge, recompute the land state in case it changes
			if (he != null) {

				/**
				 * Unfortunately we can't always run this consistency check, since hydro edge
				 * trimming means that sometimes not all edges around a node are included, which
				 * causes an inconsistency. Instead, just let this pass. Also, ensure that
				 * triangles immediately adjacent to constraint edges are correctly marked.
				 * Hopefully this strategy does not result in in-water edges being incorrectly
				 * extracted.
				 */
				// checkConsistentLandState(ohe.isWaterRight(), landState, nodePt);
				landState = currohe.isWaterLeft() ? LANDSTATE_WATER : LANDSTATE_LAND;

				/**
				 * Set the state of the (previous) triangle on the R of the quadedge as well,
				 * just in case the land state topoloyg is inconsistent due to missing hydro
				 * edges
				 */
				WatershedTriangle prevTri = (WatershedTriangle) qe.sym().getData();
				if (prevTri != null) {
					prevTri.setWater(currohe.isWaterRight());
				}

			}
			// the triangle is on the L of (ahead of) the quadedge
			WatershedTriangle tri = (WatershedTriangle) qe.getData();
			if (tri != null) {
				tri.setWater(landState == LANDSTATE_WATER);
			}

			i = (i + 1) % quadEdgeCount;
		} while (i != startIndex);
	}

	/**
	 * Finds the index of the next edge which is a hydro constraint, starting at the
	 * given index.
	 * 
	 * @param startIndex
	 * @return the index of the next edge which is a constraint
	 */
	private int findNextConstraintEdgeIndex(int startIndex) {
		int index = startIndex;
		do {
			if (getOrientedHydroEdge(index) != null)
				return index;
			index++;
		} while (index < quadEdgeCount);
		return -1;
	}

}
