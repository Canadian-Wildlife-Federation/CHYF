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
import java.util.Collections;
import java.util.List;

import org.locationtech.jts.triangulate.quadedge.EdgeConnectedTriangleTraversal;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeTriangle;
import org.locationtech.jts.triangulate.quadedge.TraversalVisitor;

import net.refractions.chyf.watershed.model.Region;
import net.refractions.chyf.watershed.model.WatershedEdge;
import net.refractions.chyf.watershed.model.WatershedTriangle;

/**
 * Merges all disconnected areas of triangles into adjacent regions. Triangles
 * are processed in order of their "spill height". The spill height of an edge
 * is the pair (lowest vertex height, slope). This ensures that the triangles at
 * the "mouth" or low point of a disconnected area are used to choose the region
 * the area is assigned to. All connected triangles in a disconnected area are
 * merged into the same region.
 * <p>
 * Typically disconnected areas occur because the flow pattern across the TIN
 * causes a trickle line to "jump" across a(some) triangle(s) in a different
 * region. This is due to the discrete, faceted, nature of the TIN. Flow may
 * follow a slope which is too narrow to be represented in the TIN, so the flow
 * will appear to "skip over" the narrow section.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class DisconnectedAreaMerger {
	private Collection<WatershedTriangle> triangles;

	public DisconnectedAreaMerger(Collection<WatershedTriangle> triangles) {
		this.triangles = triangles;
	}

	public void merge() {
		// Collections.sort(discTris, new WatershedTriangle.MinHeightComparator());
		List<DisconnectedFrontTriangle> discTris = findDisconnectedFrontTris(triangles);
		// merge repeatedly until all disconnected triangles are merged
		do {
			Collections.sort(discTris);
			mergeOnce(discTris);
			discTris = filterDisconnectedFrontTris(discTris);
		} while (!discTris.isEmpty());
	}

	/**
	 * Given a list of {@link WatershedTriangle}s, creates
	 * {@link DisconnectedFrontTriangle}s for those which are disconnected and which
	 * are adjacent to a connected triangle (i.e are on the "front" of disconnected
	 * areas).
	 * 
	 * @param triangles List of WatershedTriangle
	 * @return a list of disconnected triangles on the front of disconnected areas
	 */
	private List<DisconnectedFrontTriangle> findDisconnectedFrontTris(Collection<WatershedTriangle> triangles) {
		List<DisconnectedFrontTriangle> discTris = new ArrayList<DisconnectedFrontTriangle>();
		for (WatershedTriangle tri : triangles) {
			// WatershedDebug.watchTriangle(tri, new Coordinate(1581071.7571953444,
			// 589086.1355841735));

			// keep only disconnected tris with an edge adjacent to a connected triangle
			if (tri.isConnected())
				continue;

			if (DisconnectedFrontTriangle.isOnDisconnectedFront(tri)) {
				DisconnectedFrontTriangle dfTri = new DisconnectedFrontTriangle(tri);
				discTris.add(dfTri);
			}
		}
		return discTris;
	}

	/**
	 * Given a list of {@link WatershedTriangle}s or
	 * {@link DisconnectedFrontTriangle}s, extracts those which are disconnected and
	 * which are adjacent to a connected triangle (i.e are on the "front" of
	 * disconnected areas).
	 * 
	 * @param triangles List of WatershedTriangle or DisconnectedFrontTriangle
	 * @return a list of disconnected triangles on the front of disconnected areas
	 */
	private List<DisconnectedFrontTriangle> filterDisconnectedFrontTris(
			Collection<DisconnectedFrontTriangle> triangles) {
		List<DisconnectedFrontTriangle> discTris = new ArrayList<DisconnectedFrontTriangle>();
		for (DisconnectedFrontTriangle dfTri : triangles) {
			WatershedTriangle tri = dfTri.getTriangle();

			// keep only disconnected tris with an edge adjacent to a connected triangle
			if (tri.isConnected())
				continue;

			dfTri.updateSpillFrontEdge();
			discTris.add(dfTri);
		}
		return discTris;
	}

	private void mergeOnce(Collection<DisconnectedFrontTriangle> discTris) {
		for (DisconnectedFrontTriangle discTri : discTris) {
			WatershedTriangle tri = discTri.getTriangle();

			// WatershedDebug.watchTriangle(tri, new Coordinate(941231.8492917134,
			// 560224.3728875755));
			// WatershedDebug.watchTriangle(tri, new Coordinate(941226.446003614,
			// 560234.3357488386));

			// skip triangles which have become connected
			if (tri.isConnected())
				continue;

			// If there is no merge candidate skip this triangle
			if (!discTri.hasSpillEdge())
				continue;

			Region spillRegion = discTri.getSpillRegion();
			/**
			 * The region for the disconnected area is used to determine which triangles to
			 * merge. Assign all triangles edge-connected to the start triangle and in same
			 * region to the new region.
			 */
			Region discAreaRegion = tri.getRegion();
			DisconnectedAreaRegionAssigner.connectToRegion(tri, spillRegion);
			DisconnectedAreaRegionAssigner.compute(tri, discAreaRegion, spillRegion);
		}
	}
}

