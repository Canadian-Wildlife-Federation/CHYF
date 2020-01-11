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

import net.refractions.chyf.ChyfProperties;
import net.refractions.chyf.Utils;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.skeletonizer.points.PointEngine;

/**
 * Performs both point generation and skeletonization in 
 * a single step.
 * 
 * @author Emily
 *
 */
public class Skeletonize {

	public static void main(String[] args) throws Exception {
		String[] fields = Utils.parseArguments(args);
		if (fields == null) {
			Utils.printUsage("Skeletonize");
			return;
		}
		Path input = Paths.get(fields[0]);
		if (!Files.exists(input)) {
			System.err.println("Input file not found: " + input.toString());
			return;
		}
		
		Path output = Paths.get(fields[1]);
		ChyfGeoPackageDataSource.deleteOutputFile(output);
		
		Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
		
		ChyfProperties prop = null;
		if (fields[2] != null) prop = ChyfProperties.getProperties(Paths.get(fields[2]));
		
		long now = System.nanoTime();
		PointEngine.doWork(output, prop);
		SkeletonEngine.doWork(output, prop);
		long then = System.nanoTime();
		
		System.out.println("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
	
}
