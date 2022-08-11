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
package net.refractions.chyf.flowpathconstructor.skeletonizer.bank;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.geotools.data.FeatureReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfLogger;
import net.refractions.chyf.ExceptionWithLocation;
import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.flowpathconstructor.ChyfProperties;
import net.refractions.chyf.flowpathconstructor.FlowpathArgs;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathPostGisLocalDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.WaterbodyIterator;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.PolygonInfo;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkelLineString;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkeletonResult;

public class BankEngine {

	static final Logger logger = LoggerFactory.getLogger(BankEngine.class.getCanonicalName());

	
	public static void doWork(Path output, ChyfProperties properties) throws Exception {
		try(FlowpathGeoPackageDataSource dataSource = new FlowpathGeoPackageDataSource(output)){
			try {
				doWork(dataSource, properties);
			}catch (ExceptionWithLocation ex) {
				ChyfLogger.INSTANCE.logException(ChyfLogger.Process.SKELETON, ex);
				throw ex;
			}catch (Exception ex) {
				ChyfLogger.INSTANCE.logException(ChyfLogger.Process.SKELETON, ex);
				throw ex;
			}
		}
	}	
	
	public static void doWork(IFlowpathDataSource dataSource, ChyfProperties properties) throws Exception {

		int cnt = 1;	
		List<Polygon> ptouch = new ArrayList<>();
		List<SkelLineString> ftouch = new ArrayList<>();

		dataSource.removeExistingSkeletons(true);
			
		if (properties == null) properties = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			
		BankSkeletonizer generator = new BankSkeletonizer(properties);
		List<LineString> be = getBoundary(dataSource);
		List<SkelLineString> newSkeletons = new ArrayList<>();
			
		WaterbodyIterator iterator = new WaterbodyIterator(dataSource);
			
		SimpleFeature toProcess = null;
		while((toProcess = iterator.getNextWaterbody()) != null) {;
			logger.info("Generating Banks: " + cnt);
			cnt++;
			
			ftouch.clear();
			ptouch.clear();
			
			Polygon workingPolygon = ChyfDataSource.getPolygon(toProcess);
			
			//get overlapping polygons
			List<LineString> wateredges = new ArrayList<>();
			ReferencedEnvelope env = new ReferencedEnvelope(workingPolygon.getEnvelopeInternal(), toProcess.getType().getCoordinateReferenceSystem());
			try(FeatureReader<SimpleFeatureType, SimpleFeature> wbtouches = dataSource.query(Layer.ECATCHMENTS, env, dataSource.getWbTypeFilter())){
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
							throw new Exception("Intersection of waterbodies does not return LineStrings");
						}
							
					}
				}
			}
			
			//get any water boundary edges
			for (LineString e : be) {
				if (e.getEnvelopeInternal().intersects(workingPolygon.getEnvelopeInternal()) &&
					e.intersects(workingPolygon)) {
						wateredges.add(e);
				}
			}
			
			//get overlapping flowpaths
			HashMap<Coordinate, Integer> nodes = new HashMap<>();
			HashMap<Coordinate, Integer> ctype = new HashMap<>();
			try(FeatureReader<SimpleFeatureType, SimpleFeature> flowtouches = dataSource.query(Layer.EFLOWPATHS, env, null)){
				Name efatt = ChyfDataSource.findAttribute(flowtouches.getFeatureType(), ChyfAttribute.EFTYPE);
				while(flowtouches.hasNext()) {
					SimpleFeature t = flowtouches.next();
					if (! (((Geometry)t.getDefaultGeometry()).getEnvelopeInternal().intersects(workingPolygon.getEnvelopeInternal()))) continue; 
					EfType type = EfType.parseValue( ((Number)t.getAttribute(efatt)).intValue() );
					if (type == EfType.BANK) continue;
					
					boolean isskel = false;
					if (type == EfType.SKELETON) { 
						LineString temp = ChyfDataSource.getLineString(t);
						temp.setUserData(t.getIdentifier());
						if (workingPolygon.contains(temp)) {
							isskel = true;
							SkelLineString ls = new SkelLineString(temp, type, t);
							ftouch.add(ls);
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
			
			newSkeletons.addAll(result.getSkeletons());
		}			
		dataSource.removeExistingSkeletons(false);
		dataSource.writeSkeletons(newSkeletons);
		

	}
	
	private static List<LineString> getBoundary(IFlowpathDataSource datasource) throws Exception{

		logger.info("computing boundary edges");
		
		//intersect aoi with waterbodies
		List<LineString> edges = new ArrayList<>();	
		List<LineString> common = new ArrayList<>();
		
		List<LineString> aoiedges = new ArrayList<>();
		
		for (Polygon p : datasource.getAoi()) {	
			LineString ring = p.getExteriorRing();
			aoiedges.add(ring);
		}
		
		
		try(FeatureReader<SimpleFeatureType, SimpleFeature> reader = datasource.query(Layer.SHORELINES)){
			if (reader != null) {
				while(reader.hasNext()){
					SimpleFeature aoi = reader.next();
					LineString ring = ChyfDataSource.getLineString(aoi);
					ring.setUserData(aoi.getIdentifier());
					aoiedges.add(ring);
				}
			}
		}
		


		//intersect with waterbodies
		try(FeatureReader<SimpleFeatureType, SimpleFeature> wbreader = datasource.getWaterbodies()){
			while(wbreader.hasNext()) {
				SimpleFeature ww = wbreader.next();
				Polygon wb = ChyfDataSource.getPolygon(ww);
				
				//aoi
				for (LineString ring : aoiedges) {
					if (!ring.getEnvelopeInternal().intersects(wb.getEnvelopeInternal())) continue;
					Geometry t = ring.intersection(wb);
					if (t.isEmpty()) continue;
					if (t instanceof LineString) {
						common.add((LineString)t);
					}else if (t instanceof MultiLineString) {
						for (int i = 0; i < ((MultiLineString)t).getNumGeometries(); i ++) {
							common.add((LineString)t.getGeometryN(i));
						}
					}else if (t instanceof Point) {
						//ignore
					}else {
						throw new RuntimeException("The intersection of aoi and waterbodies returns invalid geometry type ("+ t.getGeometryType() + ")");
					}		
				}
			}
		}
		
		
		//find aoi points
		LineMerger m = new LineMerger();
		m.add(common);
		Collection<?> items = m.getMergedLineStrings();
		
		for (Object x : items) {
			if (x instanceof LineString) {
				//find the nearest point within 0.004 units
				LineString ls = (LineString)x;
				edges.add(ls);
			}else {
				throw new RuntimeException("The intersection of aoi and waterbodies linestring merger does not return linestring geometry");

			}
		}
		return edges;
	}

	
	public static void main(String[] args) throws Exception {		
		
		FlowpathArgs runtime = new FlowpathArgs("BankEngine");
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
				if (!runtime.hasAoi()) return;
				dataSource = new FlowpathPostGisLocalDataSource(runtime.getDbConnectionString(), runtime.getInput(), runtime.getOutput());
			}
			
			ChyfProperties prop = runtime.getPropertiesFile();
			if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			BankEngine.doWork(dataSource, prop);
			dataSource.finish();
		}finally {
			if (dataSource != null) dataSource.close();
		}
		long then = System.nanoTime();
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
}
