/*******************************************************************************
 * Copyright 2020 Government of Canada
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
 *******************************************************************************/
package net.refractions.chyf.util;

import java.util.Comparator;
import java.util.function.Function;

import org.locationtech.jts.geom.Coordinate;

public class CoordinateAxisComparator<T> implements Comparator<T> {
	
	Axis axis;
	Function<T,Coordinate> toCoord;
	
	public CoordinateAxisComparator(Axis axis, Function<T,Coordinate> toCoord) {
		this.axis = axis;
		this.toCoord = toCoord;
	}
	
	@Override
	public int compare(T a, T b) {
		return compareAxis(toCoord.apply(a), toCoord.apply(b), axis);
	}
	
	public static int compareAxis(Coordinate a, Coordinate b, Axis axis) {
		double diff = diffAxis(a, b, axis);
		if(diff < 0) {
			return -1;
		} else if(diff > 0) {
			return 1;
		} else {
			return 0;
		}
	}
	
	public static double diffAxis(Coordinate a, Coordinate b, Axis axis) {
		if(axis == Axis.X) {
			return a.getX() - b.getX();
		} else {
			return a.getY() - b.getY();
		}
	}
	
}
