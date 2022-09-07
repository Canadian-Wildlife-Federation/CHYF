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
package net.refractions.chyf.flowpathconstructor.skeletonizer.points;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;

import net.refractions.chyf.datasource.FlowDirection;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource.NodeType;

/**
 * Represents a skeleton input/ouput point
 * 
 * @author Emily
 *
 */
public class ConstructionPoint {
		
	private Coordinate point;
	private NodeType type;
	private FlowDirection fd;
	private PolygonInfo pinfo;
	
	//array of list of names so we can treat different name attribute differently (name1 vs name2)
	//a construction point can have multiple names for each name attribute
	//the list is one array for each set of names. The array is the set of names for that point
	//[ [NameA-1, NameA-2]  [NameB-1, NameB-2]]
	private List<String[]> nameids;
	
	public ConstructionPoint(Coordinate coordinate, NodeType type, FlowDirection fd, PolygonInfo pinfo) {
		this(coordinate, type, fd, pinfo, null);
	}
	
	public ConstructionPoint(Coordinate coordinate, NodeType type, FlowDirection fd, PolygonInfo pinfo, String[] ids) {
		this.point = coordinate;
		this.type = type;
		this.fd = fd;
		this.pinfo = pinfo;
		
		if (ids != null) {
			nameids = new ArrayList<>();
			nameids.add(ids);
		}
	}
	
	public PolygonInfo getWaterbodyInfo() {
		return this.pinfo;
	}
	
	public Coordinate getCoordinate() {
		return this.point;
	}
	
	public NodeType getType() {
		return this.type;
	}
	
	public FlowDirection getDirection() {
		return this.fd;
	}
	
	public void toText() {
		System.out.println("POINT(" + point.x +" " + point.y + ")");
	}
	
	/**
	 * < [NameA-1, NameA-2]  [NameB-1, NameB-2] >
	 * @return list of name sets that flow into or out of this construction point, generally only
	 * one set but may be multiple if multiple name streams flow into the waterbody at a specific point
	 */
	public List<String[]> getNameIds() {
		return this.nameids;
	}
	
	public void addNames(String[] ids) {
		if (nameids == null) nameids = new ArrayList<>();
		
		for (String[] s : nameids) {
			//already exists
			if (s[0].equals(ids[0])) return;
		}
		nameids.add(ids);
	}
}
