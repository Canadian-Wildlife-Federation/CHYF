/*
 * Copyright 2019 Government of Canada
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
 */
package net.refractions.chyf.directionalize.graph;

import java.util.ArrayList;
import java.util.List;

public class SubGraph {

	/**
	 * Creates a new graph that only contains the nodes and
	 * edges visited from the sink.  Direction is not taken into
	 * account.  Reuses DNode and DEdge objects.
	 * 
	 * @param graph
	 * @param sink
	 * @return
	 */
	public static DGraph computeSubGraph(DGraph graph, DNode sink) {
		
		graph.edges.forEach(e->e.visited = false);
		graph.nodes.forEach(e->e.visited = false);

		List<DEdge> toVisit = new ArrayList<>();
		toVisit.addAll(sink.getEdges());
		
		while(!toVisit.isEmpty()) {
			DEdge e = toVisit.remove(0);
			if (e.visited) continue;
			
			e.visited = true;
			e.getNodeA().visited = true;
			e.getNodeB().visited = true;
			toVisit.addAll(e.getNodeA().getEdges());
			toVisit.addAll(e.getNodeB().getEdges());
		}
		
		List<DNode> nodes = new ArrayList<>();
		List<DEdge> edges = new ArrayList<>();
		
		graph.edges.forEach(e->{if (e.visited) edges.add(e);});
		graph.nodes.forEach(e->{if (e.visited) nodes.add(e);});
		
		return new DGraph(nodes, edges);
		
	}
}
