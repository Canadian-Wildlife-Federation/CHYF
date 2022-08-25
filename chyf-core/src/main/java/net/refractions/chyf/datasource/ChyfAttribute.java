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
	NAME("name_string"),
	
	RIVERNAMEID1("rivernameid1"),
	RIVERNAMEID2("rivernameid2"),
	RIVERNAME1("rivername1"),
	RIVERNAME2("rivername2"),

	LAKENAMEID1("lakenameid1"),
	LAKENAMEID2("lakenameid2"),
	LAKENAME1("lakename1"),
	LAKENAME2("lakename2");
	
	private String fieldName;
	
	ChyfAttribute(String name){
		this.fieldName = name;
	}
	
	public String getFieldName() {
		return this.fieldName;
	}
}