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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.filter.identity.FeatureId;

import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.RankType;

/**
 * Represents an edge in our rank graph
 * 
 */
public class REdge {

	private RNode fromNode;
	private RNode toNode;
	
	private Coordinate fromC;
	private Coordinate toC;
	private EfType edgeType;
	private double length;
	private FeatureId fid;
	
	private RankType rank;
	
	private LineString ls;
	
	public REdge(RNode from, RNode to, EfType type, double length, FeatureId fid, LineString ls) {
		this.fromNode = from;
		this.toNode = to;
		this.edgeType = type;
		this.length = length;
		this.fid = fid;
		fromC = ls.getCoordinateN(1);
		toC = ls.getCoordinateN(ls.getCoordinates().length - 2);
	
		this.ls = ls;
		rank = RankType.PRIMARY;
	}
	public LineString getLineString() {
		return this.ls;
	}
	public void setRank(RankType rank) {
		this.rank = rank;
	}
	public RankType getRank() {
		return this.rank;
	}
	public Coordinate getFromC() {
		return this.fromC;
	}
	public Coordinate getToC() {
		return this.toC;
	}
	
	public double getLength() {
		return length;
	}
	
	public EfType getType() {
		return edgeType;
	}
	
	public FeatureId getID() {
		return fid;
	}
	
	public RNode getFromNode() {
		return this.fromNode;
	}
	public RNode getToNode() {
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