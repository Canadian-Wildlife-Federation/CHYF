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
package net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi;

import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;

import net.refractions.chyf.datasource.EfType;

/**
 * Skeleton line string 
 * 
 * @author Emily
 *
 */
public class SkelLineString {

	private SimpleFeature exitingFeature;
	private EfType etype;
	private LineString ls;
	
	public SkelLineString(LineString ls, EfType type) {
		this(ls, type, null);
	}
	
	/**
	 * 
	 * @param ls
	 * @param type
	 * @param feature the existing feature representing the linestring
	 */
	public SkelLineString(LineString ls, EfType type, SimpleFeature feature) {
		this.etype = type;
		this.exitingFeature = feature;
		this.ls = ls;
	}
	
	/**
	 * Get the skeleton geometry
	 * @return
	 */
	public LineString getLineString() {
		return this.ls;
	}
	
	/**
	 * Get the exitsing feature, or null if no existing feature exists
	 * @return
	 */
	public SimpleFeature getFeature() {
		return this.exitingFeature;
	}
	
	/**
	 * Get the type
	 * 
	 * @return
	 */
	public EfType getEfType() {
		return this.etype;
	}
	
	/**
	 * Update the skeleton geometry
	 * @param ls
	 */
	public void setLineString(LineString ls) {
		this.ls = ls;
	}

}
