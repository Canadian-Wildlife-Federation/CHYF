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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfPostGisDataSource;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.util.ProcessStatistics;
import net.refractions.chyf.watershed.WatershedSettings;

public class CatchmentDelineatorPostgisDataSource extends ChyfPostGisDataSource implements ICatchmentDelineatorDataSource{

	private static final Logger logger = LoggerFactory.getLogger(DataManager.class);

	public CatchmentDelineatorPostgisDataSource(String connectionString, String inschema, 
			String outschema) throws IOException {
		super(connectionString, inschema, outschema);
//		addInternalIdAttribute();
	}
	
	
	public void clearOutputTables() throws IOException {
		Connection c = getConnection();
		
		String[] dataTables = new String[] {ICatchmentDelineatorDataSource.BLOCK_LAYER, ICatchmentDelineatorDataSource.HYDRO_EDGE_LAYER, ICatchmentDelineatorDataSource.WATERSHED_BOUNDARY_LAYER};
		for (String data : dataTables) {
			StringBuilder sb = new StringBuilder();
			sb.append("DELETE FROM ");
			sb.append(workingSchema + "." + data);
			sb.append(" WHERE ");
			sb.append(getAoiFieldName(null));
			sb.append(" = ? ");
			
			try(PreparedStatement ps = c.prepareStatement(sb.toString())){
				ps.setObject(1,  aoiUuid);
				ps.executeUpdate();
			}catch (SQLException ex) {
				throw new IOException(ex);
			}
		}
	}
	
	public SimpleFeatureType getHydroEdgeFT() {
		// Build a featureType for the HydroEdge features
		SimpleFeatureTypeBuilder sftBuilder = new SimpleFeatureTypeBuilder();
		sftBuilder.setName(ICatchmentDelineatorDataSource.HYDRO_EDGE_LAYER);
		sftBuilder.setCRS(crs);
		sftBuilder.add("drainageId", Integer.class);
		sftBuilder.add("waterSide", String.class);
		sftBuilder.add(getAoiFieldName(null), UUID.class);
		sftBuilder.add("geometry", LineString.class);
		return sftBuilder.buildFeatureType();
	}
	
	public SimpleFeatureType getBlockFT() {
		// Build a featureType for the Block features
		SimpleFeatureTypeBuilder sftBuilder = new SimpleFeatureTypeBuilder();
		sftBuilder.setName(ICatchmentDelineatorDataSource.BLOCK_LAYER);
		sftBuilder.setCRS(crs);
		sftBuilder.add("id", Integer.class);
		sftBuilder.add("state", Integer.class);
		sftBuilder.add(getAoiFieldName(null), UUID.class);
		sftBuilder.add("geometry", Polygon.class);
		return sftBuilder.buildFeatureType();
	}

	public SimpleFeatureType getWatershedBoundaryEdgeFT() {
		// Build a featureType for the Catchment features
		SimpleFeatureTypeBuilder sftBuilder = new SimpleFeatureTypeBuilder();
		sftBuilder.setName(ICatchmentDelineatorDataSource.WATERSHED_BOUNDARY_LAYER);
		sftBuilder.setCRS(crs);
		sftBuilder.add("leftDrainageId", Integer.class);
		sftBuilder.add("rightDrainageId", Integer.class);
		sftBuilder.add(getAoiFieldName(null), UUID.class);
		sftBuilder.add("geometry", LineString.class);
		return sftBuilder.buildFeatureType();
	}
	
	public synchronized boolean createLayer(SimpleFeatureType ft, ReferencedEnvelope workingExtent) {		
		try {
			//TODO: add aoi_id to this
			workingDataStore.getSchema(ft.getTypeName());
			return false;
		}catch (IOException ex) {
		}
		try {
			workingDataStore.createSchema(ft);
		}catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
		return true;

	}
	
	private Filter getAoiFilter(String layerName) {
		String id = "aoi_id";
		if (layerName.equals(Layer.AOI.getLayerName())) {
			id = "id";
		}
		return ff.equals(ff.property(id), ff.literal(aoiUuid));
	}

	public synchronized FeatureReader<SimpleFeatureType, SimpleFeature> query(String layerName, ReferencedEnvelope bounds, Filter filter) throws IOException {
		
		SimpleFeatureType schema = workingDataStore.getSchema(layerName);
		
        List<Filter> filters = new ArrayList<>();
        filters.add(getAoiFilter(layerName));
        
        if (bounds != null) {
        	String geometryPropertyName = schema.getGeometryDescriptor().getLocalName();
        	filters.add(filterFromEnvelope(bounds, ff.property(geometryPropertyName)));
        }
        
        if (filter != null) {
        	filters.add(filter);
        }
        
        Query query = new Query( layerName, ff.and(filters));
        
		return workingDataStore.getFeatureReader(query, Transaction.AUTO_COMMIT );
	}



