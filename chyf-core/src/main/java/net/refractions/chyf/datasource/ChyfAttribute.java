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
 * Represents the values attribute in the chyf layers
 * @author Emily
 *
 */
public enum ChyfAttribute{
	
	FLOWDIRECTION("flow_direction"),
	ECTYPE("ec_type"),
	EFTYPE("ef_type"),
	DIRECTION("direction_known"),
	RANK("rank"),
	INTERNAL_ID("internal_id"),
	NAME("name_string");
	
	private String fieldName;
	
	ChyfAttribute(String name){
		this.fieldName = name;
	}
	
	public String getFieldName() {
		return this.fieldName;
	}
}