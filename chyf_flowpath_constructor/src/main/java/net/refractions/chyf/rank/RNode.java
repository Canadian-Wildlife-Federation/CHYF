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
package net.refractions.chyf.rank;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;

/**
 * Represents a node in the rank graph
 * 
 * @author Emily
 *
 */
public class RNode {
	
	private Coordinate c;
	
	private List<REdge> outEdges = new ArrayList<>();
	private List<REdge> inEdges = new ArrayList<>();
		
	public RNode(Coordinate c) {
		this.c = c;
	}
	
	public Coordinate getCoordinate() {
		return this.c;
	}
	
	public int getInDegree() {
		return inEdges.size();
	}

	public int getOutDegree() {
		return outEdges.size();
	}
	
	public void addInEdge(REdge e) {
		this.inEdges.add(e);
	}
	public void addOutEdge(REdge e) {
		this.outEdges.add(e);
	}
	
	public List<REdge> getInEdges(){
		return this.inEdges;
	}
	public List<REdge> getOutEdges(){
		return this.outEdges;
	}
	
	public String toString() {
		return "POINT( " + c.x + " " + c.y + ")";
	}
	public void print() {
		System.out.println(toString());
	}
	
	
}