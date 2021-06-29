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
import java.util.Collection;
import java.util.function.BiConsumer;

import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.util.ProcessStatistics;
import net.refractions.chyf.watershed.WatershedSettings;

public class CatchmentDelineatorGeoPackageDataSource extends ChyfGeoPackageDataSource implements ICatchmentDelineatorDataSource {
	private static final Logger logger = LoggerFactory.getLogger(DataManager.class);
	
	public static final String HYDRO_EDGE_LAYER = "HydroEdges"; 
	public static final String WATERSHED_BOUNDARY_LAYER = "CatchmentConstructionEdges";
	public static final String BLOCK_LAYER = "ProcessingBlocks";
	

	public CatchmentDelineatorGeoPackageDataSource(Path geopackageFile) throws IOException {
		super(geopackageFile);
		
		addInternalIdAttribute();
		
	}
	
	public synchronized boolean createLayer(SimpleFeatureType ft, ReferencedEnvelope workingExtent) {
		try {
			FeatureEntry fe = geopkg.feature(ft.getTypeName());
			if(fe == null) {
				fe = new FeatureEntry();
				fe.setBounds(workingExtent);
				geopkg.create(fe, ft);
				geopkg.createSpatialIndex(fe);
				return true;
			}
			return false;
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}
	
	public SimpleFeatureType getHydroEdgeFT() {
		// Build a featureType for the HydroEdge features
		SimpleFeatureTypeBuilder sftBuilder = new SimpleFeatureTypeBuilder();
		sftBuilder.setName(ICatchmentDelineatorDataSource.HYDRO_EDGE_LAYER);
		sftBuilder.setCRS(crs);
		sftBuilder.add("drainageId", Integer.class);
		sftBuilder.add("waterSide", String.class);
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
		sftBuilder.add("geometry", LineString.class);
		return sftBuilder.buildFeatureType();
	}
	
	public synchronized SimpleFeatureReader query(String layerName, ReferencedEnvelope bounds, Filter filter) throws IOException {
		return query(geopkg.feature(layerName), bounds, filter);
	}

//	public synchronized void updateObjects(String layerName, Consumer<SimpleFeature> func) {
//		updateObjects(layerName, null, func);
//	}		
//
//	public synchronized void updateObjects(String layerName, Filter filter, Consumer<SimpleFeature> func) {
//		ProcessStatistics stats = new ProcessStatistics();
//		stats.reportStatus(logger, "Updating " + layerName + " features");
//		if(filter == null) filter = Filter.INCLUDE;
//		try {
//			FeatureEntry fe = geopkg.feature(layerName);	
//			Transaction tx = new DefaultTransaction();
//			int count = 0;
//			try {
//				SimpleFeatureWriter writer = geopkg.writer(fe, false, filter, tx);
//				while(writer.hasNext()) {
//					count++;
//					SimpleFeature f = writer.next();
//					func.accept(f);
//					writer.write();
//				}
//				writer.close();
//				tx.commit();
//			} catch(IOException ioe) {
//				ioe.printStackTrace();
//				tx.rollback();
//				throw new RuntimeException(ioe);
//			} finally {
//				tx.close();
//			}
//			stats.reportStatus(logger, count + " " + layerName + " features updated.");
//		} catch(IOException ioe) {
//			throw new RuntimeException(ioe);
//		}
//	}		

	public synchronized <T> void writeObjects(String layerName, Collection<T> data, BiConsumer<T,SimpleFeature> func) {
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "Writing " + data.size() + " " + layerName + " features");
		
		try {
			FeatureEntry fe = geopkg.feature(layerName);	
			Transaction tx = new DefaultTransaction();
			try {
				SimpleFeatureWriter writer = geopkg.writer(fe, true, null, tx);
				for(T datum : data) {
					SimpleFeature f = writer.next();
					func.accept(datum, f);
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

	public synchronized void deleteFeatures(String name, Filter filter) {
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "Deleting previous " + name + " features");
		try {
			FeatureEntry fe = geopkg.feature(name);
			if (fe == null) {
				// nothing to delete
				return;
			}
			if(filter == null) {
				filter = Filter.INCLUDE;
			}
			Transaction tx = new DefaultTransaction();
			try { 
				SimpleFeatureWriter writer = geopkg.writer(fe, false, filter, tx);
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

	public synchronized void updateBlock(DataBlock dataBlock) {
		logger.trace("Updating Block Status for " + dataBlock);
		try {
			FeatureEntry fe = geopkg.feature(BLOCK_LAYER);
			Filter filter = ff.equals(ff.property("id"), ff.literal(dataBlock.getId()));
			Transaction tx = new DefaultTransaction();
			try { 
				SimpleFeatureWriter writer = geopkg.writer(fe, false, filter, tx);
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
		for(FeatureEntry fe : geopkg.features()) {
			int count = 0;
			try(Transaction tx = new DefaultTransaction()) {
				SimpleFeatureWriter writer = geopkg.writer(fe, false, Filter.INCLUDE, tx);
				while(writer.hasNext()) {
					count++;
					SimpleFeature f = writer.next();
					f.setDefaultGeometry(GeometryPrecisionReducer.reduce((Geometry)f.getDefaultGeometry(), WatershedSettings.getPrecisionModel()));
					writer.write();
				}
				writer.close();
				tx.commit();
			}
			stats.reportStatus(logger, "Precision reduction applied to layer " + fe.getTableName() + ": " + count + " features");
		}
	}

}
