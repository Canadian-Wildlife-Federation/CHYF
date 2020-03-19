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
package net.refractions.chyf.datasource;

/**
 * Represents the flow direction on point datasets.
 * 
 * @author Emily
 *
 */
public enum FlowDirection{
	INPUT(1),
	OUTPUT(2),
	UNKNOWN(3);
	
	private int type;
	
	FlowDirection(int type){
		this.type = type;
	}
	
	public int getChyfValue() {
		return this.type;
	}
	
	public static FlowDirection parseValue(int value) {
		for (FlowDirection t : FlowDirection.values()) {
			if (t.getChyfValue() == value) return t;
		}
		throw new RuntimeException("The value " + value + " is not supported for the IoType attribute.");
	}
}