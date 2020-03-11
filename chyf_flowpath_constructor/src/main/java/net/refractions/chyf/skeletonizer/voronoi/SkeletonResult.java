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
package net.refractions.chyf.skeletonizer.voronoi;

import java.util.Collection;

import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

/**
 * Class representing the results of the skeleton process
 * including a set of error messages from the qa process
 * 
 * @author Emily
 *
 */
public class SkeletonResult {

	private Collection<LineString> skeletons;
	private Collection<String> errors;
	private Polygon updatedPolgyon = null;
	
	/**
	 * Creates a new result
	 * @param skeletons
	 * @param errors
	 */
	public SkeletonResult(Collection<LineString> skeletons, Collection<String> errors) {
		this(skeletons, errors, null);
	}
	
	/**
	 * Creates a new result with an updated waterbody polygon
	 * @param skeletons
	 * @param errors
	 * @param updated
	 */
	public SkeletonResult(Collection<LineString> skeletons, Collection<String> errors, Polygon updated) {
		this.skeletons = skeletons;
		this.errors = errors;
		this.updatedPolgyon = updated;
	}
	
	/**
	 * Get resulting skeletons
	 * @return
	 */
	public Collection<LineString> getSkeletons(){
		return this.skeletons;
	}
	
	/**
	 * Get errors
	 * @return
	 */
	public Collection<String> getErrors(){
		return this.errors;
	}
	
	/**
	 * 
	 * @return the updated polygon; may be null if not set
	 */
	public Polygon getPolygon() {
		return this.updatedPolgyon;
	}
}
