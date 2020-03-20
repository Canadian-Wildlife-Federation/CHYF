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
 * Represents the rank value associated with an eflowpath
 * 
 * @author Emily
 *
 */
public enum RankType{
	PRIMARY(1),
	SECONDARY(2);
	
	private int type;
	
	RankType(int type){
		this.type = type;
	}
	
	public int getChyfValue() {
		return this.type;
	}
	
	public static RankType parseValue(int type) {
		for (RankType t : RankType.values()) {
			if (t.getChyfValue() == type) return t;
		}
		throw new RuntimeException("The value of " + type + " is not supported for the Rank attribute.");
	}
}