/*
 * Copyright 2022 Canadian Wildlife Federation
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
package net.refractions.chyf.model;

import org.locationtech.jts.geom.Geometry;

/**
 * A named feature - the geometry can be a geomtrycollection
 * with different geometry types (named river may have skeletons lines and
 * waterbody polygons)
 * 
 * @author Emily
 *
 */
public class NamedFeature extends HydroFeature{

	private Geometry geometry;
	private HydroFeature.Type type;
	
	public NamedFeature(HydroFeature.Type type) {
		this.type = type;
	}
	
	public HydroFeature.Type getHydroFeatureType(){
		return this.type;
	}
	
	public Geometry getGeometry() {
		return this.geometry;
	}
	
	public void setGeometry(Geometry geometry) {
		this.geometry = geometry;
	}
	
}
