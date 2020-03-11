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

import org.locationtech.jts.geom.Coordinate;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.DirectionType;
import net.refractions.chyf.datasource.EfType;

/**
 * Represents an edge in our direction graph
 * 
 */
public class DEdge {

	private static final Logger logger = LoggerFactory.getLogger(DEdge.class.getCanonicalName());
	
	private DNode n1;
	private Coordinate n1NextTo;
	
	private DNode n2;
	private Coordinate n2NextTo;
	
	private EdgeInfo info;
	private boolean isbridge = false;
	
	//visited flagged
	protected boolean visited = false;
	
	//identifies if this edge is part of 
	//a path or not
	protected boolean pathedge = false;
	
	
	private DirectionType rawdt;
	
	private List<DEdge> components = new ArrayList<>();
	
	public DEdge(DNode n1, DNode n2, Coordinate n1Next, Coordinate n2Next, EdgeInfo info) {
		this.n1 = n1;
		this.n2 = n2;
		this.info = info;
		rawdt = info.getDirectionType();
		this.n1NextTo = n1Next;
		this.n2NextTo = n2Next;
	}
	
	public void setCoordinates(Coordinate n1Next, Coordinate n2Next) {
		this.n1NextTo = n1Next;
		this.n2NextTo = n2Next;
	}
	
	public DirectionType getRawType() {
		return this.rawdt;
	}
	public boolean isVisited() {
		return this.visited;
	}
	
	public void setVisited(boolean isvisited) {
		this.visited = isvisited;
	}
	
	/**
	 * Reset the direction type and direction of this
	 * edge back to the original state.
	 * 
	 * @throws Exception
	 */
	public void resetKnown() throws Exception{
		if (rawdt == info.getDirectionType()) return;
		

		if (rawdt == DirectionType.KNOWN ) {
			//currently unknown; resetting to known; should never happen
			throw new Exception("Should never reset an edge from unknown dir to known direction");
		}
		//known resetting to unknown; if flipped lets flip back
		if (isFlipped()) {
			flip();
		}
		info.setDirectionType(DirectionType.UNKNOWN);
	}
	/**
	 * 
	 * @return set of edges that were the same as this edge and removed
	 * from the graph
	 */
	public List<DEdge> getSameEdges(){
		return this.components;
	}
	
	/**
	 * Other edges with exact same end nodes
	 */
	public void addSameEdge(DEdge other) {
		if (components == null) components = new ArrayList<>();
		if (components.contains(other)) return;
		components.add(other);
	}
	
	
	/**
	 * This field is only valid after the BridgeFinder
	 * is called.  Will always return false before then.
	 * 
	 * @return true if edge is bridge node
	 */
	public boolean isBridge() {
		return this.isbridge;
	}
	
	/**
	 * Sets the edge bridge flag to true
	 *
	 */
	public void setBridge() {
		this.isbridge = true;
	}
	public double getLength() {
		return info.getLength();
	}
	
	public double getRawLength() {
		return info.getRawLength();
	}
	
	
	public EfType getType() {
		return info.getType();
	}
	
	public FeatureId getID() {
		return info.getFeatureId();
	}
	public boolean isFlipped() {
		return info.isFlipped();
	}
	public DNode getNodeA() {
		return this.n1;
	}
	public DNode getNodeB() {
		return this.n2;
	}
	
	public Coordinate getNextToA() {
		return this.n1NextTo;
	}
	public Coordinate getNextToB() {
		return this.n2NextTo;
	}
	
	public void setNodeA(DNode node) {
		this.n1 = node;
	}
	public void setNodeB(DNode node) {
		this.n2 = node;
	}
	public DNode getOtherNode(DNode node) {
		if (n1 == node) return n2;
		if (n2 == node) return n1;
		return null;
	}
	public DirectionType getDType() {
		return info.getDirectionType();
	}
	
	public void setKnown() {
		info.setDirectionType(DirectionType.KNOWN);
	}
	public void flip() throws Exception{
		if (rawdt == DirectionType.KNOWN) {
			logger.error("Flipping the direction of a known edge: " + toString());
		}
		Coordinate t1 = n1NextTo;
		n1NextTo = n2NextTo;
		n2NextTo = t1;
		
		DNode temp = n1;
		n1 = n2;
		n2 = temp;
		
		info.setDirectionType(DirectionType.KNOWN);
		info.setFlipped();
	}
	
	public void print() {
		System.out.println(toString());
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("LINESTRING( ");
		Coordinate c = n1.getCoordinate();
		sb.append(c.x + " " + c.y);
		c = n1NextTo;
		sb.append("," + c.x + " " + c.y);
		c = n2NextTo;
		sb.append("," + c.x + " " + c.y);
		c = n2.getCoordinate();
		sb.append("," + c.x + " " + c.y);
		sb.append(")");
		return sb.toString();
	}
	
}