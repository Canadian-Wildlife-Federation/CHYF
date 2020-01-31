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

import java.io.IOException;

import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;


/**
 * CHyF data source reader that provides general functions for reading input data
 * from geotools feature readers.
 * 
 * @author Emily
 *
 */
public interface ChyfDataSource extends AutoCloseable {
	
	public enum Attribute{
		IOTYPE("io_type"),
		ECTYPE("ec_type"),
		EFTYPE("ef_type"),
		DIRECTION("direction");
		
		private String fieldName;
		
		Attribute(String name){
			this.fieldName = name;
		}
		
		public String getFieldName() {
			return this.fieldName;
		}
	}
	
	public enum Layer{
		FLOWPATH("EFlowpaths"),
		CATCHMENT("ECatchments"),
		AOI("AOI"),
		COASTLINE("Coastline"),
		BOUNDARY_POINTS("PointOnBdry");
		
		private String layerName;
		
		Layer(String layerName){
			this.layerName = layerName;
		}
		
		public String getLayerName() {
			return this.layerName;
		}		
	}

	public enum IoType{
		INPUT(1),
		OUTPUT(2);
		
		private int type;
		IoType(int type){
			this.type = type;
		}
		
		public int getType() {
			return this.type;
		}
		
		public static IoType parseType(int type) {
			for (IoType t : IoType.values()) {
				if (t.getType() == type) return t;
			}
			throw new RuntimeException("The type " + type + " is not supported for the IoType attribute.");
		}
	}
	
	/**
	 * Elementary  Flow Path Types
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
		
		public int getType() {
			return this.type;
		}
		
		public static EfType parseType(int type) {
			for (EfType t : EfType.values()) {
				if (t.getType() == type) return t;
			}
			throw new RuntimeException("The type " + type + " is not supported for the EfType attribute.");
		}
	}
	
	/**
	 * Elementary Catchment Types
	 * @author Emily
	 *
	 */
	public enum EcType{
		REACH (1),
		BANK(2),
		WATER(4);
		
		private int type;
		
		EcType(int type) {
			this.type = type;
		}
		
		public int getType() {
			return this.type;
		}
		
		public static EcType parseType(int type) {
			for (EcType t : EcType.values()) {
				if (t.getType() == type) return t;
			}
			throw new RuntimeException("The type " + type + " is not supported for the EcType attribute.");
		}
	}
	
	/**
	 * Values for direction attribute on eflowpaths
	 * 
	 * @author Emily
	 *
	 */
	public enum DirectionType {

		UNKNOWN(-1),
		KNOWN(1);
		
		int type;
		
		DirectionType(int type) {
			this.type =type;
		}
		
		public static DirectionType parseType(Integer type) throws Exception {
			if (type == null) return DirectionType.UNKNOWN;
			for (DirectionType t : DirectionType.values()) {
				if (t.type == type) return t;
			}
			throw new Exception(type + " is an invalid value for direction attribute");
		}
	}

	/**
	 * 
	 * @param bounds if null then entire dataset should be returned
	 * @return
	 * @throws IOException
	 */
	SimpleFeatureReader getWaterbodies(ReferencedEnvelope bounds) throws IOException;
	
	/**
	 * 
	 * @param bounds if null then entire dataset should be returned
	 * @return
	 * @throws IOException
	 */
	SimpleFeatureReader getFlowpaths(ReferencedEnvelope bounds) throws IOException;
	
	public static LineString getLineString(SimpleFeature feature) throws Exception{
		Geometry g = (Geometry)feature.getDefaultGeometry();
		if (g instanceof LineString) {
			return (LineString)g;
		}else if (g instanceof MultiLineString && ((MultiLineString)g).getNumGeometries() == 1  ) {
			return (LineString)g.getGeometryN(0);
		}else {
			throw new Exception("Geometry type " + g.getGeometryType() + "  not supported for linestring.");
		}
	}
	
	public static Polygon getPolygon(SimpleFeature feature) throws Exception{
		Geometry g = (Geometry) feature.getDefaultGeometry();
		if (g instanceof Polygon) {
			return (Polygon) g;
		}else if (g instanceof MultiPolygon && ((MultiPolygon)g).getNumGeometries() == 1) {
			return (Polygon) ((MultiPolygon)g).getGeometryN(0);
		}else {
			throw new Exception("Geometry type " + g.getGeometryType() + " not supported for polygon");
		}
	}
	
	public static Point getPoint(SimpleFeature feature) throws Exception{
		Geometry g = (Geometry) feature.getDefaultGeometry();
		if (g instanceof Point) {
			return (Point) g;
		}else if (g instanceof MultiPoint && ((MultiPoint)g).getNumGeometries() == 1) {
			return (Point) ((MultiPoint)g).getGeometryN(0);
		}else {
			throw new Exception("Geometry type " + g.getGeometryType() + " not supported for point");
		}
	}
	
	public static Name findAttribute(SimpleFeatureType ft, Attribute attribute) {
		for (AttributeDescriptor ad : ft.getAttributeDescriptors()) {
			if (ad.getLocalName().equalsIgnoreCase(attribute.getFieldName())) return ad.getName();
		}
		return null;
	}
}
