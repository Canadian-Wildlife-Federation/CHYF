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
	
}
