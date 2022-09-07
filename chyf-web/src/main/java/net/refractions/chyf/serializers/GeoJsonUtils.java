/*
 * Copyright 2021 Canadian Wildlife Federation
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
package net.refractions.chyf.serializers;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import net.refractions.chyf.controller.FeatureController;
import net.refractions.chyf.model.ECatchment;
import net.refractions.chyf.model.EFlowpath;
import net.refractions.chyf.model.HydroFeature;
import net.refractions.chyf.model.NamedFeature;
import net.refractions.chyf.model.Shoreline;
import net.refractions.chyf.model.dao.ECatchmentDao;
import net.refractions.chyf.model.dao.EFlowpathDao;
import net.refractions.chyf.model.dao.ShorelineDao;

/**
 * Utilties for writing geometry objects to GeoJson. 
 * 
 * @author Emily
 *
 */
public enum GeoJsonUtils {

	INSTANCE;
	
	/*
	 * 
	 * This code does not use a json library - originally used jackson library
	 * but found it very slow for large result sets.
	 */
	
	public void writeFeature(HydroFeature feature, OutputStream stream) throws IOException {
		if (feature instanceof EFlowpath) {
			writeFeature((EFlowpath)feature, stream);
			return;
		}else if(feature instanceof ECatchment) {
			writeFeature((ECatchment)feature, stream);
			return;
		}else if (feature instanceof Shoreline) {
			writeFeature((Shoreline)feature, stream);
			return;	
		}else if (feature instanceof NamedFeature) {
			writeFeature((NamedFeature)feature, stream);
			return;
		}
		throw new RuntimeException("json feature writer not implemented for hydro feature type");
	}
	
	/**
	 * Converts a feature to geo-json streaming results to output stream
	 * 
	 * 
	 * @param feature
	 * @param stream
	 * @throws IOException
	 */
	public void writeFeature(EFlowpath feature, OutputStream stream) throws IOException {

		writeString(stream, "{" + convertKey("type") + convertObject("Feature") + "," );
		// geometry
		if (feature.getGeometry() != null) writeGeometry(feature.getGeometry(), stream);
		// properties
		writeString(stream, ", " + convertKey("properties") + "{");
		
		writeString(stream, convertKey(HydroFeature.TYPE_FIELD_NAME) + convertObject(HydroFeature.Type.FLOWPATH.typeName) + ",");
		for (EFlowpathDao.Field f : EFlowpathDao.Field.values()) {
			if (f == EFlowpathDao.Field.GEOMETRY) continue;
			writeString(stream, convertKey(f.jsonname) + convertObject(f.getValue(feature)) + ",");
		}
			
		//links
		String rooturl = ServletUriComponentsBuilder.fromCurrentContextPath().path("/").build().toUriString();
		StringBuilder sb = new StringBuilder();
			
		UUID uuid = (UUID) EFlowpathDao.Field.ECATCHMENT_ID.getValue(feature);
		sb.append(convertKey("ecatchment"));
		if (uuid != null) {
			sb.append( convertObject(rooturl + FeatureController.PATH + "/" + uuid.toString()) );
		}else {
			sb.append("null");
		}
		writeString(stream, sb.toString());
		
		writeString(stream, "}}");
		
	}
	
	/**
	 * Converts a feature to geo-json streaming results to output stream
	 * 
	 * 
	 * @param feature
	 * @param stream
	 * @throws IOException
	 */
	public void writeFeature(Shoreline feature, OutputStream stream) throws IOException {

		writeString(stream, "{" + convertKey("type") + convertObject("Feature") + "," );
		// geometry
		if (feature.getGeometry() != null) writeGeometry(feature.getGeometry(), stream);
		// properties
		writeString(stream, ", " + convertKey("properties") + "{");
		
		writeString(stream, convertKey(HydroFeature.TYPE_FIELD_NAME) + convertObject(HydroFeature.Type.SHORELINE.typeName) + ",");
		int i = 0;
		for (ShorelineDao.Field f : ShorelineDao.Field.values()) {
			if (f == ShorelineDao.Field.GEOMETRY) continue;
			if ( i != 0) writeString(stream, ",");
			i++;
			writeString(stream, convertKey(f.jsonname) + convertObject(f.getValue(feature)));
		}		
		writeString(stream, "}}");
	}
	
