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

import org.locationtech.jts.geom.Coordinate;
import org.opengis.filter.identity.FeatureId;

import net.refractions.chyf.datasource.ChyfDataSource.DirectionType;
import net.refractions.chyf.datasource.ChyfDataSource.EfType;

/**
 * Class to track information about the flowpath edge used
 * to build graph for directionalizing.
 * 
 * @author Emily
 *
 */
public class EdgeInfo {

	
	private EfType type = null;
	private FeatureId fid;
	private Double length;
	private Double rawlength;
	private DirectionType dtype;
	private Coordinate c0, c1;
	private Coordinate startNext, endPrev;
	private boolean flipped = false;
	
	public EdgeInfo(Coordinate start, Coordinate startNext, Coordinate endPrev, Coordinate end, EfType type, FeatureId id, double length, DirectionType dtype) {
		this.type = type;
		this.fid = id;
		this.length = length;
		this.dtype = dtype;
		this.c0 = start;
		this.c1 = end;
		this.startNext = startNext;
		this.endPrev = endPrev;
		
		this.rawlength = length;
		if (type == EfType.INFRASTRUCTURE || type == EfType.REACH) {
			this.length = 10*length;  //weight these to encourage staying in double-line rivers
		}
	}
	
	public Coordinate getStart() {
		return this.c0;
	}

	public Coordinate getEnd() {
		return this.c1;
	}
	
	public Coordinate getStartNext() {
		return this.startNext;
	}
	public Coordinate getEndPrev() {
		return this.endPrev;
	}

	public boolean isFlipped() {
		return this.flipped;
	}

	public void setFlipped() {
		if (this.flipped) {
			this.flipped = false;
		} else {
			this.flipped = true;
		}

	}

	public FeatureId getID() {
		return this.fid;
	}

	public DirectionType getDirectionType() {
		return this.dtype;
	}

	public void setDirectionType(DirectionType dtype) {
		this.dtype = dtype;
	}

	public double getLength() {
		return length;
	}

	public double getRawLength() {
		return rawlength;
	}

	
	public FeatureId getFeatureId() {
		return this.fid;
	}

	public EfType getType() {
		return this.type;
	}
}
