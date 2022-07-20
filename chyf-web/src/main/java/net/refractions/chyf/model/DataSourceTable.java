/*
 * Copyright 2021 Canadian Wildlife Federation.
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
package net.refractions.chyf.model;

/**
 * Data source tables 
 * @author Emily
 *
 */
public enum DataSourceTable {

	VECTOR_TILE_CACHE("chyf2.vector_tile_cache"),
	WORK_UNIT("chyf2.nhn_workunit"),
	AOI("chyf2.aoi"),
	EFLOWPATH_PROPERTIES("chyf2.eflowpath_properties"),
	ECATCHMENT_ATTRIBUTES("chyf2.ecatchment_attributes"),
	NAMES("chyf2.names"),
	//use view for data tables
	//view filters out only the aoi's flagged 
	//for display in the aoi table
	SHORELINE("chyf2.shoreline_vw"),
	NEXUS("chyf2.nexus_vw"),
	EFLOWPATH("chyf2.eflowpath_vw"),
	ECATCHMENT("chyf2.ecatchment_vw"),
	
	EC_TYPE("chyf2.ec_type_codes"),
	EC_SUBTYPE("chyf2.ec_subtype_codes"),
	EF_TYPE("chyf2.ef_type_codes"),
	EF_SUBTYPE("chyf2.ef_subtype_codes"),
	NEXUS_TYPE("chyf2.nexut_type_codes");
	
	public static final int DATA_SRID = 4617;

	public String tableName;

	DataSourceTable(String tname) {
		this.tableName = tname;
	}
}
