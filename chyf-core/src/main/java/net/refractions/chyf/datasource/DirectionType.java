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
 * Values for direction attribute on eflowpaths
 * 
 * @author Emily
 *
 */
public enum DirectionType {

	UNKNOWN(-1),
	KNOWN(1);
	
	private int type;
	
	DirectionType(int type) {
		this.type = type;
	}
	
	public int getChyfValue() {
		return this.type;
	}
	
	public static DirectionType parseValue(Integer type) throws Exception {
		if (type == null) return DirectionType.UNKNOWN;
		for (DirectionType t : DirectionType.values()) {
			if (t.type == type) return t;
		}
		throw new Exception(type + " is an invalid value for direction attribute");
	}
}