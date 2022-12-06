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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.refractions.chyf.ExceptionWithLocation;
import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.WaterbodyIterator;

/**
 * Skeleton job that generates skeletons for waterbodies
 * in the provided dataset.
 * 
 * @author Emily
 *
 */
public class SkeletonJob implements Runnable{

	static final Logger logger = LoggerFactory.getLogger(SkeletonJob.class.getCanonicalName());

	private IFlowpathDataSource dataSource;
	private SkeletonGenerator generator;
	private WaterbodyIterator iterator;
	
	private Map<String,String> mdccontext;
	private List<ExceptionWithLocation> exerrors;
	private List<SkeletonResult.Error> skelerrors;
	
	public SkeletonJob(IFlowpathDataSource dataSource, WaterbodyIterator iterator, 
			SkeletonGenerator generator, Map<String,String> mdccontext) {
		this.dataSource = dataSource;
		this.generator = generator;
		this.iterator = iterator;
		this.mdccontext = mdccontext;
	}
	
	public List<ExceptionWithLocation> getExceptions() {
		return exerrors;
	}
	public List<SkeletonResult.Error> getErrors(){
		return this.skelerrors;
	}
	
	@Override
	public void run() {
		MDC.setContextMap(mdccontext);
		exerrors = new ArrayList<>();
		skelerrors = new ArrayList<>();
		
		try {
			SimpleFeature toProcess = null;
			while((toProcess = iterator.getNextWaterbody()) != null) {
				logger.info(toProcess.getIdentifier().toString());
				
				Object catchmentId = toProcess.getAttribute(ChyfAttribute.INTERNAL_ID.getFieldName());
				Polygon workingPolygon = null;
				try {
					workingPolygon = ChyfDataSource.getPolygon(toProcess);
					SkeletonResult result = generator.generateSkeleton(workingPolygon, dataSource.getConstructionsPoints(catchmentId));
					dataSource.writeSkeletons(result.getSkeletons());
					skelerrors.addAll(result.getErrors());
				}catch (Exception ex) {
					exerrors.add(new ExceptionWithLocation("Error processing catchment with identifier: " + catchmentId, ex, workingPolygon));
				}
			}
		}catch (Exception ex) {
			exerrors.add(new ExceptionWithLocation(ex.getMessage(), ex, null));
		}
	}

}
