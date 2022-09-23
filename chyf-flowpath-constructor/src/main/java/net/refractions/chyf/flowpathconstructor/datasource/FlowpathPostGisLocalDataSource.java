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
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.geotools.data.DataStore;
import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.data.store.EmptyFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.jdbc.JDBCDataStore;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.datasource.ChyfPostGisLocalDataSource;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.ConstructionPoint;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkelLineString;

/**
 * Extension of the postgis local data source for flowpath computation. 
 * 
 * @author Emily
 *
 */
public class FlowpathPostGisLocalDataSource extends ChyfPostGisLocalDataSource implements IFlowpathDataSource{

	static final Logger logger = LoggerFactory.getLogger(FlowpathPostGisLocalDataSource.class.getCanonicalName());
	
	public static final String CONSTRUCTION_PNTS_TABLE = "construction_points";
	
	private boolean addRankToOutput = false;

	public FlowpathPostGisLocalDataSource(String connectionString, String inschema, String outschema) throws Exception {
		super(connectionString, inschema, outschema);
	}

	
	/**
	 * Cleans the postgis output schema of all existing data.
	 * 
	 * @param includeAOi if true then the record from the aoi table will also be removed. 
	 * If false we leave the record in the aoi table. 
	 */
	@Override
	protected void cleanOutputSchema(Connection c, Transaction tx, boolean includeAoi) throws IOException{
//		SimpleFeatureType stype = null;
//		try{
//			stype = outputDataStore.getSchema(CONSTRUCTION_PNTS_TABLE);
//		}catch (Exception ex) {	}
//		if (stype == null) return;
//		
		StringBuilder sb = new StringBuilder();
    	sb.append("DELETE FROM ");
		sb.append(outputSchema + "." + CONSTRUCTION_PNTS_TABLE);
		sb.append(" WHERE aoi_id = ?");
		try(PreparedStatement ps = c.prepareStatement(sb.toString())){
			ps.setObject(1, aoiUuid);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new IOException(e);
		}
		
		super.cleanOutputSchema(c, tx, includeAoi);
	}
	
	@Override
	public void setAoi(String aoi) throws IOException{
		logger.warn("Processing AOI: " + aoi);
		super.setAoi(aoi);
	}
	
	@Override
	protected void uploadResultsInternal(DataStore outputDataStore, Transaction tx)  throws IOException {
		
		if (addRankToOutput) {
			//add rank attribute if it doesn't exist
			Name rankatt = ChyfDataSource.findAttribute(outputDataStore.getSchema(getTypeName(Layer.EFLOWPATHS)), ChyfAttribute.RANK);
			if (rankatt == null) {
				StringBuilder sb = new StringBuilder();
			   	sb.append("ALTER TABLE ");
			   	sb.append(outputSchema + "." + getTypeName(Layer.EFLOWPATHS));
			   	sb.append(" ADD COLUMN ");
			   	sb.append(ChyfAttribute.RANK.getFieldName());
			   	sb.append(" smallint ");
			   	
		   		Connection c = ((JDBCDataStore)outputDataStore).getConnection(tx);
		   		try {
					c.createStatement().executeUpdate(sb.toString());
				} catch (SQLException e) {
					throw new IOException(e);
				}
			}
		}
		
		super.uploadResultsInternal(outputDataStore, tx);
		
		//write construction points
		FlowpathGeoPackageDataSource  pkg = (FlowpathGeoPackageDataSource) getLocalDataSource();
		try(SimpleFeatureReader reader = pkg.getFeatureReader(pkg.getConstructionPointLayer(),Filter.INCLUDE,tx);
			FeatureWriter<SimpleFeatureType, SimpleFeature> writer = outputDataStore.getFeatureWriterAppend(CONSTRUCTION_PNTS_TABLE, tx)){
			if (reader != null) copyFeatures(reader, writer, getAoiFieldName(null));
		}
	}
	
