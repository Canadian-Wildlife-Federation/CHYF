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
package net.refractions.chyf.flowpathconstructor.skeletonizer.points;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedLineString;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfLogger;
import net.refractions.chyf.ExceptionWithLocation;
import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.datasource.DirectionType;
import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.FlowDirection;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.flowpathconstructor.ChyfProperties;
import net.refractions.chyf.flowpathconstructor.ChyfProperties.Property;
import net.refractions.chyf.flowpathconstructor.FlowpathArgs;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathPostGisLocalDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.TerminalNode;
import net.refractions.chyf.flowpathconstructor.datasource.WaterbodyIterator;

/**
 * Engine for generating skeleton input/output points. Attempts to apply names
 * to construction point based on names on inflow edges and waterbodies.
 * 
 * @author Emily
 *
 */
public class PointEngine {

	static final Logger logger = LoggerFactory.getLogger(PointEngine.class.getCanonicalName());

	protected IFlowpathDataSource dataSource;
	protected ChyfProperties properties;
	
	
	public static void doWork(Path output, ChyfProperties properties ) throws Exception {
		(new PointEngine(properties)).doWork(output);
	}
	
	public static void doWork(IFlowpathDataSource dataSource, ChyfProperties properties ) throws Exception {
		(new PointEngine(properties)).doWork(dataSource);
	}
	
	protected PointEngine(ChyfProperties properties) {
		this.properties = properties;
	}
	
	/**
	 * Modifies the geopackage provided by the output path
	 * with skeleton in/out points (and updates waterbodies as required)
	 * 
	 * @param output
	 * @param properties
	 * @throws Exception
	 */
	public void doWork(Path output) throws Exception {
		try(FlowpathGeoPackageDataSource dataSource = new FlowpathGeoPackageDataSource(output)){
			try {
				doWork(dataSource);
			}catch (ExceptionWithLocation ex) {
				ChyfLogger.INSTANCE.logException(ChyfLogger.Process.CONSTRUCTION_POINT, ex);
				throw ex;
			}catch (Exception ex) {
				ChyfLogger.INSTANCE.logException(ChyfLogger.Process.CONSTRUCTION_POINT, ex);
				throw ex;
			}
		}

	}

