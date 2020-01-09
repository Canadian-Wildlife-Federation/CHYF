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

//		if (args.length != 2) {
//			
//		}
//		String in = args[0];
//		String out = args[1];
//		Path input = Paths.get("C:\\temp\\chyf\\input2\\KOTL.gpkg");
//		Path output = Paths.get("C:\\temp\\chyf\\input2\\KOTL.out.gpkg");
		
//		Path input = Paths.get("C:\\temp\\chyf\\input2\\NS.gpkg");
//		Path output = Paths.get("C:\\temp\\chyf\\input2\\NS.out.gpkg");
		Path input = Paths.get("C:\\temp\\chyf\\input2\\Richelieu.gpkg");
		Path output = Paths.get("C:\\temp\\chyf\\input2\\Richelieu.out2.gpkg");
		Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
		
		ChyfProperties prop = ChyfProperties.getProperties();
		
		long now = System.nanoTime();
		PointEngine.doWork(output, prop);
		SkeletonEngine.doWork(output, prop);
		long then = System.nanoTime();
		
		System.out.println("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
	
}