	@Override
	protected void initOutputSchemaInternal(DataStore inputDataStore, DataStore outDataStore, Transaction tx) throws IOException{
		super.initOutputSchemaInternal(inputDataStore, outDataStore, tx);
		
		SimpleFeatureType stype = null;
		try{
			stype = outDataStore.getSchema(CONSTRUCTION_PNTS_TABLE);
		}catch (Exception ex) {	}
		
		if (stype == null) {
			StringBuilder sb = new StringBuilder();
			
			sb.append("CREATE TABLE " );
			sb.append(outputSchema + "." + CONSTRUCTION_PNTS_TABLE);
			sb.append("(");
			sb.append(getAoiFieldName(null) + " uuid not null references " + outputSchema + "." + getTypeName(Layer.AOI) + "(" + getAoiFieldName(Layer.AOI) + "),");
			sb.append(ChyfAttribute.INTERNAL_ID.getFieldName() + " uuid Primary Key,");
			sb.append(CATCHMENT_INTERNALID_ATTRIBUTE + " UUID,");
			sb.append(NODETYPE_ATTRIBUTE + " integer,");
			sb.append(ChyfAttribute.FLOWDIRECTION.getFieldName() + " integer,");
			sb.append(RIVERNAMEIDS_ATTRIBUTE + " varchar,");
			sb.append(" the_geom GEOMETRY(POINT, " + srid + ")");
			sb.append(") ");
			
		    try {
				Connection c = ((JDBCDataStore)outDataStore).getConnection(tx);
		    	c.createStatement().executeUpdate(sb.toString());
			} catch (SQLException e) {
				throw new IOException(e);
			}
		}
	}
	
	@Override
	public void updateCoastline(FeatureId fid, LineString newls, Transaction tx) throws IOException {
		((FlowpathGeoPackageDataSource)	getLocalDataSource()).updateCoastline(fid, newls, tx);
	}
	
	/**
	 * Returns all points from the terminal node layer.  The user data associated
	 * with each point is the value of the FlowDirection attribute associated
	 * with the TerminalNode.
	 * 
	 * @return
	 * @throws Exception
	 */
	@Override
	public List<TerminalNode> getTerminalNodes() throws Exception{
		return ((FlowpathGeoPackageDataSource)	getLocalDataSource()).getTerminalNodes();
	}
	
	@Override
	protected void cacheData(DataStore inputDataStore, GeoPackage geopkg) throws IOException {
		super.cacheData(inputDataStore, geopkg);
		
		//if construction points exist in schema, load these
		boolean found = false;
		for (String s : inputDataStore.getTypeNames()) {
			if (s.equals(CONSTRUCTION_PNTS_TABLE)) {
				found = true;
				break;
			}
		}
		
		if (!found) return;
		
		FeatureEntry entry = new FeatureEntry();
		entry.setTableName(FlowpathGeoPackageDataSource.CONSTRUCTION_PNTS_LAYER);
		entry.setM(false);
		
		Filter filter = getAoiFilter(null);
		
		SimpleFeatureCollection input = inputDataStore.getFeatureSource(CONSTRUCTION_PNTS_TABLE).getFeatures(filter);
		
		//convert uuid to string
		SimpleFeatureTypeBuilder ftBuilder = new SimpleFeatureTypeBuilder();
		ftBuilder.setName(FlowpathGeoPackageDataSource.CONSTRUCTION_PNTS_LAYER);
		for (AttributeDescriptor ad : input.getSchema().getAttributeDescriptors()) {
			if (ad.getType().getBinding().equals(UUID.class)) {
				ftBuilder.add(ad.getLocalName(), String.class);
			}else {
				ftBuilder.add(ad);
			}
		}
		SimpleFeatureType newtype = ftBuilder.buildFeatureType();
		
		
		List<SimpleFeature> features = new ArrayList<>();
		input.accepts(new FeatureVisitor() {
			@Override
			public void visit(Feature feature) {
				SimpleFeatureBuilder sb=  new SimpleFeatureBuilder(newtype);
				for (Property p : feature.getProperties()) {
					if (p.getValue() instanceof UUID) {
						sb.set(p.getName(), ((UUID)p.getValue()).toString());
					}else {
						sb.set(p.getName(), p.getValue());
					}
				}
				SimpleFeature sf = sb.buildFeature(feature.getIdentifier().getID());
				features.add(sf);
			}
			
		}, null);
		
		if(!features.isEmpty()) {
			geopkg.add(entry, DataUtilities.collection(features));
		}else {
			geopkg.add(entry,new EmptyFeatureCollection(newtype));
		}
		geopkg.createSpatialIndex(entry);
	}
	
