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

import java.util.HashMap;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.filter.identity.FeatureId;

import net.refractions.chyf.datasource.RankType;

/**
 * Represents an edge in our name graph
 * 
 */
public class NEdge {

	private NNode fromNode;
	private NNode toNode;
	
	private Coordinate fromC;
	private Coordinate toC;
	
	private double length;
	private FeatureId fid;
	
	private RankType rank;
	
	private HashMap<Integer, String> nameids = new HashMap<>();
	
	public NEdge(NNode from, NNode to, double length, FeatureId fid, RankType rank, LineString ls) {
		this.fromNode = from;
		this.toNode = to;
		this.length = length;
		this.fid = fid;
		this.rank = rank;
		
		fromC = ls.getCoordinateN(0);
		toC = ls.getCoordinateN(ls.getCoordinates().length - 1);
	}
	
	public void setRank(RankType rank) {
		this.rank = rank;
	}
	public RankType getRank() {
		return this.rank;
	}
	public Coordinate getFromCoordinate() {
		return this.fromC;
	}
	public Coordinate getToCoordinate() {
		return this.toC;
	}
	
	public double getLength() {
		return length;
	}
	
	public boolean hasNames() {
		return this.nameids.size() > 0;
	}
	
	public void addName(int index, String nameid) {
		if (nameids.get(index) != null) {
			System.out.println("ERROR");
		}
		this.nameids.put(index, nameid);
	}
	
	public String getNames(int index){
		return this.nameids.get(index);
	}
	
	public FeatureId getID() {
		return fid;
	}
	
	public NNode getFromNode() {
		return this.fromNode;
	}
	public NNode getToNode() {
		return this.toNode;
	}
	
	
	public void print() {
		System.out.println(toString());
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("LINESTRING( ");
		Coordinate c = fromNode.getCoordinate();
		sb.append(c.x + " " + c.y);
		c = fromC;
		sb.append("," + c.x + " " + c.y);
		c = toC;
		sb.append("," + c.x + " " + c.y);
		c = toNode.getCoordinate();
		sb.append("," + c.x + " " + c.y);
		sb.append(")");
		return sb.toString();
	}
	
}