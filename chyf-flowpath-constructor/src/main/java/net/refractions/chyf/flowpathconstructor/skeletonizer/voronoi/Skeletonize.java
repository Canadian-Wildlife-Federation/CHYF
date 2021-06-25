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

import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.ChyfProperties;
import net.refractions.chyf.flowpathconstructor.FlowpathArgs;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathPostGisDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
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
			
		long now = System.nanoTime();
		IFlowpathDataSource dataSource = null;
		try{
			if (runtime.isGeopackage()) {
				Path input = Paths.get(runtime.getInput());
				Path output = Paths.get(runtime.getOutput());
				ChyfGeoPackageDataSource.prepareOutput(input, output);
				
				dataSource = new FlowpathGeoPackageDataSource(output);
			}else if (runtime.isPostigs()){
				
				dataSource = new FlowpathPostGisDataSource(runtime.getDbConnectionString(), runtime.getInput(), runtime.getOutput(), runtime.getAoi());
				if (runtime.getAoi() == null || runtime.getAoi().isEmpty()) {
					System.err.println("An aoi argument must be provided to use this option");
					return;
				}
			}
			ChyfProperties prop = runtime.getPropertiesFile();
			if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			PointEngine.doWork(dataSource, prop);
			SkeletonEngine.doWork(dataSource, prop, runtime.getCores());
			
		}finally {
			if (dataSource != null) dataSource.close();
		}
		long then = System.nanoTime();
		System.out.println("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	
	}
	
	
	
}