	/**
	 * Converts a feature to geo-json streaming results to output stream
	 * 
	 * 
	 * @param feature
	 * @param stream
	 * @throws IOException
	 */
	public void writeFeature(NamedFeature feature, OutputStream stream) throws IOException {

		writeString(stream, "{" + convertKey("type") + convertObject("Feature") + "," );
		// geometry
		if (feature.getGeometry() != null) writeGeometry(feature.getGeometry(), stream);
		// properties
		writeString(stream, ", " + convertKey("properties") + "{");
		
		if (feature.getHydroFeatureType() != null) {
			writeString(stream, convertKey(HydroFeature.TYPE_FIELD_NAME) + convertObject(feature.getHydroFeatureType().typeName) + ",");
		}
		
		writeString(stream, convertKey("name_id") + convertObject(feature.getNameId1()) + ",");
		writeString(stream, convertKey("name_en") + convertObject(feature.getNameEn1()) + ",");
		writeString(stream, convertKey("name_fr") + convertObject(feature.getNameFr1()) );
		
		writeString(stream, "}}");
	}
	
	
	public void writeFeature(ECatchment feature, OutputStream stream) throws IOException {

		writeString(stream, "{" + convertKey("type") + convertObject("Feature") + "," );
		// geometry
		if (feature.getGeometry() != null) writeGeometry(feature.getGeometry(), stream);
		// properties
		writeString(stream, ", " + convertKey("properties") + "{");
		
		writeString(stream, convertKey(HydroFeature.TYPE_FIELD_NAME) + convertObject(feature.getFeatureType().typeName) + ",");
		int i = 0;
		for (ECatchmentDao.Field f : ECatchmentDao.Field.values()) {
			if (f == ECatchmentDao.Field.GEOMETRY) continue;
			if ( i != 0) writeString(stream, ",");
			i++;
			writeString(stream, convertKey(f.jsonname) + convertObject(f.getValue(feature)));
		}
		writeString(stream, "}}");
	}

	private String convertKey(String key) {
		return "\"" + key + "\":";
	}
	
	private String convertObject(Object value) {
		if (value == null) return "null";
		if (value instanceof String) return "\"" + escape(value.toString()) + "\"";
		if (value instanceof Number) return String.valueOf( ((Number)value) );
		if (value instanceof Boolean) {
			if ((Boolean)value) return "true";
			return "false";
		}
		if (value instanceof LocalDate) {
			return "\"" + DateTimeFormatter.ISO_DATE.format( (LocalDate)value ) + "\"";
		}
		if (value instanceof Date) {
			return "\"" + DateTimeFormatter.ISO_DATE.format( ((Date)value).toLocalDate() ) + "\"";
		}
		return "\"" + escape(value.toString()) + "\""; 		
	}
	
	private void writeString(OutputStream stream, String value) throws IOException {
		stream.write(value.getBytes());
	}
	
	private void writeGeometry(Geometry g, OutputStream stream) throws IOException {

		writeString(stream, convertKey("geometry") + "{");
		writeString(stream, createGeometryObject(g));
		writeString(stream, "}");
	}
	
	private String createGeometryObject(Geometry g) throws IOException{
		
		StringBuilder sb = new StringBuilder();
		sb.append(convertKey("type"));
		sb.append(convertObject(g.getGeometryType()));
		sb.append(",");
		sb.append(convertGeometry(g));
		return sb.toString();
	}
	
