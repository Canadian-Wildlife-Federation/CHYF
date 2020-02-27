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
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Using bridge edges, partitions the graph
 * into subcomponents.  Each subcomponents is identified
 * and a graph object created for them.  They are then collapsed
 * into a single node in the main graph.  Once partitioned the 
 * graph should form a tree.
 * 
 * Note: the nodes in the subgraphs still reference edges outside
 * the subgraph (this allows the code to figure out where they go in
 * the main graph).  
 * 
 * @author Emily
 *
 */
public class Partition {

	private DGraph graph;
	
	private List<DGraph> subgraphs = new ArrayList<>();
	
	/**
	 * 
	 * @return all subgraphs created by patitioning
	 */
	public List<DGraph> getSubGraphs(){
		return this.subgraphs;
	}
	
	/**
	 * Partitions the graph into subgraphs and collapses
	 * these subgraphs into a single node in the main graph.
	 * 
	 * @param graph
	 */
	public void partion(DGraph graph) {
		this.graph = graph;
		
		DEdge i = graph.edges.get(0);
		while(i != null) {
			i = null;
			
			for (DEdge edge : graph.edges) {
				if (!edge.isBridge()) {
					i = edge;
					break;
				}
			}
			if (i != null) makeGroup(i);
		}
	}
	
	private void makeGroup(DEdge e) {
		//find all nodes and edges that are part
		//of this group.  Do not traverse bridge edges
		
		List<DNode> toProcess = new ArrayList<>();
		toProcess.add(e.getNodeA());
		toProcess.add(e.getNodeB());
		
		Set<DNode> nodes = new HashSet<>();
		Set<DEdge> edges = new HashSet<>();
		
		Set<DEdge> bridgeEdges = new HashSet<>();
		
		while(!toProcess.isEmpty()) {
			DNode d = toProcess.remove(0);
			nodes.add(d);
			for (DEdge edge : d.getEdges()) {
				if (edge.isBridge()) {
					bridgeEdges.add(edge);
					continue;
				}
				if (edges.contains(edge)) continue;
				edges.add(edge);
				if (!nodes.contains(edge.getNodeA())) toProcess.add(edge.getNodeA());
				if (!nodes.contains(edge.getNodeB())) toProcess.add(edge.getNodeB());
			}
		}

		//collapse this group into a single node
		DGraph group = new DGraph();
		group.edges.addAll(edges);
		group.nodes.addAll(nodes);
		subgraphs.add(group);

		//new super node
		DNode node = new DNode(nodes.iterator().next().getCoordinate());
		group.collapseNode = node;
		
		for (DEdge b : bridgeEdges) {
			DNode n = b.getNodeA();
			if (nodes.contains(n)) {
				n = b.getNodeB();
				b.setNodeA(node);
			}else {
				b.setNodeB(node);
			}
			node.getEdges().add(b);
		}
		graph.edges.removeAll(edges);
		graph.nodes.removeAll(nodes);
		graph.nodes.add(node);
		
	}
}
