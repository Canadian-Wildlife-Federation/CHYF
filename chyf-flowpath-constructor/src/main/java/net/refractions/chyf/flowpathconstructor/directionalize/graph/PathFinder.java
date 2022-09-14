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
package net.refractions.chyf.flowpathconstructor.directionalize.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import net.refractions.chyf.datasource.DirectionType;

/**
 * Finds the shortest path between the source node and the sink node.
 * 
 * The shortest path is found between the source and sink node, then the path
 * is trimmed to the first node that is already part of a different path.
 *  
 * 
 * @author Emily
 *
 */
public class PathFinder {
	

	//the subgraph nodes can contain references to edges that are not
	/**
	 * Finds the shortest path from source to sink.  
	 * @param source source node
	 * @param sink sink nodes 
	 * @param graph graph
	 * @return
	 */
	public static DPath findPath(DNode source, Set<DNode> sink, DGraph graph) {
		
		HashMap<DNode, Object[]> prev = new HashMap<>();
		List<DNode> tovisit = new ArrayList<>();

		for (DNode n : graph.nodes) {
			n.pathdistance = Double.MAX_VALUE;
			n.pathvisited = false;
		}
		
		source.pathdistance = 0;
		tovisit.add(source);
		
		while(!tovisit.isEmpty()) {
			DNode max = null;
			//find the path nearest node
			for (DNode n : tovisit) {
				if (max == null || n.pathdistance < max.pathdistance) {
					max = n;
				}
			}

			if (sink.contains(max) ) {
				//stop
				//follow prev to source to get path
				DPath p = new DPath();
				DNode c = max;
				p.nodes.add(0, c);
				
				Object[] pv = prev.get(c);
				while(pv != null) {
					p.nodes.add(0, (DNode)pv[0]);
					p.edges.add(0, (DEdge)pv[1]);	
					pv = prev.get((DNode)pv[0]);	
				}
				
				//trim path to the first node that is used
				//in another node 
				for (int i = 0; i < p.nodes.size(); i ++) {
					if (p.nodes.get(i).pathnode) {
						//remove nodes starting at i + 1
						//remove edges starting at i
						p.nodes = p.nodes.subList(0, i+1);
						p.edges = p.edges.subList(0, i);
					}
				}
				return p;
			}
			
			tovisit.remove(max);
			max.pathvisited = true;
			
			for (DEdge e : max.getEdges()) {
				//if the subgraph nodes contain references to edges that are not
				//in the subgraph; we only want to follow edges in the subgraph
				if (!graph.edges.contains(e)) continue;
				if (e.getDType() == DirectionType.KNOWN) if (e.getNodeA() != max) continue;
				
				DNode o = e.getOtherNode(max);
				if (o.pathvisited) continue;
				
				double alt = max.pathdistance + e.getLength();
				if (alt < o.pathdistance) {
					o.pathdistance = alt;
					prev.put(o, new Object[] {max, e});
					tovisit.add(o);
				}
			}
		}
		return null;
		
	}
	
	

}
