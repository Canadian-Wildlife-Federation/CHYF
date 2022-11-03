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
import org.slf4j.MDC;

import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.datasource.ProcessingState;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathPostGisLocalDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
import net.refractions.chyf.flowpathconstructor.directionalize.DirectionalizeEngine;
import net.refractions.chyf.flowpathconstructor.rank.RankEngine;
import net.refractions.chyf.flowpathconstructor.skeletonizer.names.NameEngine;
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
				MDC.put("aoi_id", runtime.getAoi());
				logger.warn("Processing AOI: " + runtime.getAoi());
				
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
				logger.info("Applying Names To Skeletons");
				NameEngine.doWork(dataSource, prop, runtime.getCores());
			}catch (Throwable ex) {
				logger.error(ex.getMessage(), ex);
			}
			
			dataSource.finish();
		}catch (IOException ex) {
			logger.error(ex.getMessage(), ex);
		}
	}
	
	private static void runAllAois(FlowpathArgs runtime) throws Exception {
		FlowpathPostGisLocalDataSource dataSource = new FlowpathPostGisLocalDataSource(runtime.getDbConnectionString(), runtime.getInput(), runtime.getOutput());
		try {
			ChyfProperties prop = runtime.getPropertiesFile();
			if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			
			while(true) {
				String[] next = dataSource.getNextAoiToProcess(ProcessingState.READY, ProcessingState.FP_PROCESSING);
				if (next == null || next[0] == null) break;
				String aoi = next[0];
				
				
				MDC.put("aoi_id", aoi);
				logger.info("Processing: " + aoi);
				try {
					dataSource.setAoi(aoi);
					
					ChyfProperties localprop = prop;
					if (next[1] != null) localprop = ChyfProperties.getProperties(next[1]);
					
					logger.info("Generating Constructions Points");
					PointEngine.doWork(dataSource, localprop);
					logger.info("Generating Skeletons");
					SkeletonEngine.doWork(dataSource, localprop, runtime.getCores());
					logger.info("Directionalizing Dataset");
					DirectionalizeEngine.doWork(dataSource, localprop);
					logger.info("Computing Rank");
					RankEngine.doWork(dataSource, localprop);
					logger.info("Applying Names To Skeletons");
					NameEngine.doWork(dataSource, localprop, runtime.getCores());
					dataSource.finish();
					dataSource.setState(ProcessingState.FP_DONE);
				}catch (Throwable ex) {
					logger.error(ex.getMessage(), ex);
					try {
						dataSource.finish();
					}catch (Throwable ex2) {
						logger.error(ex2.getMessage(), ex2);	
					}
					
					dataSource.setState(ProcessingState.FP_ERROR);
				}
			}
		}finally {
			if (dataSource != null) dataSource.close();
		}
	}
}
