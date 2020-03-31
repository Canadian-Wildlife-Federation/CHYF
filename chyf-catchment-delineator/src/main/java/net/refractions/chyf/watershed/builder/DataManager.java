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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateSequences;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.index.strtree.STRtree;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.datasource.EcType;
import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.util.ProcessStatistics;
import net.refractions.chyf.util.ReprojectionUtils;
import net.refractions.chyf.watershed.WatershedSettings;
import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.WaterSide;
import net.refractions.chyf.watershed.model.WatershedBoundaryEdge;

public class DataManager {
	private static final Logger logger = LoggerFactory.getLogger(DataManager.class);

	private int srid;
	private CoordinateReferenceSystem crs;
	private GeometryFactory gf;

	private GeoTiffDirReader gridReader;
	private CatchmentDelineatorDataSource dataSource;

	private ReferencedEnvelope workingExtent;

	public DataManager(Path geoTiffDirPath, Path inputGeopackagePath, Path outputGeopackagePath, 
			boolean recover) throws IOException {
		logger.info("Processing input file: " + inputGeopackagePath);
		logger.info("Using DEM from dir: " + geoTiffDirPath);
		logger.info("Output to file " + outputGeopackagePath);
		if(recover) {
			logger.info("In recovery mode.");
		} else {
			logger.info("In normal processing mode.");
		}
		if(!recover) {
			ChyfGeoPackageDataSource.deleteOutputFile(outputGeopackagePath);
			Files.copy(inputGeopackagePath, outputGeopackagePath, StandardCopyOption.REPLACE_EXISTING);
		}
		dataSource = new CatchmentDelineatorDataSource(outputGeopackagePath);
		
		// determine the srid/crs to use
		srid = dataSource.getSrid(Layer.EFLOWPATHS);
		crs = ReprojectionUtils.srsCodeToCRS(srid);

		// load the appropriate properties
		WatershedSettings.load(crs);

		if(!recover) {
			dataSource.reprecisionAll();			
		}

		gf = new GeometryFactory(WatershedSettings.getPrecisionModel(), srid);

		gridReader = new GeoTiffDirReader(geoTiffDirPath.toString(), crs);
	}

	private SimpleFeatureType getHydroEdgeFT() {
		// Build a featureType for the HydroEdge features
		SimpleFeatureTypeBuilder sftBuilder = new SimpleFeatureTypeBuilder();
		sftBuilder.setName(CatchmentDelineatorDataSource.HYDRO_EDGE_LAYER);
		sftBuilder.setSRS("EPSG:" + srid);
		sftBuilder.add("drainageId", Integer.class);
		sftBuilder.add("waterSide", String.class);
		sftBuilder.add("geometry", LineString.class);
		return sftBuilder.buildFeatureType();
	}
	
	private SimpleFeatureType getBlockFT() {
		// Build a featureType for the Block features
		SimpleFeatureTypeBuilder sftBuilder = new SimpleFeatureTypeBuilder();
		sftBuilder.setName(CatchmentDelineatorDataSource.BLOCK_LAYER);
		sftBuilder.setSRS("EPSG:" + srid);
		sftBuilder.add("id", Integer.class);
		sftBuilder.add("state", Integer.class);
		sftBuilder.add("geometry", Polygon.class);
		return sftBuilder.buildFeatureType();
	}

	private SimpleFeatureType getWatershedBoundaryEdgeFT() {
		// Build a featureType for the Catchment features
		SimpleFeatureTypeBuilder sftBuilder = new SimpleFeatureTypeBuilder();
		sftBuilder.setName(CatchmentDelineatorDataSource.WATERSHED_BOUNDARY_LAYER);
		sftBuilder.setSRS("EPSG:" + srid);
		sftBuilder.add("leftDrainageId", Integer.class);
		sftBuilder.add("rightDrainageId", Integer.class);
		sftBuilder.add("geometry", LineString.class);
		return sftBuilder.buildFeatureType();
	}

