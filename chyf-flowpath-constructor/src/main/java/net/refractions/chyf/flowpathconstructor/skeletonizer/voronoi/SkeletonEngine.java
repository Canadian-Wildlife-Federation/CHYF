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
package net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.ChyfProperties;
import net.refractions.chyf.flowpathconstructor.FlowpathArgs;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathPostGisDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.WaterbodyIterator;

/**
 * Manages the running of the skeletonizer  
 * 
 * @author Emily
 *
 */
public class SkeletonEngine {
	
	static final Logger logger = LoggerFactory.getLogger(SkeletonEngine.class.getCanonicalName());
	
	public static void doWork(Path output, ChyfProperties props, int cores ) throws Exception {
		try(FlowpathGeoPackageDataSource dataSource = new FlowpathGeoPackageDataSource(output)){
			doWork(dataSource, props, cores);
		}
	}

	public static void doWork(IFlowpathDataSource dataSource, ChyfProperties properties, int cores) throws Exception {
		if (properties == null) properties = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());

		dataSource.removeExistingSkeletons(false);
		
		SkeletonGenerator generator = new SkeletonGenerator(properties);
	
		//break up tasks
		ExecutorService service = Executors.newFixedThreadPool(4);
		List<SkeletonJob> tasks = new ArrayList<>();
		WaterbodyIterator iterator = new WaterbodyIterator(dataSource);
		
		for (int i = 0; i < cores; i ++) {
			SkeletonJob j1 = new SkeletonJob(dataSource, iterator, generator);
			tasks.add(j1);
		}
		CompletableFuture<?>[] futures = tasks.stream()
		                               .map(task -> CompletableFuture.runAsync(task, service))
		                               .toArray(CompletableFuture[]::new);
		CompletableFuture.allOf(futures).join();    
		service.shutdown();
		
		//ensure all skeletons are written
		dataSource.writeSkeletons(Collections.emptyList());
		
		
		//check for errors
		for (SkeletonJob j : tasks) {
			j.getExceptions().forEach(e->logger.error(e.getMessage(), e));
		}
		
		for (SkeletonJob j : tasks) {
			j.getErrors().forEach(e->logger.error(e));
		}
	}
	
	public static void main(String[] args) throws Exception {
		FlowpathArgs runtime = new FlowpathArgs("SkeletonEngine");
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
				dataSource = new FlowpathPostGisDataSource(runtime.getDbConnectionString(), runtime.getInput(), runtime.getOutput());
			}
			ChyfProperties prop = runtime.getPropertiesFile();
			if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			SkeletonEngine.doWork(dataSource, prop, runtime.getCores());
			
		}finally {
			if (dataSource != null) dataSource.close();
		}
		long then = System.nanoTime();
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	
	}
	
	
}
