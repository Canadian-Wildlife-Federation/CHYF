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

import java.util.Collection;

import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.locationtech.jts.util.Assert;

import net.refractions.chyf.watershed.model.Region;
import net.refractions.chyf.watershed.model.WatershedTIN;
import net.refractions.chyf.watershed.model.WatershedTriangle;
import net.refractions.chyf.watershed.model.WatershedVertex;
import net.refractions.chyf.watershed.model.WatershedVertexUtil;

/**
 * Assigns a region to flat triangles, where possible. Flat triangles occur
 * where the vertices of the triangle are all on constraints. After Medial Axis
 * Refinement, the only flat triangles which should remain are pathological ones
 * where all vertices are nodes, and where each node is incident on two
 * different Drainage IDs.
 * <p>
 * The only case which should occur in practice is where there is a unique
 * Drainage ID which is common to all 3 nodes. In this case, the region for the
 * triangle is determined by that Drainage ID. This class detects these
 * triangles, and assigns the region appropriately.
 * <p>
 * <b>NOTE: </b> Currently this algorithm is unused, since it is more effective
 * to assign regions to flat triangles earlier in the process.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class FlatTriangleAssigner {
	public static void assign(WatershedTIN watershedTIN, Collection<WatershedTriangle> triangles) {
		FlatTriangleAssigner fta = new FlatTriangleAssigner(watershedTIN, triangles);
		fta.compute();
	}

	private WatershedTIN watershedTIN;
	private Collection<WatershedTriangle> triangles;

	public FlatTriangleAssigner(WatershedTIN watershedTIN, Collection<WatershedTriangle> triangles) {
		this.watershedTIN = watershedTIN;
		this.triangles = triangles;
	}

	public void compute() {
		/**
		 * It is sufficient to examine only tris with no region assigned yet (since all
		 * others should have been assigned by trickling).
		 */
		for (WatershedTriangle tri : triangles) {
			// WatershedDebug.watchTriangle(tri, new Coordinate(1728703.1658988409,
			// 612527.9821648226));
			if (!tri.hasRegion()) {
				assignRegionToFlatTriangle(tri);
			}
		}
	}

	private void assignRegionToFlatTriangle(WatershedTriangle tri) {
		if (!tri.isFlatAtConstraintHeight())
			return;

		Vertex[] vert = tri.getVertices();

		/**
		 * Can only determine region for triangle where all vertices are constrained
		 */
		if (!WatershedVertex.isAllNodes(vert)) {
			return;
			// Assert.shouldNeverReachHere("Flat triangle with non-node vertices found: " +
			// tri);
		}
		Region commonRegion = findUniqueCommonRegion(vert);
		if (commonRegion != null) {
			commonRegion.add(tri);
			return;
		}
		Assert.shouldNeverReachHere("Flat constrained triangle with no common region found: " + tri);
	}

	private WatershedVertex[] wv = new WatershedVertex[3];

	public Region findUniqueCommonRegion(Vertex[] vert) {
		for (int i = 0; i < 3; i++) {
			wv[i] = (WatershedVertex) vert[i];
		}
		int commonRegionID = WatershedVertexUtil.findCommonRegion(wv);
		if (commonRegionID == Region.NULL_ID)
			return null;
		return watershedTIN.getRegionFactory().getRegion(commonRegionID);
	}
}