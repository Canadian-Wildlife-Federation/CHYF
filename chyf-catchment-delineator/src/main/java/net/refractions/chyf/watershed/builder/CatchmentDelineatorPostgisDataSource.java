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
import net.refractions.chyf.datasource.ILayer;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.util.ProcessStatistics;
import net.refractions.chyf.watershed.WatershedSettings;

/**
 * Postgis data source for catchment delineator.
 * 
 * @author Emily
 *
 */
public class CatchmentDelineatorPostgisDataSource extends ChyfPostGisDataSource implements ICatchmentDelineatorDataSource{

	private static final Logger logger = LoggerFactory.getLogger(DataManager.class);

	public CatchmentDelineatorPostgisDataSource(String connectionString, String inschema, 
			String outschema) throws IOException {
		super(connectionString, inschema, outschema);
	}
	
	/**
	 * Clears any existing data from the output tables 
	 * 
	 * @throws IOException
	 */
	public void clearOutputTables() throws IOException {
		Connection c = getConnection();
		
		for (CatchmentLayer l : CatchmentLayer.values()) {
			
			SimpleFeatureType schema = null;
			try{
				schema = workingDataStore.getSchema(getTypeName(l));
			}catch (Exception ex) {
				
			}
			if (schema != null) {
				StringBuilder sb = new StringBuilder();
				sb.append("DELETE FROM ");
				sb.append(getTableName(l));
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
	}
	
	@Override
	public SimpleFeatureType getHydroEdgeFT() {
		// Build a featureType for the HydroEdge features
		SimpleFeatureTypeBuilder sftBuilder = new SimpleFeatureTypeBuilder();
		sftBuilder.setName(getTypeName(CatchmentLayer.HYDRO_EDGE_LAYER));
		sftBuilder.setCRS(crs);
		sftBuilder.add("drainageId", Integer.class);
		sftBuilder.add("waterSide", String.class);
//		sftBuilder.add(getAoiFieldName(null), UUID.class);
		sftBuilder.add("geometry", LineString.class);
		return sftBuilder.buildFeatureType();
	}
	
	@Override
	public SimpleFeatureType getBlockFT() {
		// Build a featureType for the Block features
		SimpleFeatureTypeBuilder sftBuilder = new SimpleFeatureTypeBuilder();
		sftBuilder.setName(getTypeName(CatchmentLayer.BLOCK_LAYER));
		sftBuilder.setCRS(crs);
		sftBuilder.add("id", Integer.class);
		sftBuilder.add("state", Integer.class);
//		sftBuilder.add(getAoiFieldName(null), UUID.class);
		sftBuilder.add("geometry", Polygon.class);
		SimpleFeatureType type = sftBuilder.buildFeatureType();
		
//		AttributeDescriptor ad = type.getAttributeDescriptors().get(2);
////		userData=(
////				org.geotools.jdbc.nativeType ==> 1111
////				org.geotools.jdbc.pk.column ==> true
////				org.geotools.jdbc.nativeTypeName ==> uuid)
//		ad.getUserData().put("org.geotools.jdbc.nativeTypeName", "uuid");
//		ad.getUserData().put("org.geotools.jdbc.nativeType", 1111);
		return type;
	}

	@Override
	public SimpleFeatureType getWatershedBoundaryEdgeFT() {
		// Build a featureType for the Catchment features
		SimpleFeatureTypeBuilder sftBuilder = new SimpleFeatureTypeBuilder();
		sftBuilder.setName(getTypeName(CatchmentLayer.WATERSHED_BOUNDARY_LAYER));
		sftBuilder.setCRS(crs);
		sftBuilder.add("leftDrainageId", Integer.class);
		sftBuilder.add("rightDrainageId", Integer.class);
		sftBuilder.add("geometry", LineString.class);
		return sftBuilder.buildFeatureType();
	}
	
	@Override
	public synchronized boolean createLayer(SimpleFeatureType ft, ReferencedEnvelope workingExtent) {		

		try {
			workingDataStore.getSchema(ft.getTypeName());
			return false;
		}catch (IOException ex) {
		}
		try {
			//workingDataStore.createSchema(ft) doesn't support UUID data types in postgis
			//so instead of adding the aoi to the feature type builder I'm adding it as
			//an alter table statement 
			workingDataStore.createSchema(ft);
			
			Connection c = getConnection();
			StringBuilder sb = new StringBuilder();
			sb.append("ALTER TABLE ");
			sb.append(workingSchema + "." + ft.getTypeName().toLowerCase());
			sb.append(" ADD COLUMN ");
			sb.append(getAoiFieldName(null));
			sb.append(" uuid NOT NULL ");
			
			try(Statement s = c.createStatement()){
				s.execute(sb.toString());
			}catch(SQLException ex) {
				throw new IOException (ex);
			}
			
		}catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
		return true;

	}

	@Override
	public synchronized FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer, ReferencedEnvelope bounds, Filter filter) throws IOException {
		
		SimpleFeatureType schema = workingDataStore.getSchema(getTypeName(layer));
		
        List<Filter> filters = new ArrayList<>();
        filters.add(getAoiFilter(layer));
        
        if (bounds != null) {
        	String geometryPropertyName = schema.getGeometryDescriptor().getLocalName();
        	filters.add(filterFromEnvelope(bounds, ff.property(geometryPropertyName)));
        }
        
        if (filter != null) {
        	filters.add(filter);
        }
        
        Query query = new Query( schema.getTypeName(), ff.and(filters));
        
		return workingDataStore.getFeatureReader(query, Transaction.AUTO_COMMIT );
	}

	@Override
	protected String getTypeName(ILayer layer) {
		if (layer == CatchmentLayer.BLOCK_LAYER) return layer.getLayerName().toLowerCase();
		if (layer == CatchmentLayer.HYDRO_EDGE_LAYER) return layer.getLayerName().toLowerCase();
		if (layer == CatchmentLayer.WATERSHED_BOUNDARY_LAYER) return layer.getLayerName().toLowerCase();
		return super.getTypeName(layer);
	}

	@Override
	public synchronized void deleteFeatures(ILayer layer, Filter filter) {

		//might be more efficient IF you convert the filter to a where statement
//		Connection c = getConnection(tx);
//		
//		StringBuilder sb = new StringBuilder();
//		sb.append("DELETE FROM ");
//		sb.append(getTableName(layer));
//		sb.append( " WHERE ");
//		sb.append(getAoiFieldName(null));
//		sb.append(" = ? ");
//		try(PreparedStatement ps = c.prepareStatement(sb.toString())){
//			ps.setObject(1, aoiUuid);
//			ps.executeUpdate();
//		}catch (SQLException ex) {
//			throw new IOException(ex);
//		}

		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "Deleting previous " + layer.getLayerName() + " features");
		try {
			SimpleFeatureType ftype = null;
			try{
				ftype = workingDataStore.getSchema(getTypeName(layer));
			}catch(Exception ex) {}
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
				stats.reportStatus(logger, "Deleted " + count + " previous " + layer.getLayerName() + " features.");
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

	
	public synchronized <T> void writeObjects(ILayer layer, Collection<T> data, BiConsumer<T,SimpleFeature> func) {
		
		
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "Writing " + data.size() + " " + layer.getLayerName() + " features");
		
		try {
			SimpleFeatureType type = workingDataStore.getSchema(getTypeName(layer));
			
			Transaction tx = new DefaultTransaction();
			try {
				FeatureWriter<SimpleFeatureType, SimpleFeature> writer = workingDataStore.getFeatureWriterAppend(type.getTypeName(), tx);
				for(T datum : data) {
					SimpleFeature f = writer.next();
					func.accept(datum, f);
					f.setAttribute(getAoiFieldName(null), aoiUuid);
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
			stats.reportStatus(logger, layer.getLayerName() + " features written.");
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}
	
	@Override
	public synchronized void updateBlock(DataBlock dataBlock) {
		logger.trace("Updating Block Status for " + dataBlock);
		try {
			
			SimpleFeatureType ftype = workingDataStore.getSchema(getTypeName(CatchmentLayer.BLOCK_LAYER));
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

	@Override
	public void reprecisionAll() throws IOException {
		
		ProcessStatistics stats = new ProcessStatistics();
		Connection c = getConnection();
		
		for (Layer layer : Layer.values()) {
			
			SimpleFeatureType fs = workingDataStore.getSchema(getTypeName(layer));
			
			StringBuilder sb = new StringBuilder();
			sb.append("UPDATE ");
			sb.append(getTableName(layer));
			sb.append(" SET ");
			sb.append(fs.getGeometryDescriptor().getLocalName());
//			sb.append(" = ST_SnapToGrid(");
			sb.append(" = ST_ReducePrecision(");
			sb.append(fs.getGeometryDescriptor().getLocalName());
			sb.append(", ?)");
			sb.append(" WHERE ");
			sb.append (getAoiFieldName(layer));
			sb.append(" = ? ");
			
			try(PreparedStatement ps = c.prepareStatement(sb.toString())){
				ps.setDouble(1, 1 / WatershedSettings.getPrecisionModel().getScale() );
				ps.setObject(2, aoiUuid);
				ps.executeUpdate();
			}catch (SQLException ex) {
				throw new IOException(ex);
			}
			
			stats.reportStatus(logger, "Precision reduction applied to layer " + layer.name());
		}
	}

}
