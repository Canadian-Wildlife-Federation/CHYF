/*
 * Copyright 2022 Canadian Wildlife Federation
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

import java.util.UUID;


/**
 * Parent class to represent a hydro object
 * 
 * @author Emily
 *
 */
public class HydroFeature {
	
	public static final String TYPE_FIELD_NAME = "type";
	
	/**
	 * hydro feature types
	 * 
	 */
	public enum Type{
		FLOWPATH ("flowpath"),
		WATERBODY("waterbody"),
		CATCHMENT("catchment"),
		SHORELINE("shoreline"),
		NEXUS("nexus");
		
		public String typeName;
		
		Type(String typename){
			this.typeName = typename;
		}
	}
	
	private UUID id;
	private Integer type;
	private String typeName;
	
	private Integer subType;
	private String subTypeName;
	
	private UUID nameId;
	private String nameEn;
	private String nameFr;
	
	private UUID aoi;
	private String aoiName;
	
	
	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public Integer getType() {
		return type;
	}

	public void setType(Integer type) {
		this.type = type;
	}
	
	public String getTypeName() {
		return typeName;
	}

	public void setTypeName(String typeName) {
		this.typeName = typeName;
	}

	public Integer getSubType() {
		return subType;
	}

	public void setSubType(Integer subType) {
		this.subType = subType;
	}
	
	public String getSubTypeName() {
		return subTypeName;
	}

	public void setSubTypeName(String subTypeName) {
		this.subTypeName = subTypeName;
	}

	public UUID getNameId() {
		return nameId;
	}

	public void setNameId(UUID nameId) {
		this.nameId = nameId;
	}

	public void setName(String nameen, String namefr) {
		this.nameEn = nameen;
		this.nameFr = namefr;
	}

	public String getNameEn() {
		return this.nameEn;
	}

	public String getNameFr() {
		return this.nameFr;
	}

	public UUID getAoi() {
		return aoi;
	}

	public void setAoi(UUID aoi) {
		this.aoi = aoi;
	}

	public String getAoiName() {
		return aoiName;
	}

	public void setAoiName(String aoiname) {
		this.aoiName = aoiname;
	}
}
