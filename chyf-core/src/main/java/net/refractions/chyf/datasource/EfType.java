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
package net.refractions.chyf.datasource;

/**
 * Elementary flowpath types
 * 
 * @author Emily
 *
 */
public enum EfType{
	REACH(1),
	BANK (2),
	SKELETON (3),
	INFRASTRUCTURE(4);
	
	private int type;
	
	EfType(int type) {
		this.type = type;
	}
	
	public int getChyfValue() {
		return this.type;
	}
	
	public static EfType parseValue(int value) {
		for (EfType t : EfType.values()) {
			if (t.getChyfValue() == value) return t;
		}
		throw new RuntimeException("The value " + value + " is not supported for the EfType attribute.");
	}
}