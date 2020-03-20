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

package net.refractions.chyf.watershed.hydrograph;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.util.Debug;

import net.refractions.chyf.watershed.model.HydroEdge;

/**
 * A very simple pseudo-graph structure for of hydrographic edges. The only
 * function of this structure is to track minimal information about the topology
 * of the network at nodes.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class HydroGraph {
	public static HydroGraph buildHydroGraph(Collection<HydroEdge> hydroEdges) {
		HydroGraph hydroGraph = new HydroGraph();
		for (HydroEdge e : hydroEdges) {
			hydroGraph.addEdge(e);
		}
		return hydroGraph;
	}

	public Map<Coordinate, HydroNode> nodeMap = new TreeMap<Coordinate, HydroNode>();

	public HydroGraph() {
	}

	public void addEdge(HydroEdge e) {
		LineString line = e.getLine();
		// int numPts =
		line.getNumPoints();
		HydroNode node0 = findOrAddNode(line.getCoordinateN(0));
		Integer edgeID = e.getDrainageID();
		node0.addEdge(edgeID, e);
		HydroNode node1 = findOrAddNode(line.getCoordinateN(line.getNumPoints() - 1));
		node1.addEdge(edgeID, e);
	}

	private HydroNode findOrAddNode(Coordinate pt) {
		if (pt.distance(new Coordinate(1351607.2217, 1083019.1917)) < .001)
			Debug.println(pt);

		HydroNode node = getNode(pt);
		if (node == null) {
			node = new HydroNode(pt);
			nodeMap.put(pt, node);
		}
		return node;
	}

	/**
	 * @param pt
	 * @return the node for this coordinate
	 * @return null if the node does not exist
	 */
	public HydroNode getNode(Coordinate pt) {
		HydroNode node = (HydroNode) nodeMap.get(pt);
		return node;
	}

	/**
	 * Tests whether a given point is a true (degree > 1) node in the hydro graph.
	 * 
	 * @param pt
	 * @return true if the pt is a true node
	 */
	/*
	 * public boolean isBoundaryNode(Coordinate pt) { // if (pt.distance(new
	 * Coordinate(1258184.8629, 1632269.4972)) < .001) // Debug.println(pt);
	 * HydroNode node = (HydroNode) nodeMap.get(pt); if (node == null) return false;
	 * boolean isStartNode = node.isBoundaryNode(); return isStartNode; }
	 */

}