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
package net.refractions.chyf.flowpathconstructor;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfLogger;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.datasource.ProcessingState;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathPostGisLocalDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
import net.refractions.chyf.flowpathconstructor.directionalize.DirectionalizeEngine;
import net.refractions.chyf.flowpathconstructor.rank.RankEngine;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.PointEngine;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkeletonEngine;

/**
 * Main class for running all flowpath constructor components
 * 
 * @author Emily
 *
 */
public class FlowpathConstructor {

	static final Logger logger = LoggerFactory.getLogger(FlowpathConstructor.class.getCanonicalName());

	public static void main(String[] args) throws Exception {
		
		FlowpathArgs runtime = new FlowpathArgs("FlowpathConstructor");
		if (!runtime.parseArguments(args)) return;
		
		long now = System.nanoTime();
		IFlowpathDataSource dataSource = null;
		try{
			if (runtime.isGeopackage()) {
				Path input = Paths.get(runtime.getInput());
				Path output = Paths.get(runtime.getOutput());
				ChyfGeoPackageDataSource.prepareOutput(input, output);
				
				dataSource = new FlowpathGeoPackageDataSource(output);
				runDatasource(dataSource, runtime);
			}else if (runtime.isPostigs()){
				if (runtime.hasAoi()) {
					dataSource = new FlowpathPostGisLocalDataSource(runtime.getDbConnectionString(), runtime.getInput(), runtime.getOutput());
					((FlowpathPostGisLocalDataSource)dataSource).setAoi(runtime.getAoi());	
					runDatasource(dataSource, runtime);
				}else {
					runAllAois(runtime);
				}
			}		
			
		}finally {
			if (dataSource != null) dataSource.close();
		}
		long then = System.nanoTime();
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}

	private static void runDatasource(IFlowpathDataSource dataSource, FlowpathArgs runtime) {
		try {
			try {
				ChyfProperties prop = runtime.getPropertiesFile();
				if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
				logger.info("Generating Constructions Points");
				PointEngine.doWork(dataSource, prop);
				logger.info("Generating Skeletons");
				SkeletonEngine.doWork(dataSource, prop, runtime.getCores());
				logger.info("Directionalizing Dataset");
				DirectionalizeEngine.doWork(dataSource, prop);
				logger.info("Computing Rank");
				RankEngine.doWork(dataSource, prop);
			}catch (Exception ex) {
				ChyfLogger.INSTANCE.logError(ex.getMessage(), null, ex, FlowpathConstructor.class);
			}
			
			dataSource.finish();
		}catch (IOException ex) {
			LoggerFactory.getLogger(FlowpathConstructor.class).error(ex.getMessage(), ex);
		}
	}
	
	private static void runAllAois(FlowpathArgs runtime) throws Exception {
		FlowpathPostGisLocalDataSource dataSource = new FlowpathPostGisLocalDataSource(runtime.getDbConnectionString(), runtime.getInput(), runtime.getOutput());
		try {
			ChyfProperties prop = runtime.getPropertiesFile();
			if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			while(true) {
				String aoi = dataSource.getNextAoiToProcess(ProcessingState.READY, ProcessingState.FP_PROCESSING);
				if (aoi == null) break;
				
				logger.info("Processing: " + aoi);
				
				try {
					dataSource.setAoi(aoi);
					logger.info("Generating Constructions Points");
					PointEngine.doWork(dataSource, prop);
					logger.info("Generating Skeletons");
					SkeletonEngine.doWork(dataSource, prop, runtime.getCores());
					logger.info("Directionalizing Dataset");
					DirectionalizeEngine.doWork(dataSource, prop);
					logger.info("Computing Rank");
					RankEngine.doWork(dataSource, prop);
					
					dataSource.finish();
					dataSource.setState(ProcessingState.FP_DONE);
				}catch (Exception ex) {
					ChyfLogger.INSTANCE.logError(ex.getMessage(), null, ex, FlowpathConstructor.class);
					dataSource.finish();
					dataSource.setState(ProcessingState.FP_ERROR);
				}
			}
		}finally {
			if (dataSource != null) dataSource.close();
		}
	}
}
