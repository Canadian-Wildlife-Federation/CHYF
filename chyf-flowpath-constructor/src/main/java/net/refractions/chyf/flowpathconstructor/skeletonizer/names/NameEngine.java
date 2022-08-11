/*
 * Copyright 2021 Canadian Wildlife Federation
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
package net.refractions.chyf.flowpathconstructor.skeletonizer.names;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfLogger;
import net.refractions.chyf.ExceptionWithLocation;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.ChyfProperties;
import net.refractions.chyf.flowpathconstructor.FlowpathArgs;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.NameFlowpathPostGisLocalDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.WaterbodyIterator;

/**
 * Attempts to apply names to skeletons inside waterbodies based on the names
 * on the contructions points and waterbodies
 * 
 * @author Emily
 *
 */
public class NameEngine {

	static final Logger logger = LoggerFactory.getLogger(NameEngine.class.getCanonicalName());

	public static void doWork(Path output, ChyfProperties properties, int cores) throws Exception {
		try(FlowpathGeoPackageDataSource dataSource = new FlowpathGeoPackageDataSource(output)){
			try {
				doWork(dataSource, properties, cores);
			}catch (ExceptionWithLocation ex) {
				ChyfLogger.INSTANCE.logException(ChyfLogger.Process.NAMING, ex);
				throw ex;
			}catch (Exception ex) {
				ChyfLogger.INSTANCE.logException(ChyfLogger.Process.NAMING, ex);
				throw ex;
			}
		}
	}	
	
	public static void doWork(IFlowpathDataSource dataSource, ChyfProperties properties, int cores) throws Exception {
		
		ExecutorService service = Executors.newFixedThreadPool(4);
		List<NameJob> tasks = new ArrayList<>();
		WaterbodyIterator iterator = new WaterbodyIterator(dataSource);
		
		for (int i = 0; i < cores; i ++) {
			NameJob job = new NameJob(dataSource, iterator);
			tasks.add(job);
		}
		CompletableFuture<?>[] futures = tasks.stream()
		                               .map(task -> CompletableFuture.runAsync(task, service))
		                               .toArray(CompletableFuture[]::new);
		CompletableFuture.allOf(futures).join();    
		service.shutdown();
		
		//check for errors
		boolean haserrors = false;
		for (NameJob j : tasks) {
			if (j.getException() != null) {
				Exception ex = j.getException();
				ChyfLogger.INSTANCE.logError(ChyfLogger.Process.NAMING, "Name Processing Error - " + ex.getMessage(), null, ex, NameEngine.class);
				logger.error(ex.getMessage(), ex);
				haserrors = true;
			}
		}
		
		if (haserrors) {
			throw new Exception("Error occured during naming process process - see log for details");
		}
		HashMap<FeatureId, String[]> allnames = new HashMap<>();
		for (NameJob j : tasks) {
			allnames.putAll(j.getNamedFlowpaths());
		}

		logger.info("Writing names to database");
		if (!allnames.isEmpty()) dataSource.writeFlowpathNames(allnames);
	}
	
	public static void main(String[] args) throws Exception {		
		
		FlowpathArgs runtime = new FlowpathArgs("NameEngine");
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
				dataSource = new NameFlowpathPostGisLocalDataSource(runtime.getDbConnectionString(), runtime.getInput(), runtime.getOutput());
				((NameFlowpathPostGisLocalDataSource)dataSource).setAoi(runtime.getAoi());
			}
			
			ChyfProperties prop = runtime.getPropertiesFile();
			if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			NameEngine.doWork(dataSource, prop, runtime.getCores());
			dataSource.finish();
		}finally {
			if (dataSource != null) dataSource.close();
		}
		long then = System.nanoTime();
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
}
