/*
 * Copyright 2021 Canadian Wildlife Federation
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
package net.refractions.chyf;

import org.locationtech.jts.geom.Geometry;

/**
 * Extension of an exception that includes location details. 
 * 
 * @author Emily
 *
 */
public class ExceptionWithLocation extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private Geometry location;
	
	public ExceptionWithLocation(String message, Geometry location) {
		super(message + " " + location.toText());
		this.location = location;
	}
	
	public ExceptionWithLocation(String message, Exception parent, Geometry location) {
		super(message + " " + location.getCentroid().toText(), parent);
		this.location = location;
	}
	
	public Geometry getLocation() {
		return this.location;
	}
}
