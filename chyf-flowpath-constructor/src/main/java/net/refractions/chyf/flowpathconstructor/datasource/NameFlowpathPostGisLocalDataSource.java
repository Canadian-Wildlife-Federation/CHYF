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
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfLogger;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.datasource.ChyfPostGisLocalDataSource;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.ConstructionPoint;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkelLineString;

/**
 * Extension of the postgis local data source to be used when naming flowpaths and 
 * we want to read/write from the same schema. We don't want to copy to a new schema.
 * 
 * This data source leaves everything in the single schema until ready to copy results back, 
 * then removes the data from the schema before writing the new data in a single translation. If
 * anything fails the original data still exists so we don't have to worry about loosing data.
 * 
 * @author Emily
 *
 */
public class NameFlowpathPostGisLocalDataSource extends ChyfPostGisLocalDataSource implements IFlowpathDataSource{

	static final Logger logger = LoggerFactory.getLogger(NameFlowpathPostGisLocalDataSource.class.getCanonicalName());
	
	public static final String CONSTRUCTION_PNTS_TABLE = "construction_points";

	public NameFlowpathPostGisLocalDataSource(String connectionString, String inschema, String outschema) throws Exception {
		super(connectionString, inschema, outschema);
		if (!inschema.equals(outschema)) throw new RuntimeException("The data source for updating postgis names requires the inschema and outschema to be the same.");
	}

	/**
	 * Do nothing here
	 */
	@Override
	protected void cleanOutputSchema(Transaction tx, boolean includeAoi) throws IOException{
		
		
	}
	
	@Override
	public void setAoi(String aoi) throws IOException{
		logger.warn("Processing AOI: " + aoi);
		super.setAoi(aoi);
	}
	
	@Override
	protected void uploadResultsInternal(Transaction tx)  throws IOException {
		
		Connection c = ((JDBCDataStore)outputDataStore).getConnection(tx);

		
		StringBuilder sb = new StringBuilder();
		//clean out the errors table
		sb.append("DELETE FROM ");
		sb.append( outputSchema + "." + getTypeName(Layer.ERRORS) );
		sb.append(" WHERE ");
		sb.append(getAoiFieldName(Layer.ERRORS));
		sb.append(" = ? AND process = ?");
    		
		try(PreparedStatement ps = c.prepareStatement(sb.toString())){
			ps.setObject(1, aoiUuid);
			ps.setObject(2, ChyfLogger.Process.NAMING.name());
			ps.executeUpdate();
		}catch(SQLException ex) {
			throw new IOException(ex);
		}    		   
		
		
		sb = new StringBuilder();
		sb.append("DELETE FROM ");
		sb.append( outputSchema + "." + CONSTRUCTION_PNTS_TABLE );
		sb.append(" WHERE ");
		sb.append(getAoiFieldName(null));
		sb.append(" = ? ");
    	
		
		try(PreparedStatement ps = c.prepareStatement(sb.toString())){
			ps.setObject(1, aoiUuid);
			ps.executeUpdate();
		}catch(SQLException ex) {
			throw new IOException(ex);
		}    		   
		super.cleanOutputSchema(tx, false);
		
		super.uploadResultsInternal(tx);
		
		//write construction points
		FlowpathGeoPackageDataSource  pkg = (FlowpathGeoPackageDataSource) getLocalDataSource();
		try(SimpleFeatureReader reader = pkg.getFeatureReader(pkg.getConstructionPointLayer(),Filter.INCLUDE,tx);
			FeatureWriter<SimpleFeatureType, SimpleFeature> writer = outputDataStore.getFeatureWriterAppend(CONSTRUCTION_PNTS_TABLE, tx)){
			if (reader != null) copyFeatures(reader, writer, getAoiFieldName(null));
		}
	}
	
	@Override
	protected void initOutputSchemaInternal(Connection c, Transaction tx) throws IOException{
		super.initOutputSchemaInternal(c, tx);
		
		
	}
	
	@Override
	public void updateCoastline(FeatureId fid, LineString newls, Transaction tx) throws IOException {
		throw new UnsupportedOperationException();
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
	
	/**
	 * All points from the boundary layer with the userdata set to the FlowDirection
	 * type
	 * 
	 * @return
	 * @throws Exception
	 */
	@Override
	public Set<Coordinate> getOutputConstructionPoints() throws Exception{
		throw new UnsupportedOperationException();
	}
	
	/**
	 * Create a new layer for skeleton construction points
	 * @param points
	 * @throws IOException
	 */
	@Override
	public void createConstructionsPoints(List<ConstructionPoint> points) throws IOException {
		throw new UnsupportedOperationException();		
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
	
	@Override
	public void updateWaterbodyGeometries(Collection<Polygon> polygons) throws IOException{
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void writeRanks(Map<FeatureId, RankType> ranks) throws Exception{
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void flipFlowEdges(Collection<FeatureId> pathstoflip, Collection<FeatureId> processed) throws Exception{
		throw new UnsupportedOperationException();
	}
	
	@Override
	public synchronized void writeSkeletons(Collection<SkelLineString> skeletons) throws IOException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeExistingSkeletons(boolean bankOnly) throws IOException {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public void addRankAttribute() throws Exception{
		throw new UnsupportedOperationException();
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
	protected void cacheData(GeoPackage geopkg) throws IOException {
		super.cacheData(geopkg);
		//cache construction point table
		
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

	@Override
	public void populateNameIdTable() throws IOException {
		throw new UnsupportedOperationException("Not supported when reprocessing names");
	}
}
