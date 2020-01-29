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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;

/**
 * Graph used for processing skeletons
 * @author Emily
 *
 */
public class DGraph {
	
	public List<DNode> nodes = new ArrayList<>();
	public List<DEdge> edges = new ArrayList<>();

	/*
	 * In cases where this graph is a subgraph of a
	 * parent graph, this field contains
	 * the node in the parent graph that
	 * represents this subgraph
	 */
	protected DNode collapseNode = null;
	
	/**
	 * Creates a new skeleton graph from a set of segments and inout points
	 * 
	 * This linestring usedata must be EdgeInfo class
	 * @param segments
	 * @param inout
	 * @return
	 */
	public static DGraph buildGraphLines(Collection<EdgeInfo> segments) {
		DGraph graph = new DGraph();
		HashMap<Coordinate, DNode> nodes = new HashMap<>();
		
		for (EdgeInfo ls : segments) {
			
			Coordinate c0 = ls.getStart();//ls.getCoordinateN(0);
			Coordinate c1 = ls.getEnd(); //ls.getCoordinateN(ls.getCoordinates().length - 1);
			
			DNode a1 = graph.createNode(c0);
			DNode a2 = graph.createNode(c1);
			
			if (nodes.containsKey(c0)) {
				a1 = nodes.get(c0);
			}else {
				nodes.put(c0, a1);
				graph.nodes.add(a1);
			}
			if (nodes.containsKey(c1)) {
				a2 = nodes.get(c1);
			}else {
				nodes.put(c1, a2);
				graph.nodes.add(a2);
			}
						
			DEdge e = graph.createEdge(a1, a2, ls);
			a1.getEdges().add(e);
			a2.getEdges().add(e);
			graph.edges.add(e);
		}
		return graph;
	}
	
	
	public DGraph() {
	}
	
	public DGraph(List<DNode> nodes, List<DEdge> edges) {
		this.nodes = nodes;
		this.edges = edges;
	}
	
	
	public List<DNode> getNodes(){
		return this.nodes;
	}
	
	public List<DEdge> getEdges(){
		return this.edges;
	}
	
	private DNode createNode(Coordinate cc) {
		return new DNode(cc);
	}
	private DEdge createEdge(DNode a1, DNode a2, EdgeInfo info) {
		return new DEdge(a1, a2, info);
	}
	
	
	/**
	 * In cases where multiple edges exists with the same nodes
	 * we remove all but one of the edges.
	 * 
	 * @throws Exception
	 */
	public void removeSameEdges() throws Exception {
		List<DEdge> remove = new ArrayList<>();
		
		for (DNode d : nodes) {
			for (int i = 0 ; i < d.getEdges().size() ;i ++) {
				DEdge out1 = d.getEdges().get(i);
				for (int j = i + 1; j < d.getEdges().size(); j ++) {
					DEdge out2 = d.getEdges().get(j);
			
					if ((out1.getNodeA() == out2.getNodeA() && out1.getNodeB() == out2.getNodeB()) ||
						(out1.getNodeA() == out2.getNodeB() && out1.getNodeB() == out2.getNodeA())) {
						//remove out2
						out1.addSameEdge(out2);
						remove.add(out2);
					}
				} 
				if (out1.getNodeA() == out1.getNodeB()){
					throw new Exception("Circular reference found in graph: " + out1.getNodeA().toString());
				}
			}
		}
		
		getEdges().removeAll(remove);
		for (DEdge e : remove) {
			e.getNodeA().getEdges().remove(e);
			e.getNodeB().getEdges().remove(e);
		}
	}
}
