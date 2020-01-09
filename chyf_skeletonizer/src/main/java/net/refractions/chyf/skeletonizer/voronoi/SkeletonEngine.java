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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfProperties;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.skeletonizer.points.PointEngine;

/**
 * Manages the running of the skeletonizer  
 * 
 * @author Emily
 *
 */
public class SkeletonEngine {
	
	static final Logger logger = LoggerFactory.getLogger(PointEngine.class.getCanonicalName());

	private static int NUM_JOBS = 4;
	
	public static void doWork(Path output, ChyfProperties props ) throws Exception {
		try(ChyfGeoPackageDataSource dataSource = new ChyfGeoPackageDataSource(output)){
			dataSource.addProcessedAttribute();
			dataSource.removeExistingSkeletons(false);
			
			SkeletonGenerator generator = new SkeletonGenerator(props);
		
			//break up tasks
			ExecutorService service = Executors.newFixedThreadPool(4);
			List<SkeletonJob> tasks = new ArrayList<>();
			for (int i = 0; i < NUM_JOBS; i ++) {
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
				j.getExceptions().forEach(e->e.printStackTrace());
			}
			
			for (SkeletonJob j : tasks) {
				j.getErrors().forEach(e->System.out.println(e));
			}
		}
	}

	
	public static void main(String[] args) throws Exception {
//		Path input = Paths.get("C:\\temp\\chyf\\KOTL.out.gpkg");
//		Path output = Paths.get("C:\\temp\\chyf\\KOTL.skeleton.gpkg");
		
//		Path input = Paths.get("C:\\temp\\chyf\\Richelieu.gpkg");
//		Path output = Paths.get("C:\\temp\\chyf\\Richelieu.out.gpkg");
		
//		Path input = Paths.get("C:\\temp\\chyf\\KOTL.merged.out2.gpkg");
//		Path output = Paths.get("C:\\temp\\chyf\\KOTL.test2.gpkg");

		
		Path input = Paths.get("C:\\temp\\chyf\\input2\\KOTL.out.gpkg");
		Path output = Paths.get("C:\\temp\\chyf\\input2\\KOTL.out.2.gpkg");
		
		Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
		
		long now = System.nanoTime();
		SkeletonEngine.doWork(output, ChyfProperties.getProperties());
		long then = System.nanoTime();
		
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
}