	public void doWork(IFlowpathDataSource dataSource) throws Exception{
		this.dataSource = dataSource;
		if (properties == null) properties = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
		
		int cnt = 1;
		
		List<Polygon> ptouch = new ArrayList<>();
		List<LineString> fpTouches = new ArrayList<>();
		
		PointGenerator generator = new PointGenerator(getBoundary(),  properties);
		
		Name idAttribute = ChyfDataSource.findAttribute(dataSource.getFeatureType(Layer.ECATCHMENTS), ChyfAttribute.INTERNAL_ID);
		
		WaterbodyIterator iterator = new WaterbodyIterator(dataSource);
		SimpleFeature toProcess = null;
		while((toProcess = iterator.getNextWaterbody()) != null) {

			logger.info("POINT GENERATOR: " + cnt);
			cnt++;
			
			fpTouches.clear();
			ptouch.clear();
				
			Polygon workingPolygon = ChyfDataSource.getPolygon(toProcess);
			if (!workingPolygon.isValid()) throw new Exception("Polygon not a valid geometry.  Centroid: " + workingPolygon.getCentroid().toText());
			
			workingPolygon.setUserData(new PolygonInfo(toProcess.getIdentifier(), toProcess.getAttribute(idAttribute)));
			
			//get overlapping polygons
			ReferencedEnvelope env = new ReferencedEnvelope(workingPolygon.getEnvelopeInternal(), toProcess.getType().getCoordinateReferenceSystem());
			try(FeatureReader<SimpleFeatureType, SimpleFeature> wbtouches = dataSource.query(Layer.ECATCHMENTS, env, dataSource.getWbTypeFilter())){
				while(wbtouches.hasNext()) {
					SimpleFeature t = wbtouches.next();
					
					if (t.getIdentifier().equals(toProcess.getIdentifier())) continue;
					Polygon temp = ChyfDataSource.getPolygon(t);
					
					if (workingPolygon.intersects(temp)) {
						ptouch.add(temp);
						temp.setUserData(new PolygonInfo(t.getIdentifier(), t.getAttribute(idAttribute)));
					}
				}
			}
			//get overlapping flowpaths							
			HashMap<Coordinate, List<String[]>> skeletonnameids = new HashMap<>();
			
			
			try(FeatureReader<SimpleFeatureType, SimpleFeature> flowtouches = dataSource.query(Layer.EFLOWPATHS, env)){
				Name eftypeatt = ChyfDataSource.findAttribute(flowtouches.getFeatureType(), ChyfAttribute.EFTYPE);
				Name diratt = ChyfDataSource.findAttribute(flowtouches.getFeatureType(), ChyfAttribute.DIRECTION);
				
				Map<ChyfAttribute, Name> nameAttributes = dataSource.findRiverNameAttributes(flowtouches.getFeatureType());
				
				while(flowtouches.hasNext()) {
					SimpleFeature t = flowtouches.next();
					
					EfType type = EfType.parseValue( ((Number)t.getAttribute(eftypeatt)).intValue() );
					if (type == EfType.BANK) continue; //skip banks
					if (type == EfType.SKELETON) {
						//find any names and add these to a named point layers
						String[] nameids = dataSource.getRiverNameIds(nameAttributes, t);
						if (nameids != null ) {
							LineString temp = ChyfDataSource.getLineString(t);
							if (temp.relate(workingPolygon, "****0****")) {
								List<Geometry> boundaryIntersections = new ArrayList<>();
								boundaryIntersections.add(workingPolygon.getExteriorRing().intersection(temp));
								for (int i = 0; i < workingPolygon.getNumInteriorRing(); i ++) {
									boundaryIntersections.add(workingPolygon.getInteriorRingN(i).intersection(temp));	
								}
								for (Geometry g : boundaryIntersections) {
									for (int i = 0 ; i < g.getNumGeometries(); i ++) {
										Geometry gn = g.getGeometryN(i);
										if (gn.isEmpty()) continue;
										if (gn instanceof Point) {
											Coordinate ct = ((Point)gn).getCoordinate();
											List<String[]> namelist = skeletonnameids.get(ct);
											
											if (namelist == null) {
												namelist = new ArrayList<>();
												skeletonnameids.put(ct, namelist);
											}
											namelist.add(nameids);
										}else {
											throw new Exception("Invalid data at: " +gn.toText());
										}
									}
								}
								
								
							}
						}
						continue;
					}else {
						LineString temp = ChyfDataSource.getLineString(t);
						if (workingPolygon.relate(temp, "FF*F0****")) {
							DirectionType dtype = DirectionType.parseValue( ((Number)t.getAttribute(diratt)).intValue());
							String[] nameids = dataSource.getRiverNameIds(nameAttributes, t);
							temp.setUserData(new Object[] {dtype, nameids});						
							fpTouches.add(temp);
						}
					}
				}
			}

			//generate points
			generator.processPolygon(workingPolygon, ptouch, fpTouches, skeletonnameids);

			//update polygons as required
			dataSource.updateWaterbodyGeometries(generator.getUpdatedPolygons());
		}
					
		//write point layers
		dataSource.createConstructionsPoints(generator.getPoints());
	}
	

