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
package net.refractions.chyf.skeletonizer.points;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfProperties;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfDataSource.Attribute;
import net.refractions.chyf.datasource.ChyfDataSource.EfType;
import net.refractions.chyf.skeletonizer.bank.BankEngine;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;

/**
 * Engine for generating skeleton input/output points 
 * 
 * @author Emily
 *
 */
public class PointEngine {

	static final Logger logger = LoggerFactory.getLogger(PointEngine.class.getCanonicalName());

	/**
	 * Modifies the geopackaged provided by the output path
	 * with skeleton in/out points (and updates waterbodies as required)
	 * 
	 * @param output
	 * @param properties
	 * @throws Exception
	 */
	public static void doWork(Path output, ChyfProperties properties) throws Exception {
		//copy input to output

		int cnt = 1;
		
		List<Polygon> ptouch = new ArrayList<>();
		List<LineString> ftouch = new ArrayList<>();

		try(ChyfGeoPackageDataSource dataSource = new ChyfGeoPackageDataSource(output)){
			dataSource.addProcessedAttribute();
			dataSource.addPolygonIdAttribute();
			if (properties == null) properties = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			PointGenerator generator = new PointGenerator(dataSource.getBoundary(),  properties);
			
			while(true) {
				//find next feature to process
				int polyid = cnt;
		
				SimpleFeature toProcess = dataSource.getNextWaterbody(polyid);
				if (toProcess == null) break; //finished processing

				logger.info("POINT GENERATOR: " + cnt);
				cnt++;
				
				ftouch.clear();
				ptouch.clear();
				
				Polygon workingPolygon = ChyfDataSource.getPolygon(toProcess);
				workingPolygon.setUserData(new PolygonInfo(toProcess.getIdentifier(), polyid));
				
				//get overlapping polygons
				ReferencedEnvelope env = new ReferencedEnvelope(workingPolygon.getEnvelopeInternal(), toProcess.getType().getCoordinateReferenceSystem());
				try(SimpleFeatureReader wbtouches = dataSource.getWaterbodies(env)){
					while(wbtouches.hasNext()) {
						SimpleFeature t = wbtouches.next();
						if (t.getIdentifier().equals(toProcess.getIdentifier())) continue;
						Polygon temp = ChyfDataSource.getPolygon(t);
						
						if (workingPolygon.intersects(temp)) {
							ptouch.add(temp);
							temp.setUserData(new PolygonInfo(t.getIdentifier(), (Integer)t.getAttribute(ChyfGeoPackageDataSource.POLYID_ATTRIBUTE)));
						}
					}
				}
				//get overlapping flowpaths							
				try(SimpleFeatureReader flowtouches = dataSource.getFlowpaths(env)){
					while(flowtouches.hasNext()) {
						SimpleFeature t = flowtouches.next();
						
						EfType type = EfType.parseType( (Integer)t.getAttribute(Attribute.EFTYPE.getFieldName()));
						if (type == EfType.BANK || type == EfType.SKELETON) continue; //ignore existing skeletons
						
						LineString temp = ChyfDataSource.getLineString(t);
						if (workingPolygon.relate(temp, "FF*F0****"))ftouch.add(temp);
					}
				}

				//generate points
				generator.processPolygon(workingPolygon, ptouch, ftouch);

				//update polygons as required
				dataSource.updateWaterbodyGeometries(generator.getUpdatedPolygons());

			}
						
			//write point layers
			dataSource.addPointLayer(generator.getPoints());
		}

	}

	
	public static void main(String[] args) throws Exception {

		if (args.length != 2) {
			System.err.println("Invalid Usage");
			System.err.println("usage: PointEngine infile outfile");
			System.err.println("   infile:  the input geopackage file");
			System.err.println("   outfile: the output geopackage file, will be overwritten");
			return;
		}

		Path input = Paths.get(args[0]);
		if (!Files.exists(input)) {
			System.err.println("Input file not found: " + input.toString());
			return;
		}
		
		Path output = Paths.get(args[1]);
		ChyfGeoPackageDataSource.deleteOutputFile(output);
		
		Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
		
		long now = System.nanoTime();
		PointEngine.doWork(output, null);
		long then = System.nanoTime();
		
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
}
