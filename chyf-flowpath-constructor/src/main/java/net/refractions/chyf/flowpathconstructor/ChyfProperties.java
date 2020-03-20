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
package net.refractions.chyf.flowpathconstructor;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Properties;

import javax.measure.Unit;

import org.geotools.referencing.util.CRSUtilities;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import net.refractions.chyf.IChyfProperties;
import si.uom.NonSI;
import systems.uom.common.USCustomary;
import tec.uom.se.unit.Units;

/**
 * Class for managing properties related to skeletonizer
 * 
 * @author Emily
 *
 */
public class ChyfProperties implements IChyfProperties{

	public static final String M_PROP_FILE = "chyf.meter.properties";
	public static final String DEG_PROP_FILE = "chyf.degree.properties";
	
	public enum Property{
		
		PNT_VERTEX_DISTANCE("vertex_distance"),
		SKEL_DENSIFY_FACTOR("densify_factor"),
		SKEL_SIMPLIFY_FACTOR("simplify_factor"),
		SKEL_MINSIZE("minimum_skeleton_length"),
		SKEL_ACUTE_ANGLE("acute_angle"),
		BANK_NODE_DISTANCE_OFFSET("bank_node_distance_offset"),
		BANK_MIN_VERTEX_DISTANCE("bank_min_vertex_distance"),
		
		DIR_SHORT_SEGMENT("dir_short_segement_length"),
		DIR_ANGLE_DIFF("dir_angle_diff"),
		
		RANK_CHANNEL_WEIGHT("rank_channel_weight");
		
		String key;
		
		Property(String key){
			this.key = key;
		}
		
		public String getKey() {
			return this.key;
		}
	}
	
	private HashMap<Property, Double> properties;
	
	public ChyfProperties() {
		properties = new HashMap<>();
	}
	
	public Double getProperty(Property prop) {
		return properties.get(prop);
	}
	
	/**
	 * used for testing
	 * @param prop
	 * @param value
	 */
	public void setProperty(Property prop, Double value) {
		properties.put(prop, value);
	}
	
	public static ChyfProperties getProperties(Path filename) throws Exception{
		Properties p = new Properties();
		ChyfProperties props = new ChyfProperties();

		try(InputStream is = Files.newInputStream(filename)){
			p.load(is);
		}
		
		for (Property prop : Property.values()) {
			String value = 	p.getProperty(prop.key);
			props.properties.put(prop,  Double.valueOf(value));
		}
		return props;
	}
	
	/**
	 * Reads the properties file
	 * from the classpath, parses the contents
	 * and returns new properties object
	 * 
	 * @return
	 */
	public static ChyfProperties getProperties(CoordinateReferenceSystem crs) throws Exception{
		Unit<?> units = CRSUtilities.getUnit(crs.getCoordinateSystem());
		if (units.equals(Units.METRE) || units.equals(USCustomary.FOOT)) {
			return loadDefaults(M_PROP_FILE);
		}else if (units.equals(NonSI.DEGREE_ANGLE)) {
			return loadDefaults(DEG_PROP_FILE);
		}else {
			throw new Exception("Default skeletonizer properties do not exist for the projection with units of " + units.toString() + ".  Use the -p=properties parameter to supply them");
		}
	}
	
	private static ChyfProperties loadDefaults(String filename) throws Exception{
		Properties p = new Properties();
		ChyfProperties props = new ChyfProperties();

		try(InputStream is = ClassLoader.getSystemResourceAsStream(filename)){
			p.load(is);
		}
		
		for (Property prop : Property.values()) {
			String value = 	p.getProperty(prop.key);
			props.properties.put(prop,  Double.valueOf(value));
		}
		return props;
	}
}
