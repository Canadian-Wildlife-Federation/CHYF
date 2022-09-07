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
	
	private UUID nameId1;
	private String nameEn1;
	private String nameFr1;
	
	private UUID nameId2;
	private String nameEn2;
	private String nameFr2;
	
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

	public UUID getNameId1() {
		return nameId1;
	}

	public void setNameId1(UUID nameId) {
		this.nameId1 = nameId;
	}

	public void setName1(String nameen, String namefr) {
		this.nameEn1 = nameen;
		this.nameFr1 = namefr;
	}

	public String getNameEn1() {
		return this.nameEn1;
	}

	public String getNameFr1() {
		return this.nameFr1;
	}
	
	public UUID getNameId2() {
		return nameId2;
	}

	public void setNameId2(UUID nameId) {
		this.nameId2 = nameId;
	}

	public void setName2(String nameen, String namefr) {
		this.nameEn2 = nameen;
		this.nameFr2 = namefr;
	}

	public String getNameEn2() {
		return this.nameEn2;
	}

	public String getNameFr2() {
		return this.nameFr2;
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
