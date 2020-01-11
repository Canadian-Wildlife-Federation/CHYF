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
package net.refractions.chyf.skeletonizer.bank;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfProperties;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfDataSource.Attribute;
import net.refractions.chyf.datasource.ChyfDataSource.EfType;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.skeletonizer.points.BoundaryEdge;
import net.refractions.chyf.skeletonizer.points.PolygonInfo;
import net.refractions.chyf.skeletonizer.voronoi.SkeletonJob;
import net.refractions.chyf.skeletonizer.voronoi.SkeletonResult;

public class BankEngine {

	static final Logger logger = LoggerFactory.getLogger(BankEngine.class.getCanonicalName());

	public static void doWork(Path input, Path output, ChyfProperties properties) throws Exception {
		//copy input to output
		Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);

		int cnt = 1;
		
		List<Polygon> ptouch = new ArrayList<>();
		List<LineString> ftouch = new ArrayList<>();

		try(ChyfGeoPackageDataSource dataSource = new ChyfGeoPackageDataSource(output)){

			dataSource.addProcessedAttribute();
			dataSource.removeExistingSkeletons(true);

			if (properties == null) properties = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			
			BankSkeletonizer generator = new BankSkeletonizer(properties);
			List<BoundaryEdge> be = dataSource.getBoundary();

			while(true) {
				//find next feature to process				
				SimpleFeature toProcess = dataSource.getNextWaterbody();
				if (toProcess == null) break; //finished processing
				
				logger.info("Generating Banks: " + cnt);
				cnt++;
				
				ftouch.clear();
				ptouch.clear();
				
				Polygon workingPolygon = ChyfDataSource.getPolygon(toProcess);
				
				//get overlapping polygons
				List<LineString> wateredges = new ArrayList<>();
				ReferencedEnvelope env = new ReferencedEnvelope(workingPolygon.getEnvelopeInternal(), toProcess.getType().getCoordinateReferenceSystem());
				try(SimpleFeatureReader wbtouches = dataSource.getWaterbodies(env)){
					while(wbtouches.hasNext()) {
						SimpleFeature t = wbtouches.next();
						if (t.getIdentifier().equals(toProcess.getIdentifier())) continue;
						Polygon temp = ChyfDataSource.getPolygon(t);
						Geometry g = workingPolygon.intersection(temp);
						
						if (g instanceof LineString) {
							wateredges.add((LineString)g);
						}else if (g instanceof MultiLineString) {
							MultiLineString ms = (MultiLineString)g;
							for (int i = 0; i < ms.getNumGeometries(); i ++) {
								wateredges.add((LineString) ms.getGeometryN(i));
							}
						}else {
							if (! (g instanceof Polygon && ((Polygon)g).isEmpty())) {
								System.out.println(temp.toText());
								System.out.println(workingPolygon.toText());
								throw new Exception("Intersection of waterbodies does not return LineStrings");
							}
								
						}
					}
				}
				
				//get any water boundary edges
				for (BoundaryEdge e : be) {
					if (e.getLineString().getEnvelopeInternal().intersects(workingPolygon.getEnvelopeInternal()) &&
						e.getLineString().intersects(workingPolygon)) {
							wateredges.add(e.getLineString());
					}
				}
				
				//get overlapping flowpaths
				HashMap<Coordinate, Integer> nodes = new HashMap<>();
				HashMap<Coordinate, Integer> ctype = new HashMap<>();
				try(SimpleFeatureReader flowtouches = dataSource.getFlowpaths(env)){
					while(flowtouches.hasNext()) {
						SimpleFeature t = flowtouches.next();
						if (! (((Geometry)t.getDefaultGeometry()).getEnvelopeInternal().intersects(workingPolygon.getEnvelopeInternal()))) continue; 
						EfType type = EfType.parseType( (Integer)t.getAttribute(Attribute.EFTYPE.getFieldName()) );
						if (type == EfType.BANK) continue;
						
						boolean isskel = false;
						if (type == EfType.SKELETON) { 
							LineString temp = ChyfDataSource.getLineString(t);
							if (workingPolygon.contains(temp)) {
								isskel = true;
								ftouch.add(temp);
							}
						}

						//this is observed flow
						//if the end point touches the boundary
						LineString temp = ChyfDataSource.getLineString(t);
						Coordinate c1 = temp.getCoordinates()[0];
						Coordinate c2 = temp.getCoordinates()[temp.getCoordinates().length - 1];
						
						for (Coordinate c : new Coordinate[] {c1, c2}) {
							Point pnt = temp.getFactory().createPoint(c);
							boolean onwater = false;
							for (LineString bee : wateredges) {
								for (Coordinate bc : bee.getCoordinates()) {
									if (bc.equals2D(c)) {
										onwater = true;
										break;
									}
								}
							}
							if (onwater) continue;
							
							if (workingPolygon.relate(pnt, "F**0*****")) {
								Integer cnter = nodes.get(c);
								if (cnter == null) {
									cnter = 1;
								}else {
									cnter = cnter + 1;
								}
								nodes.put(c,  cnter);
								if (isskel) {
									if (c == c1) {
										ctype.put(c, 1);
									}else {
										ctype.put(c, 2);
									}
								}
							}
						}
					}
				}

				if (ftouch.isEmpty()) {
					logger.warn("WARNING: Waterbody found without any skeletons.  Centroid: " + workingPolygon.getCentroid().toText());
					continue;
				}
				
				List<Coordinate> degree1nodes = new ArrayList<>();
				for (Entry<Coordinate,Integer> e : nodes.entrySet()) {
					if (e.getValue() == 1) degree1nodes.add(e.getKey());
				}
				Coordinate terminalnode = null;
				if (degree1nodes.size() == 1) {
					terminalnode = degree1nodes.get(0);
				}else if (degree1nodes.size() == 2) {
					if (ctype.get(degree1nodes.get(0)) != ctype.get(degree1nodes.get(1))) {
						//isolated lake 
						terminalnode = degree1nodes.get(0);
					}else {
						throw new Exception("Invalid number of headwater/terminal nodes in the lake");
					}
				}else if (degree1nodes.size() > 2) {
					throw new Exception("Invalid number of headwater/terminal nodes in the lake");
				}
				
				//generate bank skeletons
				SkeletonResult result = generator.skeletonize(workingPolygon, ftouch, terminalnode, wateredges);

				//update polygons as required
				Polygon newwb = result.getPolygon();

				newwb.setUserData(new PolygonInfo(toProcess.getIdentifier(), null));
				dataSource.updateWaterbodyGeometries(Collections.singletonList(newwb));
				dataSource.writeSkeletons(result.getSkeletons());
			}
			dataSource.writeSkeletons(Collections.emptyList());
		}

	}
	


	
	public static void main(String[] args) throws Exception {		
		if (args.length != 2) {
			System.err.println("Invalid Usage");
			System.err.println("usage: BankEngine infile outfile");
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
		BankEngine.doWork(input, output, null);
		long then = System.nanoTime();
		
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
}