	private String convertGeometry(Geometry g) throws IOException {
		switch (g.getGeometryType()) {
		case "Point":
			return convertKey("coordinates") + convertCoordinate(((Point) g).getX(), ((Point) g).getY());
		case "MultiPoint":
			return convertKey("coordinates") + convertCoordinates(((MultiPoint) g).getCoordinates());
		case "LineString":
			return convertKey("coordinates") + convertLineString((LineString)g);
		case "MultiLineString":
			return convertKey("coordinates") + convertMultiLineString((MultiLineString) g);
		case "Polygon":
			return convertKey("coordinates") + convertPolygon(((Polygon) g));
		case "MultiPolygon":
			return convertKey("coordinates") + convertMultiPolygon(((MultiPolygon) g));
		case "GeometryCollection":
			return convertKey("geometries") + convertGeometryCollection(((GeometryCollection) g));
		default:
			return "\"Unknown geometry type\"";
		}
	}
	
	private String convertGeometryCollection(GeometryCollection g) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (int i = 0; i < g.getNumGeometries(); i++) {
			if (i != 0) sb.append(",");
			sb.append("{");
			sb.append(createGeometryObject(g.getGeometryN(i)));
			sb.append("}");
		}
		sb.append("]");
		return sb.toString();
	}
	
	
	private String convertLineString(LineString ls) throws IOException{
		return convertCoordinates(ls.getCoordinateSequence());
	}
	
	private String convertMultiLineString(MultiLineString mls) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		
		for (int i = 0; i < mls.getNumGeometries(); i++) {
			if (i != 0) sb.append(",");
			sb.append(convertLineString((LineString) mls.getGeometryN(i)));
		}
		sb.append("]");
		return sb.toString();
	}
	
	private String convertMultiPolygon(MultiPolygon mp) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		
		for (int i = 0; i < mp.getNumGeometries(); i++) {
			if (i != 0) sb.append(",");
			sb.append(convertPolygon((Polygon) mp.getGeometryN(i)));
		}
		sb.append("]");
		return sb.toString();
	}

	private String convertPolygon(Polygon p) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append(convertCoordinates(p.getExteriorRing().getCoordinateSequence()));
		for (int i = 0; i < p.getNumInteriorRing(); i++) {
			sb.append(",");
			sb.append(convertCoordinates(p.getInteriorRingN(i).getCoordinateSequence()));
			 
		}
		sb.append("]");
		return sb.toString();
	}

	private String convertCoordinates(CoordinateSequence cs) throws IOException {
		
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (int i = 0; i < cs.size(); i++) {
			if (i != 0) sb.append(",");
			sb.append(convertCoordinate(cs.getX(i), cs.getY(i)));
		}
		sb.append("]");
		return sb.toString();
	}

	private String convertCoordinates(Coordinate[] cs) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (int i = 0; i < cs.length; i++) {
			if (i != 0) sb.append(",");
			sb.append(convertCoordinate(cs[i].x, cs[i].y));
		}
		sb.append("]");
		return sb.toString();
	}
	
	private String convertCoordinate(double x, double y) throws IOException {
		return "[" + x + ", " + y + "]";
	}
	
	
	
	
	/* copied from simple-json library*/
	/**
	 * Escape quotes, \, /, \r, \n, \b, \f, \t and other control characters (U+0000 through U+001F).
	 * @param s
	 * @return
	 */
	public static String escape(String s){
		if(s==null)
			return null;
        StringBuffer sb = new StringBuffer();
        escape(s, sb);
        return sb.toString();
    }

    /**
     * @param s - Must not be null.
     * @param sb
     */
    static void escape(String s, StringBuffer sb) {
    	final int len = s.length();
		for(int i=0;i<len;i++){
			char ch=s.charAt(i);
			switch(ch){
			case '"':
				sb.append("\\\"");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			case '\b':
				sb.append("\\b");
				break;
			case '\f':
				sb.append("\\f");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\t':
				sb.append("\\t");
				break;
			case '/':
				sb.append("\\/");
				break;
			default:
                //Reference: http://www.unicode.org/versions/Unicode5.1.0/
				if((ch>='\u0000' && ch<='\u001F') || (ch>='\u007F' && ch<='\u009F') || (ch>='\u2000' && ch<='\u20FF')){
					String ss=Integer.toHexString(ch);
					sb.append("\\u");
					for(int k=0;k<4-ss.length();k++){
						sb.append('0');
					}
					sb.append(ss.toUpperCase());
				}
				else{
					sb.append(ch);
				}
			}
		}//for
	}
}