	public synchronized void deleteFeatures(String name, Filter filter) {
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "Deleting previous " + name + " features");
		try {
			SimpleFeatureType ftype = workingDataStore.getSchema(name);
			if (ftype == null) {
				// nothing to delete
				return;
			}
			if(filter == null) {
				filter = Filter.INCLUDE;
			}
			Transaction tx = new DefaultTransaction();
			try { 
				
				FeatureWriter<SimpleFeatureType, SimpleFeature> writer = workingDataStore.getFeatureWriter(ftype.getTypeName(), filter, tx);
				int count = 0;
				while(writer.hasNext()) {
					count++;
					writer.next();
					writer.remove();
				}
				writer.close();
				tx.commit();
				stats.reportStatus(logger, "Deleted " + count + " previous " + name + " features.");
			} catch(IOException ioe) {
				tx.rollback();
				throw new RuntimeException(ioe);
			} finally {
				tx.close();
			}
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}

	public synchronized <T> void writeObjects(String layerName, Collection<T> data, BiConsumer<T,SimpleFeature> func) {
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "Writing " + data.size() + " " + layerName + " features");
		
		try {
			SimpleFeatureType type = workingDataStore.getSchema(layerName);
			
			Transaction tx = new DefaultTransaction();
			try {
				FeatureWriter<SimpleFeatureType, SimpleFeature> writer = workingDataStore.getFeatureWriterAppend(type.getTypeName(), tx);
				for(T datum : data) {
					SimpleFeature f = writer.next();
					func.accept(datum, f);
					f.setAttribute("aoi_id", aoiUuid);
					writer.write();
				}
				writer.close();
				tx.commit();
			} catch(IOException ioe) {
				tx.rollback();
				throw new RuntimeException(ioe);
			} finally {
				tx.close();
			}
			stats.reportStatus(logger, layerName + " features written.");
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}
	
	public synchronized void updateBlock(DataBlock dataBlock) {
		logger.trace("Updating Block Status for " + dataBlock);
		try {
			
			SimpleFeatureType ftype = workingDataStore.getSchema(BLOCK_LAYER);
			Filter filter = ff.equals(ff.property("id"), ff.literal(dataBlock.getId()));
			Transaction tx = new DefaultTransaction();
			try { 
				
				FeatureWriter<SimpleFeatureType, SimpleFeature> writer = workingDataStore.getFeatureWriter(ftype.getTypeName(), filter, tx);
				while(writer.hasNext()) {
					SimpleFeature f = writer.next();
					f.setAttribute("state", dataBlock.getState().id);
					writer.write();
				}
				writer.close();
				tx.commit();
				logger.trace("Block Status Updated.");
			} catch(IOException ioe) {
				tx.rollback();
				throw new RuntimeException(ioe);
			} finally {
				tx.close();
			}
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}

	public void reprecisionAll() throws IOException {
		
		ProcessStatistics stats = new ProcessStatistics();
		Connection c = getConnection();
		
		for (Layer layer : Layer.values()) {
			StringBuilder sb = new StringBuilder();
			sb.append("UPDATE ");
			sb.append(getTableName(layer));
			sb.append(" SET geometry = ST_ReducePrecision(geometry, ");
			sb.append(WatershedSettings.getPrecisionModel().getScale() + ")");
			
			try(Statement s = c.createStatement()){
				s.executeUpdate(sb.toString());
			}catch (SQLException ex) {
				throw new IOException(ex);
			}
			
			stats.reportStatus(logger, "Precision reduction applied to layer " + layer.name());
		}
		
//		ProcessStatistics stats = new ProcessStatistics();
//		for(FeatureEntry fe : geopkg.features()) {
//			int count = 0;
//			try(Transaction tx = new DefaultTransaction()) {
//				SimpleFeatureWriter writer = geopkg.writer(fe, false, Filter.INCLUDE, tx);
//				while(writer.hasNext()) {
//					count++;
//					SimpleFeature f = writer.next();
//					f.setDefaultGeometry(GeometryPrecisionReducer.reduce((Geometry)f.getDefaultGeometry(), WatershedSettings.getPrecisionModel()));
//					writer.write();
//				}
//				writer.close();
//				tx.commit();
//			}
//			stats.reportStatus(logger, "Precision reduction applied to layer " + fe.getTableName() + ": " + count + " features");
//		}
	}

}
