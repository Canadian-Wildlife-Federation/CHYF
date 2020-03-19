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
import java.util.List;

import org.locationtech.jts.triangulate.quadedge.EdgeConnectedTriangleTraversal;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeTriangle;
import org.locationtech.jts.triangulate.quadedge.TraversalVisitor;

import net.refractions.chyf.watershed.model.WatershedTriangle;

/**
 * Marks edge-connected regions (starting at constrained triangles). Nulls
 * region for disconnected triangles found.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class ConnectedRegionFinder implements TraversalVisitor {
	/**
	 * Marks edge-connected regions (starting at constrained triangles). Triangles
	 * in disconnected areas will be left with a null region attribute.
	 * 
	 * @param triangles a collection of {@link WatershedTriangle}s
	 */
	public static void compute(Collection<WatershedTriangle> triangles) {
		EdgeConnectedTriangleTraversal traversal = new EdgeConnectedTriangleTraversal();
		traversal.init(findInitialConnected(triangles));
		traversal.visitAll(new ConnectedRegionFinder());
	}

	public ConnectedRegionFinder() {
	}

	private static List<WatershedTriangle> findInitialConnected(Collection<WatershedTriangle> triangles) {
		List<WatershedTriangle> initItems = new ArrayList<WatershedTriangle>();
		// init queue
		/**
		 * All triangles are initially set to unconnected. The queue is initialized with
		 * triangles which are defined to be connected. These are: - border triangles -
		 * triangles adjacent to constraint edges - triangles adjacent to pit vertices
		 */
		for (WatershedTriangle tri : triangles) {
			// WatershedDebug.watchTriangle(tri, new Coordinate(1581071.7571953444,
			// 589086.1355841735));
			boolean isConnected = false;
			if (tri.isConstrained() || tri.isBorder())
				isConnected = true;

			if (isConnected) {
				tri.setConnected(true);
				initItems.add(tri);
				// if (tri.getRegion().getID() == 155416) { System.out.println(tri); }
			}
		}
		return initItems;
	}

	/*
	 * // MD - no longer used private static boolean hasPitVertex(WatershedTriangle
	 * tri) { for (int i = 0; i < 3; i++) { WatershedVertex v = (WatershedVertex)
	 * tri.getVertex(i); Region reg = v.getRegion(); if (reg != null &&
	 * reg.getType() == Region.TYPE_PIT) return true; } return false; }
	 */

	public boolean visit(QuadEdgeTriangle currTri, int edgeIndex, QuadEdgeTriangle neighTri) {
		WatershedTriangle currTriW = (WatershedTriangle) currTri;
		WatershedTriangle neighTriW = (WatershedTriangle) neighTri;

		// WatershedDebug.watchTriangle(neighTriW, new Coordinate(1581071.7571953444,
		// 589086.1355841735));

		if (neighTriW.isConnected())
			return false;
		if (neighTriW.getRegion() != currTriW.getRegion())
			return false;
		// the neighTri is in the same region as the current tri AND is edge-adjacent
		// ==> it is
		// connected
		neighTriW.setConnected(true);

		// output a line showing how connectedness is transferred
		// if (neighTriW.getRegion().getID() == 155416)
		// System.out.println(WKTWriter.toLineString(currTriW.getCentroid(),
		// neighTriW.getCentroid()));
		return true;
	}

}