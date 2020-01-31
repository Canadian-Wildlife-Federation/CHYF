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
package net.refractions.chyf.skeletonizer.points;

import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import net.refractions.chyf.skeletonizer.points.ConstructionPoint.Direction;

/**
 * Represents an edge along the boundary of the aoi
 * that is shared with a waterbody feature.
 * 
 * @author Emily
 *
 */
public class BoundaryEdge {

	private Direction direction;
	private LineString ls;
	private Point inout;
	
	public BoundaryEdge(Direction direction, LineString ls, Point inout) {
		this.ls = ls;
		this.direction = direction;
		this.inout = inout;
	}
	
	public Direction getDirection() {
		return this.direction;
	}
	
	public LineString getLineString() {
		return this.ls;
	}
	
	public Point getInOut() {
		return this.inout;
	}
}
