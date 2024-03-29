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
package net.refractions.chyf.flowpathconstructor.datasource;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.data.Transaction;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.ConstructionPoint;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkelLineString;

/**
 * Data source input for flowpath constructore tools
 * 
 * @author Emily
 *
 */
public interface IFlowpathDataSource extends ChyfDataSource{

	public static final String CATCHMENT_INTERNALID_ATTRIBUTE = "ecatchment_id";
	public static final String NODETYPE_ATTRIBUTE = "node_type";
	
	public static final String RIVERNAMEIDS_ATTRIBUTE = "rivernameids";
	/**
	 * Node type associated with construction points 
	 *
	 */
	public enum NodeType{
		BANK(1),
		FLOWPATH(2),
		WATER(3),
		HEADWATER(4),
		TERMINAL(5);
		
		private int modelValue;
		
		private NodeType(int modelValue) {
			this.modelValue = modelValue;
		}
		
		public int getChyfValue() {
			return this.modelValue;
		}
		
		public static NodeType parseValue(int value) throws IOException{
			for (NodeType d : NodeType.values()) {
				if (d.modelValue == value) return d;
			}
			throw new IOException("Type value of " + value + " is invalid for NodeType");
		}
	}

	/**
	 * Get the coordinate reference system of the data
	 * @return
	 */
	public CoordinateReferenceSystem getCoordinateReferenceSystem();
	
	/**
	 * Get the aoi polygon for the current working area
	 * @return
	 * @throws IOException
	 */
	public List<Polygon> getAoi() throws IOException ;
	
	/**
	 * Updates the geometry of the give polygon.  The polygon must
	 * have a user data set to PolygonInfo class.
	 * 
	 * @param polygon
	 * @throws IOException
	 */
	public void updateWaterbodyGeometries(Collection<Polygon> polygons) throws IOException;
	
	/**
	 * Updates the geometry of the given coastline feature
	 * 
	 * @param fid
	 * @param newls
	 * @param tx
	 * @throws IOException
	 */
	public void updateCoastline(FeatureId fid, LineString newls, Transaction tx) throws IOException;
	
	/**
	 * Updates the names for flowpaths, currently for naming skeletons
	 * 
	 * @param nameids new names for flowpaths
	 * @throws IOException
	 */
	public void writeFlowpathNames(HashMap<FeatureId, String[]> nameids) throws IOException;
	
	/**
	 * Returns all points from the terminal node layer.  The user data associated
	 * with each point is the value of the FlowDirection attribute associated
	 * with the TerminalNode.
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<TerminalNode> getTerminalNodes() throws Exception;
	
	
	/**
	 * Create a new layer for skeleton construction points
	 * @param points
	 * @throws IOException
	 */
	public void createConstructionsPoints(List<ConstructionPoint> points) throws IOException;
	
	/**
	 * Pass an empty collection to write any remaining features in the write cache
	 * 
	 * @param skeletons
	 * @throws IOException
	 */
	public void writeSkeletons(Collection<SkelLineString> skeletons) throws IOException;
	
	/**
	 * Remove any existing skeleton lines in the dataset.
	 * If bankOnly is true than only existing bank skeletons
	 * are removed; otherwise all skeletons are removed
	 * @throws SQLException 
	 * 
	 */
	public void removeExistingSkeletons(boolean bankOnly) throws IOException;

	/**
	 * Reads construction points from geopackage for the catchment
	 * associated with the provided catchment id.
	 * 
	 * @param catchmentId
	 * @return
	 * @throws IOException
	 */
	public List<ConstructionPoint> getConstructionsPoints(Object catchmentId) throws IOException;

	/**
	 * Adds the rank attribute to the flowpath schema if required
	 * @throws Exception
	 */
	public void addRankAttribute() throws Exception;
	
	/**
	 * Updates the geometry of the give polygon.  The polygon must
	 * have a user data set to PolygonInfo class.
	 * 
	 * @param polygon
	 * @throws IOException
	 */
	public void flipFlowEdges(Collection<FeatureId> pathstoflip, Collection<FeatureId> processed) throws Exception;
	
	/**
	 * All points from the boundary layer with the userdata set to the FlowDirection
	 * type
	 * 
	 * @return
	 * @throws Exception
	 */
	public Set<Coordinate> getOutputConstructionPoints() throws Exception;
	
	
	/**
	 * updates the ranks of features in the datasource
	 * 
	 * @param polygon
	 * @throws IOException
	 */
	public void writeRanks(Map<FeatureId, RankType> ranks) throws Exception;
	
	
	public default Map<ChyfAttribute, Name> findRiverNameAttributes(SimpleFeatureType type) {
		
		Name nameid1 = ChyfDataSource.findAttribute(type, ChyfAttribute.RIVERNAMEID1);
		Name nameid2 = ChyfDataSource.findAttribute(type, ChyfAttribute.RIVERNAMEID2);
		
		Map<ChyfAttribute, Name> names = new HashMap<>();
		names.put(ChyfAttribute.RIVERNAMEID1, nameid1);
		names.put(ChyfAttribute.RIVERNAMEID2, nameid2);
		
		return names;
	}
	
	
	public default String[] getRiverNameIds(Map<ChyfAttribute, Name> names, SimpleFeature feature){
		
		String[] rnames = new String[2];
		for (int i = 0; i < rnames.length; i ++) {
			rnames[i] = null;
		}
		
		if (names.get(ChyfAttribute.RIVERNAMEID1) != null ) {
			Object x = feature.getAttribute(names.get(ChyfAttribute.RIVERNAMEID1));
			if (x != null && !(x.toString().trim().isBlank())) rnames[0] = x.toString();		
		}
		
		if (names.get(ChyfAttribute.RIVERNAMEID2) != null ) {
			Object x = feature.getAttribute(names.get(ChyfAttribute.RIVERNAMEID2));
			if (x != null && !(x.toString().trim().isBlank())) rnames[1] = x.toString();
		}
		if (rnames[0] == null && rnames[1] == null) return null;
		return rnames;
	}
	
	public void populateNameIdTable() throws IOException;

}
