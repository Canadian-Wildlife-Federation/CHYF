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
package net.refractions.chyf.flowpathconstructor.datasource;

import org.geotools.geometry.jts.JTS;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Coordinate;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;

/**
 * Utilities for computing the angle between a set of coordinates
 * in the provided projection.  Angle is computed by reprojecting the coordinates
 * into lat/long then computing the bearing.
 * 
 * @author Emily
 *
 */
public class ChyfAngle {

	private MathTransform transform;
	
	public ChyfAngle(CoordinateReferenceSystem sourceCRS) {
		if (sourceCRS != null) {
			try {
				Hints hints = new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, Boolean.TRUE);
				CRSAuthorityFactory factory = ReferencingFactoryFinder.getCRSAuthorityFactory("EPSG", hints);
				CoordinateReferenceSystem target = factory.createCoordinateReferenceSystem("EPSG:4326");
				transform = CRS.findMathTransform(sourceCRS, target);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
				
	}
	
	
	public double angle(Coordinate c1, Coordinate c2, Coordinate c3) throws Exception{
		c1 = JTS.transform(c1, null, transform);
		c2 = JTS.transform(c2, null, transform);
		c3 = JTS.transform(c3, null, transform);
	
		double b1 = computeBearingInternal(c2,  c1);
		double b2 = computeBearingInternal(c2,  c3);
		double b = Math.abs(b2 - b1);
		if (b > Math.PI ) b = Math.PI*2 - b;

		return b;
	}
	
	public double computeBearing(Coordinate c1, Coordinate c2) throws Exception{
		c1 = JTS.transform(c1, null, transform);
		c2 = JTS.transform(c2, null, transform);
		
		return computeBearingInternal(c1,  c2);
	}
	
	private double computeBearingInternal(Coordinate c1, Coordinate c2) throws Exception{
		c1 = new Coordinate(Math.toRadians(c1.x), Math.toRadians(c1.y));
		c2 = new Coordinate(Math.toRadians(c2.x), Math.toRadians(c2.y));
		
		double y = Math.sin(c2.x-c1.x) * Math.cos(c2.y);
		double x = Math.cos(c1.y)*Math.sin(c2.y) -
		        Math.sin(c1.y)*Math.cos(c2.y)*Math.cos(c2.x-c1.x);
		double brng = Math.atan2(y, x) ;
		brng = (brng + Math.PI * 2) % (Math.PI * 2);
		return brng;
	}
	
	
	public static void main(String[] args) throws Exception{
		Coordinate c1 = new Coordinate(-1545393.6534379544, 285973.8339422862);
		Coordinate c2 = new Coordinate(-1545422.5078766237, 285979.08511363337);
		Coordinate c3 = new Coordinate(-1545433.0349952378, 286017.35624482913);
		
		ChyfAngle a = new ChyfAngle(CRS.decode("EPSG:3978"));
		System.out.println(Math.toDegrees( a.angle(c1, c2, c3)) );
	}
}
