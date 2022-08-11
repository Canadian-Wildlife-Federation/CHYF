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
package net.refractions.chyf.flowpathconstructor.directionalize;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfLogger;
import net.refractions.chyf.ExceptionWithLocation;
import net.refractions.chyf.flowpathconstructor.ChyfProperties;
import net.refractions.chyf.flowpathconstructor.ReadOnlyFlowpathArgs;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.ReadOnlyFlowpathGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.ReadOnlyFlowpathPostGisDataSource;

/**
 * Main class for validating no cycles exist in dataset
 * 
 * @author Emily
 *
 */
public class CycleCheckEngine {
	
	static final Logger logger = LoggerFactory.getLogger(CycleCheckEngine.class.getCanonicalName());

	public static void doWork(Path output, ChyfProperties properties) throws Exception {
		try(FlowpathGeoPackageDataSource dataSource = new FlowpathGeoPackageDataSource(output)){
			try {
				doWork(dataSource, properties);
			}catch (ExceptionWithLocation ex) {
				ChyfLogger.INSTANCE.logException(ChyfLogger.Process.CYCLE, ex);
				throw ex;
			}catch (Exception ex) {
				ChyfLogger.INSTANCE.logException(ChyfLogger.Process.CYCLE, ex);
				throw ex;
			}
		}
	}
	
	public static void doWork(IFlowpathDataSource dataSource, ChyfProperties properties) throws Exception {
		if (properties == null) properties = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
		logger.info("checking output for cycles");
		CycleChecker checker = new CycleChecker();
		if (checker.checkCycles(dataSource)) {
			throw new Exception("Dataset contains cycles.");
		}
	}

	
	public static void main(String[] args) throws Exception {		
		ReadOnlyFlowpathArgs runtime = new ReadOnlyFlowpathArgs("CycleCheckEngine");
		
		if (!runtime.parseArguments(args)) return;
			
		long now = System.nanoTime();
		IFlowpathDataSource dataSource = null;
		try{
			if (runtime.isGeopackage()) {
				dataSource = new ReadOnlyFlowpathGeoPackageDataSource(Paths.get(runtime.getDataset()));
			}else if (runtime.isPostigs()){
				if (!runtime.hasAoi()) {
					logger.error("AOI required for cycle checking on PostGIS data source");
					runtime.printUsage();
					return;
				}
				dataSource = new ReadOnlyFlowpathPostGisDataSource(runtime.getDbConnectionString(), runtime.getDataset());
				((ReadOnlyFlowpathPostGisDataSource)dataSource).setAoi(runtime.getAoi());
			}
			ChyfProperties prop = (ChyfProperties) runtime.getPropertiesFile();
			if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			CycleCheckEngine.doWork(dataSource, prop);
			dataSource.finish();
		}finally {
			if (dataSource != null) dataSource.close();
		}
		long then = System.nanoTime();
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
}