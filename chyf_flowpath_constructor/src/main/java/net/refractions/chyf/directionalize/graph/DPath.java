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

/**
 * Represents a path in the graph
 * 
 * @author Emily
 *
 */
public class DPath {

	protected List<DNode> nodes;
	protected List<DEdge> edges;
	
	
	public DPath() {
		nodes = new ArrayList<>();
		edges = new ArrayList<>();
	}
	
	public List<DEdge> getEdges(){
		return this.edges;
	}
	
	public List<DNode> getNodes(){
		return this.nodes;
	}
	
	public String toString() {
		if (edges.isEmpty()) return "EMPTY";
		
		StringBuilder sb = new StringBuilder();
		sb.append("LINESTRING(");
		sb.append(edges.get(0).getNodeA().getCoordinate().x);
		sb.append(" ");
		sb.append(edges.get(0).getNodeA().getCoordinate().y);
		
		for (DEdge e : edges) {
			sb.append(",");
			sb.append(e.getNodeB().getCoordinate().x);
			sb.append(" ");
			sb.append(e.getNodeB().getCoordinate().y);
		}
		sb.append(")");
		return sb.toString();
	}
}