/**
 * A wrapper for {@link WatershedTriangle}s which provides an ordering based on
 * the lowest edge. The lowest edge is the edge with:
 * <ul>
 * <li>the lowest vertex elevation, or
 * <li>if two lowest vertices are equal in elevation, the edge with the smallest
 * slope.
 * </ul>
 * (Physically, this represents the edge out which a trickle would flow).
 * 
 * @author Martin Davis
 * @version 1.0
 */
class DisconnectedFrontTriangle implements Comparable<DisconnectedFrontTriangle> {
	private WatershedTriangle tri;
	private int spillEdgeIndex = -1;
	private double lowestElev = Double.MAX_VALUE;
	private double lowestSlope = Double.MAX_VALUE;

	public DisconnectedFrontTriangle(WatershedTriangle tri) {
		this.tri = tri;
		updateSpillFrontEdge();
	}

	public WatershedTriangle getTriangle() {
		return tri;
	}

	public int getSpillEdgeIndex() {
		return spillEdgeIndex;
	}

	public boolean hasSpillEdge() {
		return spillEdgeIndex >= 0;
	}

	/**
	 * Finds the lowest edge (the spill edge) for this triangle. The triangle will
	 * only have a spill edge if it is adjacent to a connected triangle. Updates the
	 * instance variables appropriately.
	 */
	public void updateSpillFrontEdge() {
		spillEdgeIndex = -1;
		for (int i = 0; i < 3; i++) {

			if (!isNeighbourConnected(tri, i))
				continue;

			QuadEdge e = tri.getEdge(i);
			double elev = WatershedEdge.getMinimumHeight(e);
			double slope = WatershedEdge.getSlope(e);

			if (elev < lowestElev || elev == lowestElev && slope < lowestSlope) {
				lowestElev = elev;
				lowestSlope = slope;
				spillEdgeIndex = i;
			}
		}
	}

	public static boolean isOnDisconnectedFront(WatershedTriangle tri) {
		Region region = tri.getRegion();
		for (int i = 0; i < 3; i++) {
			WatershedTriangle neighTri = (WatershedTriangle) tri.getAdjacentTriangleAcrossEdge(i);
			Region neighRegion = neighTri.getRegion();
			if (region != neighRegion)
				return true;
		}
		return false;
	}

	private static boolean isNeighbourConnected(WatershedTriangle tri, int i) {
		WatershedTriangle neighTri = (WatershedTriangle) tri.getAdjacentTriangleAcrossEdge(i);
		// Debug.println("neighb = " + neighTri);
		// neighbour might be null if triangle is on area border
		if (neighTri == null)
			return false;
		// only connect to connected areas
		return neighTri.isConnected();
	}

	public int compareTo(DisconnectedFrontTriangle dt) {
		if (lowestElev < dt.lowestElev)
			return -1;
		if (lowestElev > dt.lowestElev)
			return 1;
		// elevations must be the same
		if (lowestSlope < dt.lowestSlope)
			return -1;
		if (lowestSlope > dt.lowestSlope)
			return 1;
		return 0;
	}

	public Region getSpillRegion() {
		WatershedTriangle neighTri = (WatershedTriangle) tri.getAdjacentTriangleAcrossEdge(spillEdgeIndex);

		// neighbour might be null if triangle is on area border
		if (neighTri == null)
			return null;
		// only connect to connected areas
		if (!neighTri.isConnected())
			return null;

		Region region = neighTri.getRegion();
		return region;
	}
}

/**
 * An algorithm class to set the region for all disconnected (null-region)
 * {@link WatershedTriangle}s which are edge-connected to a given triangle to be
 * the given
 * 
 * @link Region}.
 * @author Martin Davis
 * @version 1.0
 */
class DisconnectedAreaRegionAssigner implements TraversalVisitor {
	public static void compute(WatershedTriangle startTri, Region startRegion, Region newRegion) {
		EdgeConnectedTriangleTraversal traversal = new EdgeConnectedTriangleTraversal();
		traversal.init(startTri);
		traversal.visitAll(new DisconnectedAreaRegionAssigner(startRegion, newRegion));
	}

	public static void connectToRegion(WatershedTriangle tri, Region newRegion) {
		tri.getRegion().remove(tri);
		newRegion.add(tri);
		tri.setConnected(true);
	}

	private Region startRegion;
	private Region newRegion;

	public DisconnectedAreaRegionAssigner(Region startRegion, Region newRegion) {
		this.startRegion = startRegion;
		this.newRegion = newRegion;
	}

	public boolean visit(QuadEdgeTriangle currTri, int edgeIndex, QuadEdgeTriangle neighTri) {
		WatershedTriangle tri = (WatershedTriangle) neighTri;
		// stop searching when we hit the edge of the disconnected mergeRegion area
		if (tri.isConnected())
			return false;
		if (tri.getRegion() != startRegion)
			return false;

		connectToRegion(tri, newRegion);
		return true;
	}

}