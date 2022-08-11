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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.data.FeatureReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.WaterbodyIterator;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.ConstructionPoint;

/**
 * Job for the name engine
 * @author Emily
 *
 */
public class NameJob implements Runnable {
	
	static final Logger logger = LoggerFactory.getLogger(NameJob.class.getCanonicalName());

	
	private IFlowpathDataSource dataSource;
	private WaterbodyIterator iterator;
	
	private Exception exception;
	private HashMap<FeatureId, String[]> allnames;
	
	public NameJob(IFlowpathDataSource dataSource, WaterbodyIterator iterator) {
		this.dataSource = dataSource;
		this.iterator = iterator;
		allnames = new HashMap<>();
	}
	
	/**
	 * 
	 * @return any exception that occurred while processing
	 */
	public Exception getException() {
		return this.exception;
	}
	
	/**
	 * mapping of flowpath feature id to list of names 
	 * @return
	 */
	public HashMap<FeatureId, String[]> getNamedFlowpaths(){
		return this.allnames;
	}
	
	
	@Override
	public void run() {

		try {
			SimpleFeature toProcess = null;
			while((toProcess = iterator.getNextWaterbody()) != null) {
				logger.info(toProcess.getIdentifier().toString());
				processWaterbody(toProcess);
			}
		}catch (Exception ex) {
			this.exception = ex;
		}
	}

	private void processWaterbody(SimpleFeature toProcess) throws Exception {
		Polygon workingPolygon = ChyfDataSource.getPolygon(toProcess);
		Map<ChyfAttribute, Name> nameAttributes = dataSource.findRiverNameAttributes(toProcess.getFeatureType());
		String[] wbnames = dataSource.getRiverNameIds(nameAttributes, toProcess);
		workingPolygon.setUserData(wbnames);
		
		//get skeleton lines
		ReferencedEnvelope env = new ReferencedEnvelope(workingPolygon.getEnvelopeInternal(), toProcess.getType().getCoordinateReferenceSystem());
		
		//get overlapping flowpaths
		List<LineString> flowpaths = new ArrayList<>();
		try(FeatureReader<SimpleFeatureType, SimpleFeature> flowtouches = dataSource.query(Layer.EFLOWPATHS, env, null)){
			Name efatt = ChyfDataSource.findAttribute(flowtouches.getFeatureType(), ChyfAttribute.EFTYPE);
			Name rankatt = ChyfDataSource.findAttribute(flowtouches.getFeatureType(), ChyfAttribute.RANK);
			
			while(flowtouches.hasNext()) {
				SimpleFeature t = flowtouches.next();
				if (! (((Geometry)t.getDefaultGeometry()).getEnvelopeInternal().intersects(workingPolygon.getEnvelopeInternal()))) continue; 
				EfType type = EfType.parseValue( ((Number)t.getAttribute(efatt)).intValue() );
				if (type == EfType.BANK) continue;
		
				RankType rank = RankType.parseValue(((Number)t.getAttribute(rankatt)).intValue());
				
				LineString temp = ChyfDataSource.getLineString(t);
				temp.setUserData(new Object[] {t.getIdentifier(), rank});
				if (workingPolygon.contains(temp)) {
					flowpaths.add(temp);
				}
				
			}
		}
		Object catchmentId = toProcess.getAttribute(ChyfAttribute.INTERNAL_ID.getFieldName());

		List<ConstructionPoint> namedPoints = dataSource.getConstructionsPoints(catchmentId);
					
		HashMap<FeatureId, String[]> names = SkeletonNamer.INSTANCE.nameFlowpaths(flowpaths, namedPoints, workingPolygon);
		
		if (!names.isEmpty()) allnames.putAll(names);
	}			
	
}
