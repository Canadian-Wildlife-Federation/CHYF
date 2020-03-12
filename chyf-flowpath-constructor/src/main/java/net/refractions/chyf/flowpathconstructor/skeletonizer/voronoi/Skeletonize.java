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

import net.refractions.chyf.flowpathconstructor.FlowpathArgs;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.PointEngine;

/**
 * Performs both point generation and skeletonization in 
 * a single step.
 * 
 * @author Emily
 *
 */
public class Skeletonize {

	public static void main(String[] args) throws Exception {
		FlowpathArgs runtime = new FlowpathArgs("Skeletonize");
		if (!runtime.parseArguments(args)) return;
		
		
		runtime.prepareOutput();
		
		long now = System.nanoTime();
		PointEngine.doWork(runtime.getOutput(), runtime.getPropertiesFile());
		SkeletonEngine.doWork(runtime.getOutput(), runtime.getPropertiesFile(), runtime.getCores());
		long then = System.nanoTime();
		
		System.out.println("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
	
}
