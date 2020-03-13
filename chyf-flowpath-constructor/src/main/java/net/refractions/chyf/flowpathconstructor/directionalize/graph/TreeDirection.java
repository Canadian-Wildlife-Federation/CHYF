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
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.DirectionType;

/**
 * Directionalizes a non-directionalized tree structured
 * based on a set of sink nodes.  The order of the sink nodes
 * matters.  The first sink node is the primary and if a path
 * exists from any node to that sink that path is used for
 * directionalizing first.  Then the next node is processed
 * etc until all edges are directionalized.  Known direction edges
 * are kept.
 * 
 * @author Emily
 *
 */
public class TreeDirection {
	private static final Logger logger = LoggerFactory.getLogger(TreeDirection.class.getCanonicalName());

	/**
	 * Order of the sinks matters, especially in cases where
	 * most of the graph is not directionalized.
	 * 
	 * @param graph
	 * @param localSinks
	 */
	public void directionalize(DGraph graph, List<DNode> localSinks) throws Exception {
		graph.edges.forEach(e->e.visited = false);
		
		//process main sink first
		DNode sink = localSinks.get(0);
		visitUpstream(sink);
		
		if (localSinks.size() != 1) {
			
			
			//process remaining sinks; walking up a source node is not created
			HashSet<DEdge> temp = new HashSet<>();
			
			for (int i = 1; i < localSinks.size(); i ++) {
				sink = localSinks.get(i);
				//walk up until first degree 3 node
				DNode prev = sink;
				List<DNode> toVisit = new ArrayList<>();
				while(sink != null) {
					toVisit.add(sink);
					if (sink.getDegree() > 2) {
						//did I create a source node; if yes
						boolean issrc = true;
						for (DEdge e : sink.getEdges()) {
							if (e.getNodeB() == sink) {
								issrc = false;
							}
						}
						if (!issrc) break;
					}
					
					DEdge e = null;
					if (sink.getEdges().size() == 1) {
						e = sink.getEdges().get(0);
					}else if (sink.getEdges().size() == 2){
						e = sink.getEdges().get(0);
						if (e.getOtherNode(sink) == prev) {
							e = sink.getEdges().get(1);
						}
					}else {
						//pick one that isn't in the hashset
						for (DEdge g : sink.getEdges()) {
							if (!temp.contains(g)) e= g;
						}
					}
					if (e == null) {
						logger.error("Could not find an edge to walk up from sink node: " + localSinks.get(i).toString() + ".  An internal source node will be created at " + sink.toString());
						break;
					}
					if (e.getNodeB() != sink) {
						e.flip();
					}else {
						e.setKnown();
					}
					e.visited = true;
					temp.add(e);
					prev = sink;
					sink = e.getOtherNode(sink);
				}
				
				//walk up any unvisited edges that go into one of the nodes above
				for (DNode d : toVisit) visitUpstream(d);
			}
			
			
			
		}
		
		for (DEdge d : graph.edges) {
			if (!d.visited) {
				logger.error("Not all edges visited when directionalizing tree.  At least one eddge missed: " + d.toString());
			}
		}
	}
	
	private void visitUpstream(DNode sink) throws Exception{
		List<DNode> tovisit = new ArrayList<>();
		tovisit.add(sink);
		while(!tovisit.isEmpty()) {
			DNode current = tovisit.remove(0);
	
			for (DEdge e : current.getEdges()) {
				if (e.visited) continue;
				if (e.getDType() == DirectionType.KNOWN) {
					if (e.getNodeB() == current) {
						e.visited = true;
						tovisit.add(e.getOtherNode(current));
					}else {
						//we want to flip but we can't 
						//maybe there is another sink node that can 
						//flip this later
					}
				}else {
					e.visited = true;
					if (e.getNodeB() != current) {
						e.flip();
					}else {
						e.setKnown();
					}
					tovisit.add(e.getOtherNode(current));
				}
			}
		}
	}
	
}
