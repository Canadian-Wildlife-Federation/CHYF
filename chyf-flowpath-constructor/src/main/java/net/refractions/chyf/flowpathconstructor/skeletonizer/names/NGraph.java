/*
 * Copyright 2021 Canadian Wildlife Federation
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
package net.refractions.chyf.flowpathconstructor.skeletonizer.names;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.filter.identity.FeatureId;

import net.refractions.chyf.datasource.RankType;

/**
 * Graph used for computing names inside waterbodies
 * 
 * @author Emily
 *
 */
public class NGraph {
	
	public List<NNode> nodes = new ArrayList<>();
	public List<NEdge> edges = new ArrayList<>();

	
	public NGraph() {
	}
	

	
	public void addEdge(LineString ls, FeatureId fid, RankType rank) throws Exception {

		Coordinate c0 = ls.getCoordinateN(0);
		Coordinate c1 = ls.getCoordinateN(ls.getCoordinates().length-1);
		
		NNode fromNode = null;
		NNode toNode = null;
		
		for (NNode n : nodes) {
			if (n.getCoordinate().equals2D(c0)) fromNode = n;
			if (n.getCoordinate().equals2D(c1)) toNode = n;
			
			if(fromNode != null && toNode != null) break;
		}
		
		if (fromNode == null) fromNode = createNode(c0);
		if (toNode == null) toNode = createNode(c1);
		
		NEdge e = new NEdge(fromNode, toNode,  ls.getLength(), fid, rank, ls);
		edges.add(e);
		
		toNode.getInEdges().add(e);
		fromNode.getOutEdges().add(e);
		
	}
	
	public List<NNode> getNodes(){
		return this.nodes;
	}
	
	public List<NEdge> getEdges(){
		return this.edges;
	}
	
	private NNode createNode(Coordinate cc) {
		NNode n = new NNode(cc);
		nodes.add(n);
		return n;
	}

}