	public synchronized List<DataBlock> generateBlocks(List<HydroEdge> edges) {
		STRtree edgeIndex = new STRtree();
		Envelope overallEnv = new Envelope();
		for(HydroEdge edge : edges) {
			Envelope env = edge.getLine().getEnvelopeInternal();
			overallEnv.expandToInclude(env);
			edgeIndex.insert(env, edge);
		}
		edgeIndex.build();
		
		int nextBlockId = 1;
		List<DataBlock> blocks = new ArrayList<DataBlock>();
		// and block geometry
		for(double lon = Math.floor(overallEnv.getMinX() / WatershedSettings.BLOCK_SIZE) * WatershedSettings.BLOCK_SIZE; lon < overallEnv.getMaxX(); lon += WatershedSettings.BLOCK_SIZE) {
			for(double lat = Math.floor(overallEnv.getMinY() / WatershedSettings.BLOCK_SIZE) * WatershedSettings.BLOCK_SIZE; lat < overallEnv.getMaxY(); lat += WatershedSettings.BLOCK_SIZE) {
				Envelope env = new Envelope(lon, lon + WatershedSettings.BLOCK_SIZE, lat, lat + WatershedSettings.BLOCK_SIZE);
				if(!edgeIndex.query(env).isEmpty()) {
					blocks.add(new DataBlock(nextBlockId++, env, BlockState.READY, this));
				}
			}
		}
		if(!dataSource.createLayer(getBlockFT(), workingExtent)) { 
			deleteBlocks();
		}
		writeBlocks(blocks);
		return blocks;
	}