	protected List<BoundaryEdge> getBoundary() throws Exception{

		logger.info("computing boundary points");
		
		//intersect aoi with waterbodies
		List<BoundaryEdge> edges = new ArrayList<>();	
		List<LineString> common = new ArrayList<>();
		List<PreparedLineString> aoiedges = new ArrayList<>();
		List<LineString> coastlineedges = new ArrayList<>();

		for (Polygon p : dataSource.getAoi()) {
			LineString ring = p.getExteriorRing();
			aoiedges.add(new PreparedLineString(ring));
		}
		
		try(FeatureReader<SimpleFeatureType, SimpleFeature> reader = dataSource.query(Layer.SHORELINES, null, null)){
			if (reader != null) {
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
		try(FeatureReader<SimpleFeatureType, SimpleFeature> wbreader = dataSource.getWaterbodies()){
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
					}else if (t instanceof MultiPoint) {
						//we don't care - touches two points of waterbody 
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
							//make a temporary terminal node
							TerminalNode temp = new TerminalNode(p, FlowDirection.OUTPUT);
							findSkeletonNames(temp, lls, ww.getAttribute(ChyfAttribute.INTERNAL_ID.getFieldName()));
							BoundaryEdge be = new BoundaryEdge(lls, temp);
							edges.add(be);
						}
					}
					
					if (updatedls != null) {
						updatedls.setUserData(ring.getUserData());
						cstoupdate.add(updatedls);
					}
				}
				if (workingwb != null) {
					Object id = ww.getAttribute(ChyfAttribute.INTERNAL_ID.getFieldName());
					workingwb.setUserData(new PolygonInfo(ww.getIdentifier(), id));
					toupdate.add(workingwb);
				}
			}
		}
		
		//update coastline and waterbodies as required
		dataSource.updateWaterbodyGeometries(toupdate);
		try(DefaultTransaction tx = new DefaultTransaction()){
			try {
				for (LineString ls : cstoupdate) {
					dataSource.updateCoastline((FeatureId)ls.getUserData(), ls, tx);
				}
				tx.commit();
			}catch (Exception ex) {
				tx.rollback();
				throw ex;
			}
		}
	
		//find aoi points
		List<TerminalNode> inoutpoints = dataSource.getTerminalNodes();
		LineMerger m = new LineMerger();
		m.add(common);
		Collection<?> items = m.getMergedLineStrings();
		
		for (Object x : items) {
			if (x instanceof LineString) {
				//find the nearest point within 0.004 units
				LineString ls = (LineString)x;

				List<TerminalNode> found = new ArrayList<>();
				for (Coordinate c : ls.getCoordinates()) {
					for (TerminalNode p : inoutpoints) {
						if (c.equals2D(p.getPoint().getCoordinate())) {
							found.add(p);
						}
					}
				}
					
				if (!found.isEmpty()) {
					for (TerminalNode f : found) {
						BoundaryEdge be = new BoundaryEdge((LineString)x, f);
						edges.add(be);
					}
				}else {
					BoundaryEdge be = new BoundaryEdge((LineString)x, null);
					edges.add(be);
					dataSource.logWarning("The boundary edge has no in/out points", be.getLineString(), ChyfLogger.Process.CONSTRUCTION_POINT.name());
					logger.warn("WARNING: The boundary edge has no in/out points: " + be.getLineString().toText());
				}
			}else {
				throw new RuntimeException("The intersection of aoi and waterbodies linestring merger does not return linestring geometry");

			}
		}
		
		for (Point p : intersectionPoints) {
			boolean found = false;
			for (TerminalNode inout : inoutpoints) {
				if (p.getCoordinate().equals2D(inout.getPoint().getCoordinate())) {
					BoundaryEdge be = new BoundaryEdge(null, inout);
					edges.add(be);
					found = true;
					break;
				}
			}
			if (!found) {
				dataSource.logWarning("WARNING: The waterbody intersects the aoi at a single point, but this point is not defined as a in/out point.", p, ChyfLogger.Process.CONSTRUCTION_POINT.name());
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
	private LineString insertCoordinate(LineString ls,PointGenerator.InsertPoint pnt){
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
	
	
	
	private void findSkeletonNames(TerminalNode toUpdate, LineString commonEdge, Object catchmentInternalId) throws IOException {

		ReferencedEnvelope bounds = new ReferencedEnvelope(toUpdate.getPoint().getEnvelopeInternal(), dataSource.getCoordinateReferenceSystem());
		if (commonEdge != null) {
			bounds.expandToInclude(commonEdge.getEnvelopeInternal());
		}
		
		
		Filter filter1 = ChyfDataSource.ff.equals(
				ChyfDataSource.ff.property(ChyfAttribute.EFTYPE.getFieldName()), 
				ChyfDataSource.ff.literal(EfType.SKELETON.getChyfValue()));
		
		try(FeatureReader<SimpleFeatureType, SimpleFeature> reader = dataSource.query(Layer.EFLOWPATHS, bounds, filter1)){
			SimpleFeature closest = null;
			double distance = Double.MAX_VALUE;
			
			Map<ChyfAttribute, Name> nameAttributes = ((IFlowpathDataSource)dataSource).findRiverNameAttributes(reader.getFeatureType());
			while(reader.hasNext()) {
        		SimpleFeature sf = reader.next();
        		
        		LineString ls = ChyfDataSource.getLineString(sf);
        		
        		if (ls.intersects(toUpdate.getPoint())) {
        			closest = sf;
        			break;
        		}else if (ls.intersects(commonEdge)) {
        			if (closest == null) {
        				closest = sf;
        			}else {
        				double x = ls.distance(toUpdate.getPoint());
        				if (x < distance ) {
        					closest = sf;
        				}
        			}
        		}
        	}
			
			if (closest != null) {
				//find and set names
				String[] names = ((IFlowpathDataSource)dataSource).getRiverNameIds(nameAttributes, closest);
				toUpdate.setNames(names);
			}
        }
	}
	
	
	public static void main(String[] args) throws Exception {

		FlowpathArgs runtime = new FlowpathArgs("PointEngine");
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
				((FlowpathPostGisLocalDataSource)dataSource).setAoi(runtime.getAoi());	
			}
			ChyfProperties prop = runtime.getPropertiesFile();
			if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			
			PointEngine.doWork(dataSource, prop);
			dataSource.finish();
		}finally {
			if (dataSource != null) dataSource.close();
		}
		long then = System.nanoTime();
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	
	}

}
