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

import java.util.List;

/**
 * A collection of hydro features.
 * 
 * @author Emily
 *
 */
//I created this class to make it simple to
//serialize a list of features to a GeoJson
//feature collection
public class HydroFeatureList{

	private List<HydroFeature> items;
	
	public HydroFeatureList(List<HydroFeature> features) {
		this.items = features;
	}
	
	public List<HydroFeature> getItems(){
		return this.items;
	}

}