	public List<Coordinate> getDEM(DataBlock block) {
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "loading DEM for " + block);
		Envelope env = block.getBufferedBounds();
		List<Coordinate> coords = gridReader.getDEM(env);
		stats.reportStatus(logger, "loaded " + coords.size() + " DEM points.");
		return coords;
	}
	
	public synchronized List<HydroEdge> getHydroEdges(DataBlock block) {
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "loading hydro edges for " + block);
		try {
			ReferencedEnvelope bufferedEnv = null;
			Geometry targetEnvPoly = null;
			if(block != null) {
				Envelope env = block.getBufferedBounds();
				bufferedEnv = new ReferencedEnvelope(env, crs);
				targetEnvPoly = gf.toGeometry(env);
			}
			List<HydroEdge> edges = new ArrayList<HydroEdge>();

			SimpleFeatureReader edgeReader = dataSource.query(CatchmentDelineatorDataSource.HYDRO_EDGE_LAYER, bufferedEnv, null);
			while (edgeReader.hasNext()) {
				SimpleFeature edge = edgeReader.next();
				LineString edgeGeom = (LineString) edge.getDefaultGeometry();
				Integer drainageId = (Integer)edge.getAttribute("drainageId");
				WaterSide waterSide = WaterSide.convert((String)edge.getAttribute("waterSide"));
				// ignore edges with water on both sides
				if(waterSide == WaterSide.BOTH) continue; 
				if(edgeGeom.getCoordinate() instanceof CoordinateXY) {
					CoordinateSequence newSeq = PackedCoordinateSequenceFactory.DOUBLE_FACTORY.create(edgeGeom.getNumPoints(), 3);
					CoordinateSequences.copy(edgeGeom.getCoordinateSequence(), 0, newSeq, 0, edgeGeom.getNumPoints());
					edgeGeom = gf.createLineString(newSeq);
				}
				if(targetEnvPoly == null || targetEnvPoly.intersects(edgeGeom)) {
					edges.add(new HydroEdge(edgeGeom, drainageId, waterSide));
				}
			}
			edgeReader.close();

			stats.reportStatus(logger, "loaded " + edges.size() + " hydro edges.");
			return edges;
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}
	
	public synchronized List<Geometry> getFlowpaths() {
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "loading flowpaths");
		try {
			List<Geometry> flowpaths = new ArrayList<Geometry>();

			SimpleFeatureReader fpReader = dataSource.query(Layer.EFLOWPATHS, dataSource.getEFlowpathTypeFilter(EfType.REACH));
			while (fpReader.hasNext()) {
				SimpleFeature fp = fpReader.next();
				Geometry fpGeom = (Geometry) fp.getDefaultGeometry();
				flowpaths.add(fpGeom);
			}
			fpReader.close();
			stats.reportStatus(logger, "loaded " + flowpaths.size() + " flowpaths.");
			return flowpaths;
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}
	
	public synchronized List<Catchment> getWaterbodies() {
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "loading waterbodies");
		try {
			List<Catchment> waterbodies = new ArrayList<Catchment>();
			SimpleFeatureReader wbReader = dataSource.query(Layer.ECATCHMENTS, dataSource.getECatchmentTypeFilter(EcType.WATER));
			while (wbReader.hasNext()) {
				SimpleFeature wb = wbReader.next();
				String internalId = (String) wb.getAttribute(ChyfDataSource.findAttribute(wb.getFeatureType(), ChyfAttribute.INTERNAL_ID));
				Polygon p = ChyfDataSource.getPolygon(wb);
				
				waterbodies.add(new Catchment(internalId, EcType.WATER.getChyfValue(), p));
			}
			wbReader.close();
			stats.reportStatus(logger, "loaded " + waterbodies.size() + " waterbodies.");
			return waterbodies;
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}

	public synchronized List<Geometry> getShorelines() {
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "loading shorelines");
		try {
			List<Geometry> coastlines = new ArrayList<Geometry>();
			SimpleFeatureReader clReader = dataSource.query(Layer.SHORELINES);
			if(clReader != null) {
				while (clReader.hasNext()) {
					SimpleFeature cl = clReader.next();
					Geometry clGeom = (Geometry) cl.getDefaultGeometry();
					coastlines.add(clGeom);
				}
				clReader.close();
			}
			stats.reportStatus(logger, "loaded " + coastlines.size() + " shorelines.");
			return coastlines;
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}
	
	public synchronized List<WatershedBoundaryEdge> getWatershedBoundaries() {
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "loading all watershed boundaries");
		try {
			List<WatershedBoundaryEdge> boundaryEdges = new ArrayList<WatershedBoundaryEdge>();
			SimpleFeatureReader wbReader = dataSource.query(CatchmentDelineatorDataSource.WATERSHED_BOUNDARY_LAYER, null, null);
			if(wbReader == null) {
				throw new RuntimeException("No Watershed Boundaries have been defined; Have you provided valid data, including DEM coverage?");
			}
			while (wbReader.hasNext()) {
				SimpleFeature f = wbReader.next();
				int left = (Integer)f.getAttribute("leftDrainageId");
				int right = (Integer)f.getAttribute("rightDrainageId");
				LineString line = (LineString)f.getDefaultGeometry();				

				WatershedBoundaryEdge edge = new WatershedBoundaryEdge(line, new boolean[] {false, false}, new int[] {left, right}, false, false); 

				boundaryEdges.add(edge);
			}
			wbReader.close();
			stats.reportStatus(logger, "loaded " + boundaryEdges.size() + " watershed boundary edges.");
			return boundaryEdges;
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}
	
	public List<DataBlock> getBlocks() {
		DataManager dm = this;
		return getObjects(CatchmentDelineatorDataSource.BLOCK_LAYER, new Function<SimpleFeature, DataBlock>() {

			@Override
			public DataBlock apply(SimpleFeature f) {
				return new DataBlock(
						(Integer)f.getAttribute("id"), 
						((Geometry)f.getDefaultGeometry()).getEnvelopeInternal(),
						BlockState.fromId((Integer)f.getAttribute("state")),
						dm);
			}
			
		});
	}

	public synchronized <T> List<T> getObjects(String name, Function<SimpleFeature, T> func) {
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "loading all " + name + " features");
		try {
			List<T> data = new ArrayList<T>();
			SimpleFeatureReader featureReader = dataSource.query(name, null, null);
			while (featureReader.hasNext()) {
				data.add(func.apply(featureReader.next()));
			}
			featureReader.close();
			stats.reportStatus(logger, "loaded " + data.size() + " " + name + " features");
			return data;
		} catch(IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}

	public synchronized void writeWatershedBoundaries(Collection<WatershedBoundaryEdge> watershedBoundaries) {
		dataSource.createLayer(getWatershedBoundaryEdgeFT(), workingExtent);
		dataSource.writeObjects(CatchmentDelineatorDataSource.WATERSHED_BOUNDARY_LAYER, watershedBoundaries, new BiConsumer<WatershedBoundaryEdge,SimpleFeature>() {

			@Override
			public void accept(WatershedBoundaryEdge edge, SimpleFeature f) {
				f.setAttribute("leftDrainageId", edge.getRegionID(WatershedBoundaryEdge.LEFT));
				f.setAttribute("rightDrainageId", edge.getRegionID(WatershedBoundaryEdge.RIGHT));
				f.setDefaultGeometry(edge.getGeometry());				
			}
			
		});
	}
	
	public synchronized void writeHydroEdges(Collection<HydroEdge> hydroEdges) {
		if(!dataSource.createLayer(getHydroEdgeFT(), workingExtent)) {
			deleteHydroEdges();
		}
		dataSource.writeObjects(CatchmentDelineatorDataSource.HYDRO_EDGE_LAYER, hydroEdges, new BiConsumer<HydroEdge,SimpleFeature>() {

			@Override
			public void accept(HydroEdge edge, SimpleFeature f) {
				f.setAttribute("drainageId", edge.getDrainageID());
				f.setAttribute("waterSide", edge.getWaterSide());
				f.setDefaultGeometry(edge.getLine());
			}
			
		});
	}
	
	public synchronized void writeBlocks(Collection<DataBlock> blocks) {
		dataSource.writeObjects(CatchmentDelineatorDataSource.BLOCK_LAYER, blocks, new BiConsumer<DataBlock,SimpleFeature>() {

			@Override
			public void accept(DataBlock block, SimpleFeature f) {
				f.setAttribute("id", block.getId());
				f.setAttribute("state", block.getState().id);
				f.setDefaultGeometry(JTS.toPolygon(JTS.toRectangle2D(block.getBounds())));
			}
			
		});
	}
	
