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

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;

/**
 * Skeleton job that generates skeletons for waterbodies
 * in the provided dataset.
 * 
 * @author Emily
 *
 */
public class SkeletonJob implements Runnable{

	static final Logger logger = LoggerFactory.getLogger(SkeletonJob.class.getCanonicalName());

	private ChyfGeoPackageDataSource dataSource;
	private SkeletonGenerator generator;
	
	private List<Exception> exerrors;
	private List<String> skelerrors;
	
	public SkeletonJob(ChyfGeoPackageDataSource dataSource, SkeletonGenerator generator) {
		this.dataSource = dataSource;
		this.generator = generator;
	}
	
	public List<Exception> getExceptions() {
		return exerrors;
	}
	public List<String> getErrors(){
		return this.skelerrors;
	}
	
	@Override
	public void run() {
		exerrors = new ArrayList<>();
		skelerrors = new ArrayList<>();
		
		try {
			while(true) {
				//find next feature to process
				SimpleFeature toProcess = dataSource.getNextWaterbody();
				if (toProcess == null) break; //finished processing
				logger.info(toProcess.getIdentifier().toString());
				Integer polyid = (Integer) toProcess.getAttribute(ChyfGeoPackageDataSource.POLYID_ATTRIBUTE);

				try {
					Polygon workingPolygon = ChyfDataSource.getPolygon(toProcess);
					SkeletonResult result = generator.generateSkeleton(workingPolygon, dataSource.getSkeletonPoints(polyid));
					dataSource.writeSkeletons(result.getSkeletons());
					skelerrors.addAll(result.getErrors());
				}catch (Exception ex) {
					exerrors.add(new Exception("Error processing polyid: " + polyid, ex));
				}
			}
		}catch (Exception ex) {
			exerrors.add(ex);
		}
	}

}
