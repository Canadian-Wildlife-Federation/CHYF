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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedLineString;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.Name;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.Args;
import net.refractions.chyf.ChyfProperties;
import net.refractions.chyf.ChyfProperties.Property;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfDataSource.Attribute;
import net.refractions.chyf.datasource.ChyfDataSource.DirectionType;
import net.refractions.chyf.datasource.ChyfDataSource.EfType;
import net.refractions.chyf.datasource.ChyfDataSource.IoType;
import net.refractions.chyf.datasource.ChyfDataSource.Layer;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.skeletonizer.points.ConstructionPoint.Direction;

/**
 * Engine for generating skeleton input/output points 
 * 
 * @author Emily
 *
 */
public class PointEngine {

	static final Logger logger = LoggerFactory.getLogger(PointEngine.class.getCanonicalName());

	/**
	 * Modifies the geopackage provided by the output path
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
			PointGenerator generator = new PointGenerator(getBoundary(dataSource, properties),  properties);
			
			while(true) {
				//find next feature to process
				SimpleFeature toProcess = dataSource.getNextWaterbody();
				if (toProcess == null) break; //finished processing

				logger.info("POINT GENERATOR: " + cnt);
				cnt++;

				ftouch.clear();
				ptouch.clear();
				
				Polygon workingPolygon = ChyfDataSource.getPolygon(toProcess);
				if (!workingPolygon.isValid()) throw new Exception("Polygon not a valid geometry.  Centroid: " + workingPolygon.getCentroid().toText());
				int polyid = (Integer)toProcess.getAttribute(ChyfGeoPackageDataSource.POLYID_ATTRIBUTE);
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
					Name eftypeatt = ChyfDataSource.findAttribute(flowtouches.getFeatureType(), Attribute.EFTYPE);
					Name diratt = ChyfDataSource.findAttribute(flowtouches.getFeatureType(), Attribute.DIRECTION);
					while(flowtouches.hasNext()) {
						SimpleFeature t = flowtouches.next();
						
						EfType type = EfType.parseType( (Integer)t.getAttribute(eftypeatt) );
						if (type == EfType.BANK || type == EfType.SKELETON) continue; //ignore existing skeletons
						
						
						LineString temp = ChyfDataSource.getLineString(t);
						if (workingPolygon.relate(temp, "FF*F0****")) {
							DirectionType dtype = DirectionType.parseType((Integer)t.getAttribute(diratt));
							ftouch.add(temp);
							temp.setUserData(dtype);
						}
					}
				}

				//generate points
				generator.processPolygon(workingPolygon, ptouch, ftouch);

				//update polygons as required
				dataSource.updateWaterbodyGeometries(generator.getUpdatedPolygons());
			}
						
			//write point layers
			dataSource.createConstructionsPoints(generator.getPoints());
		}

	}

	private static List<BoundaryEdge> getBoundary(ChyfGeoPackageDataSource geopkg, ChyfProperties properties) throws Exception{

		logger.info("computing boundary points");
		
		//intersect aoi with waterbodies
		List<BoundaryEdge> edges = new ArrayList<>();	
		List<LineString> common = new ArrayList<>();
		List<PreparedLineString> aoiedges = new ArrayList<>();
		List<LineString> coastlineedges = new ArrayList<>();

		try(SimpleFeatureReader reader = geopkg.query(null, geopkg.getEntry(Layer.AOI), null)){
			while(reader.hasNext()){
				SimpleFeature aoi = reader.next();
				Polygon pg = ChyfDataSource.getPolygon(aoi);
				LineString ring = pg.getExteriorRing();
				aoiedges.add(new PreparedLineString(ring));
			}
		}
		
		if (geopkg.getEntry(Layer.COASTLINE) != null){
			try(SimpleFeatureReader reader = geopkg.query(null, geopkg.getEntry(Layer.COASTLINE), null)){
				while(reader.hasNext()){
					SimpleFeature aoi = reader.next();
					LineString ring = ChyfDataSource.getLineString(aoi);
					ring.setUserData(aoi.getIdentifier());
					coastlineedges.add(ring);
				}
			}
		}


		//intersect with waterbodies
		List<Polygon> toupdate = new ArrayList<>();
		List<LineString> cstoupdate = new ArrayList<>();
		//point intersections; these are potentially in/out construction points
		List<Point> intersectionPoints = new ArrayList<>();
		try(SimpleFeatureReader wbreader = geopkg.getWaterbodies()){
			while(wbreader.hasNext()) {
				SimpleFeature ww = wbreader.next();

				Polygon wb = ChyfDataSource.getPolygon(ww);
				Polygon workingwb = null;
				
				//aoi
				for (PreparedLineString ring : aoiedges) {
					if (!ring.intersects(wb)) continue;
					
					Geometry gintersect = ring.getGeometry().intersection(wb);
					if (gintersect.isEmpty()) continue;
					
					for (int j = 0; j < gintersect.getNumGeometries(); j ++) {
						Geometry t = gintersect.getGeometryN(j);
						if (t instanceof LineString) {
							common.add((LineString)t);
						}else if (t instanceof Point) {
							intersectionPoints.add((Point)t);
						}else {
							throw new RuntimeException("The intersection of aoi and waterbodies returns invalid geometry type ("+ t.getGeometryType() + ")");
						}
					}
				}
				
				//coastline
				//we might need to make a point in the middle and 
				//add it to both the coastline geometry and the waterbody geometry
				for (LineString ring : coastlineedges) {
					LineString updatedls = null;
					if (!ring.getEnvelopeInternal().intersects(wb.getEnvelopeInternal())) continue;
					Geometry t = ring.intersection(wb);
					if (t.isEmpty()) continue;
					if (t instanceof Point) continue;
					
					List<LineString> items = new ArrayList<>();
					if (t instanceof LineString) {
						items.add((LineString)t);
					}else if (t instanceof MultiLineString) {
						for (int i = 0; i < ((MultiLineString)t).getNumGeometries(); i ++) {
							items.add((LineString)t.getGeometryN(i));
						}					
					}else {
						throw new RuntimeException("The intersection of coastline and waterbodies returns invalid geometry type ("+ t.getGeometryType() + ")");
					}		
					if (items.isEmpty()) continue;
					
					LineMerger m = new LineMerger();
					m.add(items);
					Collection<?> ints = m.getMergedLineStrings();
					for (Object x : ints) {
						if (x instanceof LineString) {
							LineString lls = (LineString)x;
							List<Coordinate> cs = new ArrayList<>();
							for (Coordinate z : lls.getCoordinates()) cs.add(z);
							PointGenerator.InsertPoint ins = PointGenerator.findMidPoint(cs, properties.getProperty(Property.PNT_VERTEX_DISTANCE), true);
							if (ins.before != null) {
								//insert this coordinate on the waterbody and the coastline edge
								//coastline
								if (updatedls == null) updatedls = ring;
								updatedls = insertCoordinate(updatedls, ins);
								//add to intersection
								lls = insertCoordinate(lls, ins);
								//update waterbody
								if (workingwb == null) {
									workingwb = wb;
									workingwb.setUserData(null);
								}
								workingwb = PointGenerator.addVertex(workingwb, Collections.singletonList(ins));
							}
							Point p = lls.getFactory().createPoint(ins.toinsert);
							BoundaryEdge be = new BoundaryEdge(Direction.OUT,  lls, p);
							edges.add(be);
						}
					}
					
					if (updatedls != null) {
						updatedls.setUserData(ring.getUserData());
						cstoupdate.add(updatedls);
					}
				}
				if (workingwb != null) {
					workingwb.setUserData(new PolygonInfo(ww.getIdentifier(), null));
					toupdate.add(workingwb);
				}
			}
		}
		
		//update coastline and waterbodies as required
		geopkg.updateWaterbodyGeometries(toupdate);
		for (LineString ls : cstoupdate) {
			geopkg.updateCoastline((FeatureId)ls.getUserData(), ls);
		}
	
		//find aoi points
		List<Point> inoutpoints = geopkg.getBoundaries();
		LineMerger m = new LineMerger();
		m.add(common);
		Collection<?> items = m.getMergedLineStrings();
		
		for (Object x : items) {
			if (x instanceof LineString) {
				//find the nearest point within 0.004 units
				LineString ls = (LineString)x;
				
				Point found = null;
				for (Coordinate c : ls.getCoordinates()) {
					for (Point p : inoutpoints) {
						if (c.equals2D(p.getCoordinate())) {
							found = p;
							break;
						}
					}
					if (found != null) break;
				}
					
				Direction d = Direction.UNKNOWN;
				if (found != null) {
					if (found.getUserData() != null) {
						IoType t  = (IoType) found.getUserData();
						if (t == IoType.INPUT) d = Direction.IN;
						if (t == IoType.OUTPUT) d = Direction.OUT;
					}
					BoundaryEdge be = new BoundaryEdge(d,  (LineString)x, found);
					edges.add(be);
				}else {
					BoundaryEdge be = new BoundaryEdge(d,  (LineString)x, null);
					edges.add(be);
					logger.warn("WARNING: The boundary edge has no in/out points: " + be.getLineString().toText());
				}
			}else {
				throw new RuntimeException("The intersection of aoi and waterbodies linestring merger does not return linestring geometry");

			}
		}
		
		for (Point p : intersectionPoints) {
			boolean found = false;
			for (Point inout : inoutpoints) {
				if (p.getCoordinate().equals2D(inout.getCoordinate())) {
					Direction d = Direction.UNKNOWN;
					if (inout.getUserData() != null) {
						IoType t  = (IoType) inout.getUserData();
						if (t == IoType.INPUT) d = Direction.IN;
						if (t == IoType.OUTPUT) d = Direction.OUT;
					}
					BoundaryEdge be = new BoundaryEdge(d, null, inout);
					edges.add(be);
					found = true;
					break;
				}
			}
			if (!found) {
				logger.warn("WARNING: The waterbody intersects the aoi at a single point, but this point is not defined as a in/out point: " + p.toText());
			}
		}
		return edges;
	}
	
	/**
	 * Creates a new linestring with the cordinate toinsert inserted between
	 * the first and second coordinate
	 * @param toinsert
	 * @param first
	 * @param second
	 * @return
	 */
	private static LineString insertCoordinate(LineString ls,PointGenerator.InsertPoint pnt){
		Coordinate[] newcs = new Coordinate[ls.getCoordinates().length + 1];
		int j = 0;
		for (int i = 0; i < ls.getCoordinates().length; i ++) {
			newcs[j++] = ls.getCoordinateN(i);
			if (ls.getCoordinateN(i).equals2D(pnt.before) &&
					i != ls.getCoordinates().length - 1 && 
					ls.getCoordinateN(i+1).equals2D(pnt.after)) {
				newcs[j++] = pnt.toinsert;
			}
		}
		return ls.getFactory().createLineString(newcs);
	}
	
	
	public static void main(String[] args) throws Exception {

		Args runtime = Args.parseArguments(args, "PointEngine");
		if (runtime == null) return;
		
		runtime.prepareOutput();
		
		long now = System.nanoTime();
		PointEngine.doWork(runtime.getOutput(), runtime.getPropertiesFile());
		long then = System.nanoTime();
		
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
}