//	public synchronized void updateWaterbodies(List<Catchment> waterbodies) {
//		Map<String,Catchment> waterbodyMap = waterbodies.stream().collect(Collectors.toMap(Catchment::getInternalId, Function.identity()));
//		
//		dataSource.updateObjects(Layer.ECATCHMENTS.getLayerName(), new Consumer<SimpleFeature>() {
//
//			@Override
//			public void accept(SimpleFeature f) {
//				Catchment c = waterbodyMap.get(f.getAttribute(ChyfDataSource.findAttribute(f.getFeatureType(), ChyfAttribute.INTERNAL_ID)));
//				if(c != null) {
//					f.setDefaultGeometry(c.getPoly());
//				}
//			}
//			
//		});
//	}

	public void writeCatchments(List<Catchment> watersheds) {
		dataSource.writeObjects(Layer.ECATCHMENTS.getLayerName(), watersheds, new BiConsumer<Catchment,SimpleFeature>() {

			@Override
			public void accept(Catchment c, SimpleFeature f) {
				//f.setAttribute("drainage_id", wb.getDrainageId());
				f.setAttribute(ChyfDataSource.findAttribute(f.getFeatureType(), ChyfAttribute.INTERNAL_ID), c.getInternalId());
				f.setAttribute(ChyfDataSource.findAttribute(f.getFeatureType(), ChyfAttribute.ECTYPE), c.getCatchmentType());
				f.setDefaultGeometry(c.getPoly());
			}
			
		});
	}

	public void deleteHydroEdges() {
		dataSource.deleteFeatures(CatchmentDelineatorDataSource.HYDRO_EDGE_LAYER, null);
	}
	
	public void deleteBlocks()  {
		dataSource.deleteFeatures(CatchmentDelineatorDataSource.BLOCK_LAYER, null);
	}

	public synchronized void deleteECatchments(EcType... ecTypes) {
		dataSource.deleteFeatures(Layer.ECATCHMENTS.getLayerName(), dataSource.getECatchmentTypeFilter(ecTypes));
	}

	public void updateBlock(DataBlock dataBlock) {
		dataSource.updateBlock(dataBlock);
	}

	public GeometryFactory getGeometryFactory() {
		return gf;
	}

	public void setWorkingExtent(Envelope overallEnv) {
		overallEnv.expandBy(WatershedSettings.BLOCK_SIZE);
		workingExtent = new ReferencedEnvelope(overallEnv, crs);
	}

}
