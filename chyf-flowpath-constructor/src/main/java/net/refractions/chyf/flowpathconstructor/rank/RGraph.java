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
package net.refractions.chyf.flowpathconstructor.rank;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.Name;
import org.opengis.filter.identity.FeatureId;

import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.EfType;

/**
 * Graph used for computing rank flow edges
 * 
 * @author Emily
 *
 */
public class RGraph {
	
	public List<RNode> nodes = new ArrayList<>();
	public List<REdge> edges = new ArrayList<>();

	
	
	public RGraph() {
	}
	

	/**
	 * For Testing Only
	 * @param sf
	 * @param typeAtt
	 * @throws Exception
	 */
	public void addEdgeTesting(EfType eftype, LineString ls, FeatureId id) throws Exception {
		Coordinate c0 = ls.getCoordinateN(0);
		Coordinate c1 = ls.getCoordinateN(ls.getCoordinates().length-1);
		
		RNode fromNode = null;
		RNode toNode = null;
		
		for (RNode n : nodes) {
			if (n.getCoordinate().equals2D(c0)) fromNode = n;
			if (n.getCoordinate().equals2D(c1)) toNode = n;
			
			if(fromNode != null && toNode != null) break;
		}
		
		if (fromNode == null) fromNode = createNode(c0);
		if (toNode == null) toNode = createNode(c1);
		
		REdge e = new REdge(fromNode, toNode, eftype, ls.getLength(), id, ls);
		edges.add(e);
		
		toNode.getInEdges().add(e);
		fromNode.getOutEdges().add(e);
	}
	
	public void addEdge(SimpleFeature sf, Name typeAtt) throws Exception {

		EfType eftype = EfType.parseValue((Integer)sf.getAttribute(typeAtt));

		LineString ls = ChyfDataSource.getLineString(sf);
		
		Coordinate c0 = ls.getCoordinateN(0);
		Coordinate c1 = ls.getCoordinateN(ls.getCoordinates().length-1);
		
		RNode fromNode = null;
		RNode toNode = null;
		
		for (RNode n : nodes) {
			if (n.getCoordinate().equals2D(c0)) fromNode = n;
			if (n.getCoordinate().equals2D(c1)) toNode = n;
			
			if(fromNode != null && toNode != null) break;
		}
		
		if (fromNode == null) fromNode = createNode(c0);
		if (toNode == null) toNode = createNode(c1);
		
		REdge e = new REdge(fromNode, toNode, eftype, ls.getLength(), sf.getIdentifier(), ls);
		edges.add(e);
		
		toNode.getInEdges().add(e);
		fromNode.getOutEdges().add(e);
		
	}
	
	public List<RNode> getNodes(){
		return this.nodes;
	}
	
	public List<REdge> getEdges(){
		return this.edges;
	}
	
	private RNode createNode(Coordinate cc) {
		RNode n = new RNode(cc);
		nodes.add(n);
		return n;
	}

}
