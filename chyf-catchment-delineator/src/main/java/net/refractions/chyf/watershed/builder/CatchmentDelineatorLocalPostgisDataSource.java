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
package net.refractions.chyf.watershed.builder;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.function.BiConsumer;

import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.jdbc.JDBCDataStore;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.datasource.ChyfPostGisLocalDataSource;
import net.refractions.chyf.datasource.ILayer;

/**
 * Postgis data source for catchment delineator.
 * 
 * @author Emily
 *
 */
public class CatchmentDelineatorLocalPostgisDataSource extends ChyfPostGisLocalDataSource implements ICatchmentDelineatorDataSource{

	private static final Logger logger = LoggerFactory.getLogger(DataManager.class);

	public CatchmentDelineatorLocalPostgisDataSource(String connectionString, String inschema, 
			String outschema) throws IOException {
		super(connectionString, inschema, outschema);
	}
	
	@Override
	public void setAoi(String aoi) throws IOException{
		setAoi(aoi, true);
	}
	
	public void setAoi(String aoi, boolean clearOutput) throws IOException{
		logger.warn("Processing AOI: " + aoi);
		super.setAoi(aoi);
	}
	
	/**
	 * Clears any existing data from the output tables 
	 * 
	 * @throws IOException
	 */
	@Override
	protected void cleanOutputSchema(Transaction tx) throws IOException{
		
		Connection c = ((JDBCDataStore)outputDataStore).getConnection(tx);

		for (ILayer l : CatchmentLayer.values()) {
			
			SimpleFeatureType schema = null;
			try{
				schema = outputDataStore.getSchema(getTypeName(l));
			}catch (Exception ex) {
				
			}
			if (schema != null) {
				StringBuilder sb = new StringBuilder();
				sb.append("DELETE FROM ");
				sb.append(outputSchema + "." + getTypeName(l));
				sb.append(" WHERE ");
				sb.append(getAoiFieldName(l));
				sb.append(" = ? ");
				
				try(PreparedStatement ps = c.prepareStatement(sb.toString())){
					ps.setObject(1,  aoiUuid);
					ps.executeUpdate();
				}catch (SQLException ex) {
					throw new IOException(ex);
				}
			}
		}
		super.cleanOutputSchema(tx);
		
	}
	
	@Override
	public SimpleFeatureType getHydroEdgeFT() {
		return ((CatchmentDelineatorGeoPackageDataSource)getInternalDataSource()).getHydroEdgeFT();
	}
	
	@Override
	public SimpleFeatureType getBlockFT() {
		return ((CatchmentDelineatorGeoPackageDataSource)getInternalDataSource()).getBlockFT();
	}

	@Override
	public SimpleFeatureType getWatershedBoundaryEdgeFT() {
		return ((CatchmentDelineatorGeoPackageDataSource)getInternalDataSource()).getWatershedBoundaryEdgeFT();
	}
	
	@Override
	public synchronized boolean createLayer(SimpleFeatureType ft, ReferencedEnvelope workingExtent) {		
		return ((CatchmentDelineatorGeoPackageDataSource)getInternalDataSource()).createLayer(ft, workingExtent);
	}

	@Override
	public synchronized FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer, ReferencedEnvelope bounds, Filter filter) throws IOException {
		return getInternalDataSource().query(layer, bounds, filter);
	}

	@Override
	protected String getTypeName(ILayer layer) {
		if (layer == CatchmentLayer.BLOCK_LAYER || 
			layer == CatchmentLayer.HYDRO_EDGE_LAYER ||
			layer == CatchmentLayer.WATERSHED_BOUNDARY_LAYER) { 
			return layer.getLayerName().toLowerCase();
		}
		return super.getTypeName(layer);
	}

	@Override
	public synchronized void deleteFeatures(ILayer layer, Filter filter) {
		((CatchmentDelineatorGeoPackageDataSource)getInternalDataSource()).deleteFeatures(layer, filter);
	}

	
	public synchronized <T> void writeObjects(ILayer layer, Collection<T> data, BiConsumer<T,SimpleFeature> func) {
		((CatchmentDelineatorGeoPackageDataSource)getInternalDataSource()).writeObjects(layer, data, func);
	}
	
	@Override
	public synchronized void updateBlock(DataBlock dataBlock) {
		((CatchmentDelineatorGeoPackageDataSource)getInternalDataSource()).updateBlock(dataBlock);

//		} catch(IOException ioe) {
//			throw new RuntimeException(ioe);
//		}
	}

	@Override
	public void reprecisionAll() throws IOException {
		((CatchmentDelineatorGeoPackageDataSource)getLocalDataSource()).reprecisionAll();
	}

	private CatchmentDelineatorGeoPackageDataSource getInternalDataSource() {
		try {
			return ((CatchmentDelineatorGeoPackageDataSource)getLocalDataSource());
		}catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}

	@Override
	protected ChyfGeoPackageDataSource createLocalDataSourceInternal(Path file) throws Exception {
		return new CatchmentDelineatorGeoPackageDataSource(file);
	}
	
	
	@Override
	protected void initOutputSchemaInternal(Connection c, Transaction tx) throws IOException{
		super.initOutputSchemaInternal(c, tx);
		
		for (ILayer l : CatchmentLayer.values() ) {
			SimpleFeatureType stype = null;
			try{
				stype = inputDataStore.getSchema(getTypeName(l));
			}catch (Exception ex) {}
			if (stype != null) continue;
			
			//workingDataStore.createSchema(ft) doesn't support UUID data types in postgis
			//so instead of adding the aoi to the feature type builder I'm adding it as
			//an alter table statement 
			outputDataStore.createSchema(stype);
			
			StringBuilder sb = new StringBuilder();
			sb.append("ALTER TABLE ");
			sb.append(outputSchema + "." + getTypeName(l));
			sb.append(" ADD COLUMN ");
			sb.append(getAoiFieldName(null));
			sb.append(" uuid NOT NULL ");
			
			try(Statement s = c.createStatement()){
				s.execute(sb.toString());
			}catch(SQLException ex) {
				throw new IOException (ex);
			}
		}
	}
	
	
	@Override
	protected void uploadResultsInternal(Transaction tx)  throws IOException {
		super.uploadResultsInternal(tx);
		
		//catchment layers
		for (ILayer l : CatchmentLayer.values() ) {
			try(SimpleFeatureReader reader = getInternalDataSource().getFeatureReader(l, Filter.INCLUDE, tx);
					FeatureWriter<SimpleFeatureType, SimpleFeature> writer = outputDataStore.getFeatureWriterAppend(getTypeName(l), tx)){
				copyFeatures(reader, writer, getAoiFieldName(null));
			}	
		}
		
		
	}
}
