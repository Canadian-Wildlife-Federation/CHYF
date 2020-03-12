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
package net.refractions.chyf.flowpathconstructor.datasource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.filter.identity.FeatureIdImpl;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;

import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.Layer;

/**
 * Iterates over all the waterbodies inside the AOI in the provided dataset.
 * 
 * @author Emily
 *
 */
public class WaterbodyIterator {

	private static FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

	private List<String> wbToProcess;
	private FlowpathGeoPackageDataSource source;
	
	public WaterbodyIterator(FlowpathGeoPackageDataSource source) throws Exception {
		this.source = source;
		init();
	}
	
	/*
	 * Creates a list of waterbodies to process.  Only includes
	 * waterbodies that are inside the aoi.
	 *
	 */
	private void init() throws Exception{
		wbToProcess = new ArrayList<>();
		
		List<Polygon> aois = source.getAoi();
		List<PreparedPolygon> aoispp = new ArrayList<>();
		for (Polygon p : aois) {
			aoispp.add(new PreparedPolygon(p));
		}
		try(SimpleFeatureReader reader = source.getWaterbodies()){
			while(reader.hasNext()) {
				SimpleFeature fs = reader.next();
				Polygon p = ChyfDataSource.getPolygon(fs);
				for (PreparedPolygon aoi:aoispp) {
					if (aoi.intersects(p) && p.relate(aoi.getGeometry(),"2********")) {
						wbToProcess.add(fs.getID());
						break;
					}
				}
			}
		}
	}
	
	/**
	 * 
	 * @return gets the next waterbody to process
	 * 
	 * @throws IOException
	 */
	public synchronized SimpleFeature getNextWaterbody() throws IOException {
		if (wbToProcess.isEmpty()) return null;
		
		String fid = wbToProcess.get(0);
		
		Filter featureFilter = ff.id(new FeatureIdImpl(fid));
		
		SimpleFeature next = null;
		
		try(SimpleFeatureReader rr = source.getFeatureReader(Layer.ECATCHMENTS, featureFilter, null)){
			if (rr.hasNext()) next = rr.next();
		}
		if (next == null) throw new IllegalStateException("Waterbody with feature id " + fid + " not found.");
		wbToProcess.remove(fid.toString());
		return next;
	}

	
}
