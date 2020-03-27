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

package net.refractions.chyf.watershed;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.measure.Unit;

import org.geotools.referencing.util.CRSUtilities;
import org.locationtech.jts.geom.PrecisionModel;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import si.uom.NonSI;
import systems.uom.common.USCustomary;
import tec.uom.se.unit.Units;

/**
 * Contains constants and tolerance values for the Watershed Boundary Generator.
 */
public class WatershedSettings {
	
	private static final String M_PROP_FILE = "meters.properties";
	private static final String DEG_PROP_FILE = "degrees.properties";
	
	/**
	 * The output precision model.
	 */
	private static PrecisionModel pm;
	public static double PRECISION = 1000000000;
	
	/**
	 * The fixed elevation value assigned to constraint vertices. Should be lower
	 * than any DEM vertex, in order to insure water flows to constraints
	 */
	public static double CONSTRAINT_HEIGHT = 0.0;

	public static double BUBBLE_BIAS_FACTOR = 0.01;

	/**
	 * The amount to inset the DEM hull by for trimming the constraint edges. This
	 * keeps the constraint segments safely away from the DEM hull boundary.
	 */
	public static double DEM_HULL_INSET = 0.005; // 2.0;

	/**
	 * The snap tolerance for enforcing constraints in the TIN surface. If this
	 * tolerance is too large, the TIN may be constructed incorrectly (since
	 * endpoints of short segments will be snapped to be coincident, thus collapsing
	 * triangles which should appear) If the value is too small, there may be
	 * robustness problems which appear. (In earlier versions of the code, too-small
	 * tolerances resulted in repeated splitting during constraint enforcement, but
	 * this problem may have been solved by using domain-specific logic to find
	 * optimal split points).
	 */
	public static double SNAP_TOLERANCE = 0.0000001; // 0.005;

	/**
	 * The minimum distance a DEM point is allowed to be to a constraint segment.
	 * DEM points within this distance are discarded. Enforcing this should prevent
	 * Delaunay site snapping from happening during constraint enforcement (which
	 * otherwise can produce invalid triangulations).
	 */
	public static double MIN_DEM_CONSTRAINT_DISTANCE = 0.0002; // 1.0;

	/**
	 * Tolerances controlling how boundary spikes are cleaned up
	 */
	public static double MAX_SPIKE_BASE_WIDTH = 0.00002; // 20.0;
	public static double MAX_SPIKE_ANGLE = 15.0;

    /**
     * Tolerances controlling how boundary edges are smoothed
     */
    //public static final double SIMPLIFY_TOL = 0.001;
    public static double MAX_SMOOTH_ANGLE = 170;
    public static double MIN_LEN_TO_SMOOTH = 0.00001; //10.0;
    public static double MAX_SEG_LEN = 0.001; //100.0;

	
    /**
     * The size of a block (0.1 degrees square or 10,000m square by default)
     */ 
    public static double BLOCK_SIZE = 0.1; //10000\
    
	/**
     * The amount to buffer a block by for processing, determined by multiplying 
     * the size of the block by this factor. For example, 0.5 means to buffer 
     * by half the size of the block, all around, making the buffered block 
     * twice as big in each dimension and covering 4 times the area. 
     */
    public static double BLOCK_BUFFER_FACTOR = 0.5;
    

	public static void load(CoordinateReferenceSystem crs) {
		Unit<?> units = CRSUtilities.getUnit(crs.getCoordinateSystem());
		String propFile;
		if (units.equals(Units.METRE) || units.equals(USCustomary.FOOT)) {
			propFile = M_PROP_FILE;
		} else if (units.equals(NonSI.DEGREE_ANGLE)) {
			propFile = DEG_PROP_FILE;
		} else {
		    throw new RuntimeException("Default Watershedbuilder settings are not provided for the projection units of " + units.toString() + ".  Use the -settings parameter to supply them");
		}
		Properties p = new Properties();
		try(InputStream is = ClassLoader.getSystemResourceAsStream(propFile)) {
			p.load(is);
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
		PRECISION = getValue(p, "PRECISION");
		pm = new PrecisionModel(PRECISION);
		CONSTRAINT_HEIGHT = getValue(p, "CONSTRAINT_HEIGHT");
		BUBBLE_BIAS_FACTOR = getValue(p, "BUBBLE_BIAS_FACTOR");
		DEM_HULL_INSET = getValue(p, "DEM_HULL_INSET");
		SNAP_TOLERANCE = getValue(p, "SNAP_TOLERANCE");
		MIN_DEM_CONSTRAINT_DISTANCE = getValue(p, "MIN_DEM_CONSTRAINT_DISTANCE");
		MAX_SPIKE_BASE_WIDTH = getValue(p, "MAX_SPIKE_BASE_WIDTH");
		MAX_SPIKE_ANGLE = getValue(p, "MAX_SPIKE_ANGLE");
		MAX_SMOOTH_ANGLE = getValue(p, "MAX_SMOOTH_ANGLE");
		MIN_LEN_TO_SMOOTH = getValue(p, "MIN_LEN_TO_SMOOTH");
		MAX_SEG_LEN = getValue(p, "MAX_SEG_LEN");
		BLOCK_SIZE = getValue(p, "BLOCK_SIZE");
		BLOCK_BUFFER_FACTOR = getValue(p, "BLOCK_BUFFER_FACTOR");
	}
	
	private static double getValue(Properties p, String propName) {
		String propVal = p.getProperty(propName);
		if(propVal == null) {
			throw new RuntimeException("No value provided for setting " + propName);
		} 
		try {
			return Double.parseDouble(propVal);
		} catch(NumberFormatException nfe) {
			throw new RuntimeException("Invalid value provided for setting " + propName + ", value: '" + propVal + "' is not a valid double value");
		}
	}

	public static PrecisionModel getPrecisionModel() {
		return pm;
	}

}