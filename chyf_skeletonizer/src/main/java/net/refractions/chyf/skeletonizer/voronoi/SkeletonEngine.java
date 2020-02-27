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
package net.refractions.chyf.skeletonizer.voronoi;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.Args;
import net.refractions.chyf.ChyfProperties;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;

/**
 * Manages the running of the skeletonizer  
 * 
 * @author Emily
 *
 */
public class SkeletonEngine {
	
	static final Logger logger = LoggerFactory.getLogger(SkeletonEngine.class.getCanonicalName());
	
	public static void doWork(Path output, ChyfProperties props, int cores ) throws Exception {
		try(ChyfGeoPackageDataSource dataSource = new ChyfGeoPackageDataSource(output)){
			if (props == null) props = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());

			dataSource.addProcessedAttribute();
			dataSource.removeExistingSkeletons(false);
			
			SkeletonGenerator generator = new SkeletonGenerator(props);
		
			//break up tasks
			ExecutorService service = Executors.newFixedThreadPool(4);
			List<SkeletonJob> tasks = new ArrayList<>();
			for (int i = 0; i < cores; i ++) {
				SkeletonJob j1 = new SkeletonJob(dataSource, generator);
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
	}

	
	public static void main(String[] args) throws Exception {
		Args runtime = Args.parseArguments(args, "SkeletonEngine");
		if (runtime == null) return;
		
		runtime.prepareOutput();
		
		long now = System.nanoTime();
		SkeletonEngine.doWork(runtime.getOutput(), runtime.getPropertiesFile(), runtime.getCores());
		long then = System.nanoTime();
		
		System.out.println("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
}
