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
package net.refractions.chyf.watershed.model;

public enum WaterSide {
	LEFT("L"), 
	RIGHT("R"), 
	NEITHER("N"), 
	BOTH("B");

	private String label;
	
	private WaterSide(String label) {
		this.label = label;
	}
	
	/**
	 * Converts from a string representation of the WaterSide value to the WaterSide object.
	 * 
	 * @param waterSide the string representation of the WaterSide
	 * @return the WaterSide object corresponding to the given string representation
	 */
	public static WaterSide convert(String waterSide) {
		for(WaterSide side : values()) {
			if(side.label.equalsIgnoreCase(waterSide)) {
				return side;
			}
		}
		if("".equals(waterSide) || null == waterSide) {
			return null;
		}
		throw new IllegalArgumentException("Invalid WaterSide value: '" + waterSide + "'.");
	}
	
	/**
	 * @return the string representation of this WaterSide object
	 */
	@Override
	public String toString() {
		return label;
	}}
