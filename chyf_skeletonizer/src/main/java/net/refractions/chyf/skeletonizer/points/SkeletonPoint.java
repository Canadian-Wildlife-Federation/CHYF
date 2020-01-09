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

import java.io.IOException;

import org.locationtech.jts.geom.Coordinate;

import net.refractions.chyf.datasource.ChyfGeoPackageDataSource.FlowDirection;

/**
 * Represents a skeleton input/ouput point
 * @author Emily
 *
 */
public class SkeletonPoint {

	public enum Type{
		BANK(1),
		FLOWPATH(2),
		WATER(3),
		HEADWATER(4),
		TERMINAL(5);
		
		private int modelValue;
		
		private Type(int modelValue) {
			this.modelValue = modelValue;
		}
		
		public int modelValue() {
			return this.modelValue;
		}
		public static Type getType(int value) throws IOException{
			for (Type d : Type.values()) {
				if (d.modelValue == value) return d;
			}
			throw new IOException("Type value of " + value + " is invalid");
		}
	}
	
	private Coordinate point;
	private Type type;
	private FlowDirection fd;
	private PolygonInfo pinfo;
	
	public SkeletonPoint(Coordinate coordinate, Type type, FlowDirection fd, PolygonInfo pinfo) {
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
	
	public Type getType() {
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
