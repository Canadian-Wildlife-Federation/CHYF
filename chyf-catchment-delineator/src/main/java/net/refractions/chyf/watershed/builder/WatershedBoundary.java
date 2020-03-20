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
package net.refractions.chyf.watershed.builder;

import org.locationtech.jts.geom.Polygon;

public class WatershedBoundary {
	private int drainageId;
	private int catchmentType;
	private Polygon poly;

	public WatershedBoundary(int drainageId, int catchmentType, Polygon poly) {
		this.drainageId = drainageId;
		this.catchmentType = catchmentType;
		this.poly = poly;
	}

	public int getDrainageId() {
		return drainageId;
	}

	public int getCatchmentType() {
		return catchmentType;
	}

	public Polygon getPoly() {
		return poly;
	}

}
