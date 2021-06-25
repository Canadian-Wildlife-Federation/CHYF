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

import org.locationtech.jts.geom.Coordinate;

import net.refractions.chyf.datasource.FlowDirection;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource.NodeType;

/**
 * Represents a skeleton input/ouput point
 * @author Emily
 *
 */
public class ConstructionPoint {
		
	private Coordinate point;
	private NodeType type;
	private FlowDirection fd;
	private PolygonInfo pinfo;
	
	public ConstructionPoint(Coordinate coordinate, NodeType type, FlowDirection fd, PolygonInfo pinfo) {
		if (coordinate == null) {
			System.out.println("stop");
		}
		this.point = coordinate;
		this.type = type;
		this.fd = fd;
		this.pinfo = pinfo;
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
//		System.out.println("expected.add(new SkeletonPoint(new Coordinate(" + point.x + "," + point.y + "),Type." + type + ",FlowDirection." + fd + "));");
//		System.out.println("POINT(" + point.x +" " + point.y + ") : " + type + ":" + fd);
	}
}
