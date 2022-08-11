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
package net.refractions.chyf.flowpathconstructor.rank;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

import org.geotools.data.FeatureReader;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfLogger;
import net.refractions.chyf.ExceptionWithLocation;
import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.flowpathconstructor.ChyfProperties;
import net.refractions.chyf.flowpathconstructor.FlowpathArgs;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathPostGisLocalDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;

/**
 * Main class for computing Rank in dataset
 * @author Emily
 *
 */
public class RankEngine {
	
	static final Logger logger = LoggerFactory.getLogger(RankEngine.class.getCanonicalName());

	public static void doWork(Path output, ChyfProperties properties) throws Exception {
		try(FlowpathGeoPackageDataSource dataSource = new FlowpathGeoPackageDataSource(output)){
			try {
				doWork(dataSource, properties);
			}catch (ExceptionWithLocation ex) {
				ChyfLogger.INSTANCE.logException(ChyfLogger.Process.RANK, ex);
				throw ex;
			}catch (Exception ex) {
				ChyfLogger.INSTANCE.logException(ChyfLogger.Process.RANK, ex);
				throw ex;
			}				
		}
	}
	
	public static void doWork(IFlowpathDataSource dataSource, ChyfProperties properties) throws Exception {
		logger.info("build graph");
		RGraph graph = new RGraph();
		CoordinateReferenceSystem crs;
		
		if (properties == null) properties = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());

		logger.info("adding attribute");
		try {
			dataSource.addRankAttribute();
		}catch (Exception ex) {
			logger.error("Rank attribute cannot be added to dataset.  It likely already exists.  Please remove it from the dataset before proceeding.");
			throw ex;
		}
		
		logger.info("loading flowpaths");
		try(FeatureReader<SimpleFeatureType, SimpleFeature> reader = dataSource.query(Layer.EFLOWPATHS)){
			
			Name eftypeatt = ChyfDataSource.findAttribute(reader.getFeatureType(), ChyfAttribute.EFTYPE);
			crs = reader.getFeatureType().getCoordinateReferenceSystem();
			while(reader.hasNext()) {
				SimpleFeature sf = reader.next();
				graph.addEdge(sf, eftypeatt);	
			}
		}
		
		logger.info("computing ranks");
		RankComputer engine = new RankComputer(crs, dataSource, properties);
		engine.computeRank(graph);

		logger.info("saving results");
		Map<FeatureId, RankType> data = graph.getEdges().stream().collect(Collectors.toMap(REdge::getID, REdge::getRank));
		dataSource.writeRanks(data);
	}
	
	public static void main(String[] args) throws Exception {		
		FlowpathArgs runtime = new FlowpathArgs("RankEngine");
		if (!runtime.parseArguments(args)) return;
			
		long now = System.nanoTime();
		IFlowpathDataSource dataSource = null;
		try{
			if (runtime.isGeopackage()) {
				Path input = Paths.get(runtime.getInput());
				Path output = Paths.get(runtime.getOutput());
				ChyfGeoPackageDataSource.prepareOutput(input, output);
				
				dataSource = new FlowpathGeoPackageDataSource(output);
			}else if (runtime.isPostigs()){
				if (!runtime.hasAoi()) return;
				dataSource = new FlowpathPostGisLocalDataSource(runtime.getDbConnectionString(), runtime.getInput(), runtime.getOutput());
			}
			ChyfProperties prop = runtime.getPropertiesFile();
			if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			RankEngine.doWork(dataSource, prop);
			dataSource.finish();
		}finally {
			if (dataSource != null) dataSource.close();
		}
		long then = System.nanoTime();
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	
	}
}