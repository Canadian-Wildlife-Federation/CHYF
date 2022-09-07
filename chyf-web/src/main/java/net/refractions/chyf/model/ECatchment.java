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

import org.locationtech.jts.geom.Polygon;

/**
 * Class to represent a catchment object.
 * 
 * @author Emily
 *
 */
public class ECatchment extends HydroFeature {

	/**
	 * Valid catchment types
	 *
	 */
	public enum EcType{
		REACH(1),
		BANK(2),
		EMPTY(3),
		WATER(4),
		BUILTUPAREA(5);
		
		public int code;
		
		EcType(int code){
			this.code = code;
		}
		
		HydroFeature.Type toType(){
			if (this == WATER) return HydroFeature.Type.WATERBODY;
			return HydroFeature.Type.CATCHMENT;
		}
		
		public static HydroFeature.Type toType(int code){
			for (EcType ectype : EcType.values()) {
				if (ectype.code == code) return ectype.toType();
			}
			return HydroFeature.Type.CATCHMENT;
		}
	}
	
	private double area;
	private Boolean isReservoir;
	private Polygon geometry;

	private UUID lakenameId1;
	private String lakenameEn1;
	private String lakenameFr1;
	
	private UUID lakenameId2;
	private String lakenameEn2;
	private String lakenameFr2;
	
	public double getArea() {
		return area;
	}

	public void setArea(double area) {
		this.area = area;
	}

	public Boolean getIsReservoir() {
		return isReservoir;
	}

	public void setIsReservoir(Boolean isReservoir) {
		this.isReservoir = isReservoir;
	}
	
	public Polygon getGeometry() {
		return this.geometry;
	}
	public void setGeometry(Polygon geometry) {
		this.geometry = geometry;
	}
	
	public HydroFeature.Type getFeatureType() {
		return EcType.toType(getType());
	}
	
	
	public UUID getLakeNameId1() {
		return lakenameId1;
	}

	public void setLakeNameId1(UUID nameId) {
		this.lakenameId1 = nameId;
	}

	public void setLakeName1(String nameen, String namefr) {
		this.lakenameEn1 = nameen;
		this.lakenameFr1 = namefr;
	}

	public String getLakeNameEn1() {
		return this.lakenameEn1;
	}

	public String getLakeNameFr1() {
		return this.lakenameFr1;
	}
	
	public UUID getLakeNameId2() {
		return lakenameId2;
	}

	public void setLakeNameId2(UUID nameId) {
		this.lakenameId2 = nameId;
	}

	public void setLakeName2(String nameen, String namefr) {
		this.lakenameEn2 = nameen;
		this.lakenameFr2 = namefr;
	}

	public String getLakeNameEn2() {
		return this.lakenameEn2;
	}

	public String getLakeNameFr2() {
		return this.lakenameFr2;
	}
}