	/**
	 * All points from the boundary layer with the userdata set to the FlowDirection
	 * type
	 * 
	 * @return
	 * @throws Exception
	 */
	@Override
	public Set<Coordinate> getOutputConstructionPoints() throws Exception{
		return ((FlowpathGeoPackageDataSource)	getLocalDataSource()).getOutputConstructionPoints();
	}
	
	/**
	 * Create a new layer for skeleton construction points
	 * @param points
	 * @throws IOException
	 */
	@Override
	public void createConstructionsPoints(List<ConstructionPoint> points) throws IOException {
		((FlowpathGeoPackageDataSource)	getLocalDataSource()).createConstructionsPoints(points);		
	}

	/**
	 * Reads construction points from geopackage for the catchment
	 * associated with the provided catchment id.
	 * 
	 * @param catchmentId
	 * @return
	 * @throws IOException
	 */
	@Override
	public synchronized List<ConstructionPoint> getConstructionsPoints(Object catchmentId) throws IOException{
		return ((FlowpathGeoPackageDataSource)	getLocalDataSource()).getConstructionsPoints(catchmentId);
	}
	

	
	/**
	 * Updates the geometry of the give polygon.  The polygon must
	 * have a user data set to PolygonInfo class.
	 * 
	 * @param polygon
	 * @throws IOException
	 */
	@Override
	public void updateWaterbodyGeometries(Collection<Polygon> polygons) throws IOException{
		((FlowpathGeoPackageDataSource)	getLocalDataSource()).updateWaterbodyGeometries(polygons);
	}
	
	
	/**
	 * updates the ranks of features in the datasource
	 * 
	 * @param polygon
	 * @throws IOException
	 */
	@Override
	public void writeRanks(Map<FeatureId, RankType> ranks) throws Exception{
		((FlowpathGeoPackageDataSource)	getLocalDataSource()).writeRanks(ranks);
	}
	
	/**
	 * Updates the geometry of the give polygon.  The polygon must
	 * have a user data set to PolygonInfo class.
	 * 
	 * @param polygon
	 * @throws IOException
	 */
	@Override
	public void flipFlowEdges(Collection<FeatureId> pathstoflip, Collection<FeatureId> processed) throws Exception{
		((FlowpathGeoPackageDataSource)	getLocalDataSource()).flipFlowEdges(pathstoflip, processed);
	}
	
	/**
	 * Pass an empty collection to write any remaining features in the write cache
	 * 
	 * @param skeletons
	 * @throws IOException
	 */
	@Override
	public synchronized void writeSkeletons(Collection<SkelLineString> skeletons) throws IOException {
		((FlowpathGeoPackageDataSource)	getLocalDataSource()).writeSkeletons(skeletons);
	}

		/**
	 * Remove any existing skeleton lines in the dataset.
	 * If bankOnly is true than only existing bank skeletons
	 * are removed; otherwise all skeletons are removed
	 * @throws SQLException 
	 * 
	 */
	@Override
	public void removeExistingSkeletons(boolean bankOnly) throws IOException {
		((FlowpathGeoPackageDataSource)	getLocalDataSource()).removeExistingSkeletons(bankOnly);
	}
	
	@Override
	public void addRankAttribute() throws Exception{
		((FlowpathGeoPackageDataSource)	getLocalDataSource()).addRankAttribute();
		addRankToOutput = true;
	}

	@Override
	protected ChyfGeoPackageDataSource createLocalDataSourceInternal(Path file) throws Exception {
		return new FlowpathGeoPackageDataSource(file);
	}


	@Override
	public List<Polygon> getAoi() throws IOException {
		return ((FlowpathGeoPackageDataSource)	getLocalDataSource()).getAoi();

	}


	@Override
	public void writeFlowpathNames(HashMap<FeatureId, String[]> nameids) throws IOException {
		((FlowpathGeoPackageDataSource)	getLocalDataSource()).writeFlowpathNames(nameids);
	}

	@Override
	public void populateNameIdTable() throws IOException{
		((FlowpathGeoPackageDataSource)	getLocalDataSource()).populateNameIdTable();
		
	}
}
