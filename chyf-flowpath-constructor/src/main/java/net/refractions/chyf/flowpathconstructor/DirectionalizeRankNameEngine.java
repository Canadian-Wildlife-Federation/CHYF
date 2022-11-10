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

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathPostGisLocalDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
import net.refractions.chyf.flowpathconstructor.directionalize.DirectionalizeEngine;
import net.refractions.chyf.flowpathconstructor.rank.RankEngine;
import net.refractions.chyf.flowpathconstructor.skeletonizer.names.NameEngine;

/**
 * Main class for computing direction, rank and names on dataset
 * @author Emily
 *
 */
public class DirectionalizeRankNameEngine {
	
	static final Logger logger = LoggerFactory.getLogger(DirectionalizeRankNameEngine.class.getCanonicalName());

	
	public static void main(String[] args) throws Exception {		
		FlowpathArgs runtime = new FlowpathArgs("DirectionalizeRankNameEngine");
		if (!runtime.parseArguments(args)) return;
			
		long now = System.nanoTime();
		IFlowpathDataSource dataSource = null;
		try{
			MDC.put("aoi_id", runtime.getLogFileName());
			
			if (runtime.isGeopackage()) {
				Path input = Paths.get(runtime.getInput());
				Path output = Paths.get(runtime.getOutput());
				ChyfGeoPackageDataSource.prepareOutput(input, output);
				
				dataSource = new FlowpathGeoPackageDataSource(output);
			}else if (runtime.isPostigs()){
				if (!runtime.hasAoi()) return;
				dataSource = new FlowpathPostGisLocalDataSource(runtime.getDbConnectionString(), runtime.getInput(), runtime.getOutput());
				((FlowpathPostGisLocalDataSource)dataSource).setAoi(runtime.getAoi());
			}
			ChyfProperties prop = runtime.getPropertiesFile();
			if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			
			try {
				logger.info("Directionalizing Dataset");
				DirectionalizeEngine.doWork(dataSource, prop);
				logger.info("Computing Rank");
				RankEngine.doWork(dataSource, prop);
				logger.info("Applying Names To Skeletons");
				NameEngine.doWork(dataSource, prop, runtime.getCores());
				dataSource.finish();
			}catch (Throwable ex) {
				logger.error(ex.getMessage(), ex);
				try {
					dataSource.finish();
				}catch (Throwable ex2) {
					logger.error(ex2.getMessage(), ex2);	
				}
			}
		}finally {
			if (dataSource != null) dataSource.close();
		}
		long then = System.nanoTime();
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
}