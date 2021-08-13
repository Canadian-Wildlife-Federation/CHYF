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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.FlowDirection;
import net.refractions.chyf.flowpathconstructor.ChyfProperties;
import net.refractions.chyf.flowpathconstructor.ChyfProperties.Property;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource.NodeType;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.ConstructionPoint;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkelLineString;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkeletonGenerator;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkeletonGraph;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkeletonResult;

/**
 * Generates bank skeletons from waterbodies
 * and existing skeleton lines
 * 
 * @author Emily
 *
 */
public class BankSkeletonizer {
	
	private static final Logger logger = LoggerFactory.getLogger(BankSkeletonizer.class.getCanonicalName());

	private static double DISTANCE_ZERO_TOLERANCE = 0.000000001;
	
	private double bankNodeDistanceFactor = 0.2;
	private ChyfProperties properties;
	private GeometryFactory gf;
	
	public BankSkeletonizer(ChyfProperties properties) {
		this.properties = properties;
		bankNodeDistanceFactor = properties.getProperty(ChyfProperties.Property.BANK_NODE_DISTANCE_OFFSET);
	}
	

	/**
	 * 
	 * @param waterbody the waterbody to add bank skeletons to
	 * @param existingSkeletons set of existing skeletons linking all inflows to outflows
	 * @param terminalNode degree 1 input or output node if it exists.  This is for isolated and headwater (or tailwater)
	 * lakes where we only want one bank edge.
	 * @param wateredges set of linestrings that represent shared boundaries with other waterbodies. We don't
	 * want to create bank edges that connect to these
	 * 
	 * @return
	 * @throws Exception
	 */
	public SkeletonResult skeletonize(Polygon waterbody, Collection<SkelLineString> existingSkeletons,
			Coordinate terminalNode, List<LineString> wateredges) throws Exception {
		gf = waterbody.getFactory();
		
		
		List<SkelLineString> newSkeletonBankEdges = new ArrayList<>();
		SkeletonGenerator gen = new SkeletonGenerator(properties);
				
		List<WorkingPolygon> toprocess = new ArrayList<>();
		toprocess.addAll(breakPolgyon(waterbody, existingSkeletons, wateredges));

		while(!toprocess.isEmpty()) {
			
			//process each working polygon
			WorkingPolygon p = toprocess.remove(0);
			
			//points for skeletonizing
			List<ConstructionPoint> points = new ArrayList<>();
			List<ConstructionPoint> breakpoints = new ArrayList<>();

			if (p.getBankSkeletonEdges().isEmpty()) {
				//first time seeing this polygon; processing the boundary edges of the polygon
				//merge the skeleton lines together into a single line
				LineString skl = p.getMergedSkels();
				double minD = skl.getLength() * bankNodeDistanceFactor;
				double maxD = skl.getLength() * (1-bankNodeDistanceFactor);
				LineString tpart = chopLineString(skl.getCoordinates(), minD, maxD, gf);
				
				if (!p.getBoundaryEdges().isEmpty()) {
					List<LineString> newboundaries = new ArrayList<>();
					for (LineString ls : p.getBoundaryEdges()) {
						LineString part = removeWaterPart(ls, wateredges);
						
						double minDistance = part.getLength() * bankNodeDistanceFactor;
						double maxDistance = part.getLength() * (1-bankNodeDistanceFactor);
						part = chopLineString(part.getCoordinates(), minDistance, maxDistance, gf);
						
						//find the closest point between the polygon boundary edge
						//and the skeleton ediges
						Coordinate[] nearest = findClosest(part, tpart);
	
						//add verticies to edges if necessary
						LineString newb = addVertex(ls, nearest[0]);
						newboundaries.add(newb);
						
						List<LineString> temp = new ArrayList<>();
						for (LineString sls : p.getSkeletonEdges()) {
							LineString ils = addVertex(sls, nearest[1]);
							temp.add(ils);
						}
						p.getSkeletonEdges().clear();
						p.getSkeletonEdges().addAll(temp);
						
						//skeletonizer points
						points.add(new ConstructionPoint(nearest[0], NodeType.BANK, FlowDirection.INPUT, null));
						points.add(new ConstructionPoint(nearest[1], NodeType.BANK, FlowDirection.OUTPUT, null));
					}
					p.getBoundaryEdges().clear();
					p.getBoundaryEdges().addAll(newboundaries);
				}else if (!p.getInnerEdges().isEmpty()) {
					LineString part = removeWaterPart(p.getInnerEdges().remove(0), wateredges);
					double minDistance = part.getLength() * bankNodeDistanceFactor;
					double maxDistance = part.getLength() * (1-bankNodeDistanceFactor);
					part = chopLineString(part.getCoordinates(), minDistance, maxDistance,gf);
					
					//find the closest point between the polygon boundary edge
					//and the skeleton edges
					Coordinate[] nearest = findClosest(part, tpart);

					//add verticies to edges if necessary
					LineString newb = addVertex(part, nearest[0]);
					List<LineString> temp = new ArrayList<>();
					for (LineString sls : p.getSkeletonEdges()) {
						LineString ils = addVertex(sls, nearest[1]);
						temp.add(ils);
					}
					p.getSkeletonEdges().clear();
					p.getSkeletonEdges().addAll(temp);
					
					//update working polygon with added verticies
//					p.getInnerEdges().remove(part);
					p.getInnerEdges().add(newb);
					
					//skeletonizer points
					points.add(new ConstructionPoint(nearest[0], NodeType.BANK, FlowDirection.INPUT, null));
					points.add(new ConstructionPoint(nearest[1], NodeType.BANK, FlowDirection.OUTPUT, null));
				}
			}else {
				//this is the second time we are processing this polygon;
				//thus we need to process the island
				//pick one of the holes and process that
				LineString part = removeWaterPart(p.getInnerEdges().get(0), wateredges);
				LineString skl = p.getMergedSkels();
				LineString tpart = skl;
				Coordinate[] nearest = findClosest(part, tpart);

				//add verticies to edges if necessary
				LineString newb = addVertex(part, nearest[0]);
				
				List<LineString> temp = new ArrayList<>();
				for (LineString sls : p.getSkeletonEdges()) {
					LineString ils = addVertex(sls, nearest[1]);
					temp.add(ils);
				}
				p.getSkeletonEdges().clear();
				p.getSkeletonEdges().addAll(temp);
				
				points.add(new ConstructionPoint(nearest[0], NodeType.BANK, FlowDirection.INPUT, null));
				points.add(new ConstructionPoint(nearest[1], NodeType.BANK, FlowDirection.OUTPUT, null));
				
				p.getInnerEdges().remove(part);
				p.getInnerEdges().add(newb);
				
				//do again for boundary of polygon to be able to create a break across 
				LineString bnk = removeWaterPart(p.getBoundaryEdges().get(0), wateredges);
				nearest = findClosest(part, bnk);
				//add verticies to edges if necessary
				newb = addVertex(part, nearest[0]);
				LineString newb2 = addVertex(bnk, nearest[1]);
				
				//this is the break on the otherside of the island
				breakpoints.add(new ConstructionPoint(nearest[1], NodeType.BANK, FlowDirection.INPUT, null));
				breakpoints.add(new ConstructionPoint(nearest[0], NodeType.BANK, FlowDirection.INPUT, null));
				
				p.getInnerEdges().remove(part);
				p.getInnerEdges().add(newb);
				p.getBoundaryEdges().remove(bnk);
				p.getBoundaryEdges().add(newb2);
			} 
			
			List<LineString> newskels = new ArrayList<>();
			if (!points.isEmpty()) {
				if (points.size() < 2) throw new IllegalStateException("Number of input points to skeletonizer is < 2.");
				
				//regenerate polygon; this will add new verticies as required
				p.regeneratePolygon();
				newskels.add(generateSkeleton(points, p, gen));
				for (LineString ll : newskels) newSkeletonBankEdges.add(new SkelLineString(ll, EfType.BANK));
			}
			
			
			if (!breakpoints.isEmpty()) {
				//we need to break across the otherside of the polygon
				newskels.add(generateSkeleton(breakpoints, p, gen));
			}
			//if this polygon has an interior ring we need to repeat for the ring
			//process the closest ring to the skeleton line
			if (p.getPolygon().getNumInteriorRing() == 0) {
				//we are finished
			}else if (p.getInnerEdges().isEmpty()) {
				//hole is made by skeletons we don't need to process anything
			}else {
				
				List<WorkingPolygon> polys = breakPolgyon(p, newskels);
				for (WorkingPolygon wp : polys) {
					if (wp.getPolygon().getNumInteriorRing() == 0) continue;
					if (wp.getInnerEdges().isEmpty()) continue; //hole is made by skeletons do not process
					LineString exterior = wp.getPolygon().getExteriorRing();
					
					for (int i = 0; i < wp.getPolygon().getNumInteriorRing(); i ++) {
						LineString inner = wp.getPolygon().getInteriorRingN(i);
						if (!inner.intersects(exterior)) {
							toprocess.add(wp);
							break;
						}
					}
					
				}
			}
		}

		//Make a new waterbody polygon with vertices added for
		//the new bank skeleton edges
		waterbody = generatePolygon(waterbody,  newSkeletonBankEdges);
				
		if (terminalNode != null) {
			//throw away one of the bank edges that touches either side 
			//of the input 
			int index = -1;
			
			Coordinate[] cs = waterbody.getExteriorRing().getCoordinates();
			for (int i = 0; i < cs.length; i ++) {
				if (terminalNode.equals2D(cs[i])) {
					index = i; break;
				}
			}
			//start at index look for first bank edge that touches segment 
			if (index == -1) throw new IllegalStateException("terminal node provided not found in vertex list: "  + waterbody.toText());
			
			int i = index+1;
			if (i == cs.length - 1) i = 1;
			SkelLineString toremove = null;
			Set<Coordinate> stopPoints = new HashSet<>();
			for (SkelLineString ls : newSkeletonBankEdges) stopPoints.add(ls.getLineString().getCoordinateN(0));
			
			while(true) {
				for (SkelLineString ls : newSkeletonBankEdges) {
					if (ls.getLineString().getCoordinateN(0).equals2D(cs[i])){
						toremove = ls;
						break;
					}
				}
				if (toremove != null) break;
				if (i == index) break;
				i++;
				if (i == cs.length - 1) i = 1;
			}
			if (toremove == null) {
				throw new IllegalStateException("Unable to find bank edge to remove for headwater lake: " + waterbody.toText());
			} else {
				newSkeletonBankEdges.remove(toremove);
			}
		}
		

		//add verticies to existing skeletons
		HashSet<Coordinate> breakpoints = new HashSet<Coordinate>();
		newSkeletonBankEdges.forEach(e->{
			breakpoints.add(e.getLineString().getCoordinateN(0));
			breakpoints.add(e.getLineString().getCoordinateN(e.getLineString().getCoordinates().length-1));
		});
		
		Set<SkelLineString> skels = new HashSet<>();
		for (SkelLineString ls : existingSkeletons) {
			LineString working = ls.getLineString();
			
			for (Coordinate c : breakpoints) {
				if (ls.getLineString().getEnvelopeInternal().intersects(c)) {
					working = addVertex(working, c);
				}
			}
			
			ls.setLineString(working);
			skels.add(ls);
		}
		
		//mark requested we don't do this
		//merge all existing skeletons incase we introduce new degree2 nodes 
//		SkeletonGraph g = SkeletonGraph.buildGraphLines(skels, Collections.emptyList());
//		g.mergedegree2();
//		skels = g.getSkeletons(null, gf);
		
		//need to break the updatedSkeletons at the new SkeletonBankEdge
			List<Coordinate> items = new ArrayList<>();
		List<SkelLineString> brokenSkels = new ArrayList<>();
		
		for (SkelLineString sls : skels) {
			LineString ls = sls.getLineString();
			items.clear();
			items.add(ls.getCoordinateN(0));
				
			for (int i = 1; i < ls.getCoordinates().length - 1; i ++) {
				if (breakpoints.contains(ls.getCoordinateN(i))) {
					//break into two lines
					items.add(ls.getCoordinateN(i));
					
					LineString newls = gf.createLineString(items.toArray(new Coordinate[items.size()])); 
					brokenSkels.add(new SkelLineString(newls, EfType.SKELETON, sls.getFeature()));
					items.clear();
				}
				items.add(ls.getCoordinateN(i));
			}
			items.add(ls.getCoordinateN(ls.getCoordinates().length - 1));
			if (items.size() >= 2) {
				LineString newls = gf.createLineString(items.toArray(new Coordinate[items.size()])); 
				brokenSkels.add(new SkelLineString(newls, EfType.SKELETON, sls.getFeature()));
			}
		}
		
		//set type and combine			
		newSkeletonBankEdges.addAll(brokenSkels);
		
		SkeletonResult result = new SkeletonResult(newSkeletonBankEdges, Collections.emptyList(), waterbody);
		return result;
	}

	/**
	 * Create a new polygon adding verticies at the locations
	 * where the new bank edges touch the boundary of the polygon
	 * 
	 * @param waterbody
	 * @param newBankEdges
	 */
	private Polygon generatePolygon(Polygon waterbody, List<SkelLineString> newBankEdges) {
		LineString ls = waterbody.getExteriorRing();
		for (SkelLineString b : newBankEdges) {
			ls = addVertex(ls, b.getLineString().getCoordinateN(0));
		}
		
		ArrayList<LinearRing> ints = new ArrayList<>();
		for (int i = 0; i < waterbody.getNumInteriorRing(); i ++) {
			LineString ils = waterbody.getInteriorRingN(i);
			for (SkelLineString b : newBankEdges) {
				ils = addVertex(ils, b.getLineString().getCoordinateN(0));	
			}
			ints.add(gf.createLinearRing(ils.getCoordinates()));
		}
		Polygon newWb= gf.createPolygon(gf.createLinearRing(ls.getCoordinates()),  ints.toArray(new LinearRing[ints.size()]));
		return newWb;
	}
	//return a linestring with the wateredges components removed.
	//these wateredges should always be an the beginning or edge
	//if not we have an error of some sort
	private LineString removeWaterPart(LineString ls, List<LineString> wateredges) {
		if (wateredges.isEmpty()) return ls;		
		Geometry g = ls;
		for(LineString l : wateredges) {
			g = g.difference(l);
		}
		if (!(g instanceof LineString)) {
			//TODO:
			System.out.println("ERROR");
			System.out.println(g.toText());
			//return (LineString)(((MultiLineString)g).getGeometryN(0));
			throw new RuntimeException("There is a problem with the existing skeletons and waterbodies near linestring " + ls.toText());
		}
		return (LineString)g;
	}
	
	
	private LineString generateSkeleton(List<ConstructionPoint> points, WorkingPolygon p, SkeletonGenerator gen) throws Exception {
		if (points.size() == 2) {
			LineString ls = gf.createLineString(new Coordinate[] {points.get(0).getCoordinate(), points.get(1).getCoordinate()});
			//good; use the straight line
			if (p.getPolygon().covers(ls))  return ls;
		}

		//we need to use the skeletonizer as we need to navigate around curves or islands
		SkeletonResult skels = gen.generateSkeleton(p.getPolygon(), points);
		if (!skels.getErrors().isEmpty()) {			
			skels.getErrors().forEach(e->{
				logger.warn(e.getMessage() + " " + e.getGeometry().toText());	
			});
			throw new IllegalStateException("Skeletonizer threw errors");
		}
		//build a graph from the skeletons and keep the shortest path between the points
		SkeletonGraph graph = SkeletonGraph.buildGraphLines(skels.getSkeletons(), points);
		LineString skelline = graph.shortestPath(gf);
		return skelline;
	}
	
	
	/**
	 * Inserts the vertex in the linestring doing nothing if the
	 * vertex already exists
	 * 
	 * @param ls
	 * @param c
	 * @return
	 */
	private LineString addVertex(LineString ls, Coordinate c) {
		ArrayList<Coordinate> newcs = new ArrayList<>();
		newcs.add(ls.getCoordinateN(0));
		for (int i = 1 ; i <ls.getCoordinates().length; i ++) {
			if (ls.getCoordinateN(i).equals2D(c)) return ls;
			LineSegment seg = new LineSegment(ls.getCoordinateN(i-1), ls.getCoordinateN(i));
			if (seg.distance(c) < DISTANCE_ZERO_TOLERANCE) {
				newcs.add(c);
			}
			newcs.add(ls.getCoordinateN(i));
		}
		return gf.createLineString(newcs.toArray(new Coordinate[newcs.size()]));
	}
	
	
	/**
	 * Breaks the polygon into component parts using the existing skeleton lines.
	 * Returns working polygons with the edges that are identified as bank or skeleton.
	 * 
	 * @param polygon
	 * @param existingSkeletons
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private List<WorkingPolygon> breakPolgyon(Polygon polygon, Collection<SkelLineString> existingSkeletons,
			List<LineString> wateredges) {
		
		LineString outer = polygon.getExteriorRing();
		
		Collection<LineString> inner = new ArrayList<>();
		for (int i = 0; i < polygon.getNumInteriorRing(); i ++) {
			inner.add(polygon.getInteriorRingN(i));
		}
		
		Set<Coordinate> stopPoints = new HashSet<>();
		for (SkelLineString ls : existingSkeletons) {
			if (polygon.getExteriorRing().intersects(ls.getLineString())) {
				stopPoints.add(ls.getLineString().getCoordinateN(0));
				stopPoints.add(ls.getLineString().getCoordinateN(ls.getLineString().getCoordinates().length - 1));
			}
		}

		//break the outer linestring at the existing skeleton points
		int start = -1;
		Coordinate[] outc = outer.getCoordinates();
		for (int i = 0; i < outc.length; i ++) {
			if (stopPoints.contains(outc[i])) {
				start = i;
				break;
			}
		}
		
		//walk around polygon breaking into LineStrings at stop points
		ArrayList<LineString> parts = new ArrayList<>();
		ArrayList<Coordinate> coords = new ArrayList<>();
		coords.add(outc[start]);
		int cnt = start+1;
		while(cnt != start) {
			coords.add(outc[cnt]);
			if (stopPoints.contains(outc[cnt])) {
				LineString ls = gf.createLineString(coords.toArray(new Coordinate[coords.size()]));
				parts.add(ls);
				coords.clear();
				coords.add(outc[cnt]);
			}
			cnt ++;
			if (cnt == outc.length) cnt = 0;
		}
		if (coords.size() > 1) { 
			coords.add(outc[start]);
			LineString ls = gf.createLineString(coords.toArray(new Coordinate[coords.size()]));
			parts.add(ls);
		}
		
		//polygonize noded edges
		Polygonizer pgen = new Polygonizer();
		pgen.add(parts);
		pgen.add(inner);
		for (SkelLineString l : existingSkeletons) pgen.add(l.getLineString());
		
		Collection<Polygon> items = pgen.getPolygons();
		
		List<WorkingPolygon> working = new ArrayList<>();
		for (Polygon p : items) {
			WorkingPolygon wp = new WorkingPolygon (p);
			for (LineString ls : parts) {
				if (ls.relate(p, "F1*F*****")) wp.getBoundaryEdges().add(ls);
			}
			for (LineString ls : inner) {
				if (ls.relate(p, "F1*F*****")) wp.getInnerEdges().add(ls);
			}
			for (SkelLineString ls : existingSkeletons) {
				if (ls.getLineString().relate(p, "F1*F*****")) wp.getSkeletonEdges().add(ls.getLineString());
			}
			if (!wp.getSkeletonEdges().isEmpty()) {
				working.add(wp);
			}
		}
		
		return working;
	}
	
	/**
	 * Breaks a working polygon 
	 * @param tobreak the polygon to break
	 * @param bankSkels the additional bank skeleton lines to use when breaking polygon
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private List<WorkingPolygon> breakPolgyon(WorkingPolygon tobreak, Collection<LineString> bankSkels) {
		
		HashSet<Coordinate> breakpoints = new HashSet<>();
		for (LineString ls : bankSkels) {
			breakpoints.add(ls.getCoordinateN(0));
			breakpoints.add(ls.getCoordinateN(ls.getCoordinates().length - 1 ));
		}
		
		List<LineString> bnds = new ArrayList<>();
		for (LineString ls : tobreak.getBoundaryEdges()) {
			ArrayList<Coordinate> cs = new ArrayList<>();
			for (int i = 0; i < ls.getCoordinates().length; i ++) {
				Coordinate c = ls.getCoordinateN(i);
				if (breakpoints.contains(c) && cs.size() > 1) {
					cs.add(c);
					bnds.add(gf.createLineString(cs.toArray(new Coordinate[cs.size()])));
					cs.clear();
				}
				cs.add(c);
			}
			if(!cs.isEmpty() && cs.size() > 1) bnds.add(gf.createLineString(cs.toArray(new Coordinate[cs.size()])));
		}
		
		List<LineString> skels = new ArrayList<>();
		for (LineString ls : tobreak.getSkeletonEdges()) {
			ArrayList<Coordinate> cs = new ArrayList<>();
			for (int i = 0; i < ls.getCoordinates().length; i ++) {
				Coordinate c = ls.getCoordinateN(i);
				if (breakpoints.contains(c) && cs.size() > 0) {
					cs.add(c);
					skels.add(gf.createLineString(cs.toArray(new Coordinate[cs.size()])));
					cs.clear();
				}
				cs.add(c);
			}
			if(!cs.isEmpty() && cs.size() > 1) skels.add(gf.createLineString(cs.toArray(new Coordinate[cs.size()])));
		}
		
		List<LineString> inner = new ArrayList<>();
		for (LineString ls : tobreak.getInnerEdges()) {
			ArrayList<Coordinate> cs = new ArrayList<>();
			for (int i = 0; i < ls.getCoordinates().length; i ++) {
				Coordinate c = ls.getCoordinateN(i);
				if (breakpoints.contains(c) && cs.size() > 0) {
					cs.add(c);
					inner.add(gf.createLineString(cs.toArray(new Coordinate[cs.size()])));
					cs.clear();
				}
				cs.add(c);
			}
			if(!cs.isEmpty() && cs.size() > 1) inner.add(gf.createLineString(cs.toArray(new Coordinate[cs.size()])));
		}

		Polygonizer pgen = new Polygonizer();
		pgen.add(bnds);
		pgen.add(inner);
		pgen.add(skels);
		pgen.add(bankSkels);
		pgen.add(tobreak.getBankSkeletonEdges());
		
		Collection<Polygon> items = pgen.getPolygons();
		
		List<WorkingPolygon> working = new ArrayList<>();
		for (Polygon p : items) {
			WorkingPolygon wp = new WorkingPolygon (p);
			for (LineString ls : bnds) {
				if (ls.relate(p, "F1*F*****")) wp.getBoundaryEdges().add(ls);
			}
			for (LineString ls : inner) {
				if (ls.relate(p, "F1*F*****")) wp.getInnerEdges().add(ls);
			}
			for (LineString ls : skels) {
				if (ls.relate(p, "F1*F*****")) wp.getSkeletonEdges().add(ls);
			}
			for (LineString ls : bankSkels) {
				if (ls.relate(p, "F1*F*****")) wp.getBankSkeletonEdges().add(ls);
			}
			for (LineString ls : tobreak.getBankSkeletonEdges()) {
				if (ls.relate(p, "F1*F*****")) wp.getBankSkeletonEdges().add(ls);
			}
			if (!wp.getSkeletonEdges().isEmpty()) working.add(wp);
		}
		
		return working;
	}

	/**
	 * Find the closest points between to linestrings.  If
	 * the cloests points are near existing verticies then these
	 * verticies will be returned
	 * 
	 * @param ls1
	 * @param ls2
	 * @return
	 */
	private Coordinate[] findClosest(LineString ls1, LineString ls2) {
		Coordinate[] nearest = DistanceOp.nearestPoints(ls1, ls2);
		
		Coordinate c0 = nearest[0];
		Coordinate n = null;
		double distance  = Double.MAX_VALUE;
		for (Coordinate c : ls1.getCoordinates()) {
			double temp = c.distance(c0);
			if (temp < properties.getProperty(Property.BANK_MIN_VERTEX_DISTANCE) 
					&& temp < distance) {
				//snap to this coordinate
				n = c;
				distance = temp;
			}
		}
		if (n != null) c0 = n;
		
		Coordinate c1 = nearest[1];
		n = null;
		distance  = Double.MAX_VALUE;
		for (Coordinate c : ls2.getCoordinates()) {
			double temp = c.distance(c1);
			if (temp < properties.getProperty(Property.BANK_MIN_VERTEX_DISTANCE) 
					&& temp < distance) {
				//snap to this coordinate
				n = c;
				distance = temp;
			}
		}
		if (n != null) c1 = n;
		
		return new Coordinate[] {c0,c1};
	}
	
	/**
	 * Chops the linestring represented by the coordinate array from the min distance to the
	 * max distance creating new coordinates as required
	 * 
	 * @param items
	 * @param mindistance
	 * @param maxdistance
	 * @return
	 * @throws Exception
	 */
	private LineString chopLineString(Coordinate[] items, double mindistance, double maxdistance, GeometryFactory gf) throws Exception {
		double d = 0;
		ArrayList<Coordinate> coords = new ArrayList<>();
		for (int i = 1; i < items.length; i ++) {
			Coordinate c1 = items[i-1];
			Coordinate c2 = items[i];
			double oldd = d;
			d += c1.distance(c2);
			
			if (d > mindistance && coords.isEmpty()) {
				double off = ( mindistance - oldd ) / c1.distance(c2) ;
				LineSegment ls = new LineSegment(c1, c2);
				Coordinate c= ls.pointAlong(off);
				if (c.distance(c1)  < properties.getProperty(Property.BANK_MIN_VERTEX_DISTANCE)) {
					if (i == 1) {
						//cannot use the first point, so use this one even though it is close
						coords.add(c);
					}else {
						coords.add(c1);
					}
				}else if (c.distance(c2)  < properties.getProperty(Property.BANK_MIN_VERTEX_DISTANCE)) {
					//use c2 which will be added in the next go
					coords.add(c2);
				}else {
					coords.add(c);
				}
			}
			
			if (d > maxdistance) {
				double off = ( maxdistance - oldd ) / c1.distance(c2);
				LineSegment ls = new LineSegment(c1, c2);
				Coordinate c= ls.pointAlong(off);
				if (c.distance(c1)  < properties.getProperty(Property.BANK_MIN_VERTEX_DISTANCE)) {
					//use c1 which was already added unless this is a really short line
					if (coords.size() == 1) coords.add(c);
				}else if (c.distance(c2)  < properties.getProperty(Property.BANK_MIN_VERTEX_DISTANCE)) {
					if (i == items.length-1) {
						//cannot use the last point so use this one even though it is close
						coords.add(c);
					}else {
						coords.add(c2);
					}
				}else {
					coords.add(c);
				}
				break;
			}	
			if (!coords.isEmpty() && !coords.get(coords.size() - 1).equals2D(c2)) coords.add(c2);
		}	
		return gf.createLineString(coords.toArray(new Coordinate[coords.size()]));
	}
	
	class WorkingPolygon{
	
		private Polygon poly;
		private List<LineString> boundary = new ArrayList<>();  //exterior bank edges
		private List<LineString> inner = new ArrayList<>(); //interior bank edges
		private List<LineString> skels = new ArrayList<>(); //existing skeleton edges 
		private List<LineString> bankskels = new ArrayList<>(); //new bank skeleton edges
				
		public WorkingPolygon(Polygon p) {
			this.poly = p;
		}
		
		/**
		 * 
		 * @return the polygon
		 */
		public Polygon getPolygon() {
			return this.poly;
		}
		
		/**
		 * Regenerate the polygon from the boundary edges.
		 */
		@SuppressWarnings("unchecked")
		public void regeneratePolygon() {
			//regenerate the working polygon; this adds the verticies
			//we may have added to the ediges
			Polygonizer pgen = new Polygonizer();
			pgen.add(getBoundaryEdges());
			pgen.add(getInnerEdges());
			pgen.add(getSkeletonEdges());
			pgen.add(getBankSkeletonEdges());
			
			Polygon newp = null;
			List<Polygon> polygs = (List<Polygon>) pgen.getPolygons();
			if (polygs.size() == 1) {
				newp = polygs.get(0);
			}else {
				//find the outer polygon throwing away the ones 
				//that represent islands
				for (Polygon op : polygs) {
					if (op.getNumInteriorRing() > 0) {
						if (newp != null) throw new IllegalStateException("Multiple polygons generated.  No allowed.");
						newp = op;
					}
				}
			}
			if (newp == null) {

				throw new IllegalStateException("No polygon generated when regenerating skeleton lines.  Lines must form polygon.");
			}
			this.poly = newp;
		}
		
		/**
		 * 
		 * @return exterior ring bank edges 
		 */
		public List<LineString> getBoundaryEdges(){
			return this.boundary;
		}
		
		/**
		 * 
		 * @return interior ring edges 
		 */
		public List<LineString> getInnerEdges(){
			return this.inner;
		}
		
		/**
		 * 
		 * @return skeleton edges that form part of the 
		 * boundary of the polygon  
		 */
		public List<LineString> getSkeletonEdges(){
			return this.skels;
		}
		
		/**
		 * 
		 * @return bank skeleton edges that form part of the 
		 * boundary of the polygon  
		 */
		public List<LineString> getBankSkeletonEdges(){
			return this.bankskels;
		}
		
		@SuppressWarnings("unchecked")
		public LineString getMergedSkels() throws Exception{
			LineMerger lm = new LineMerger();
			lm.add(skels);
			
			Collection<LineString> items = lm.getMergedLineStrings();
			if (items.size() == 0) throw new Exception("No skeletons found");
			if (items.size() == 1) return (LineString)items.iterator().next();
			
			//skeletons could form holes; find the linestring that 
			//intersects the exterior ring
			LineString ex = null;
			for (LineString ls : items) {
				if (ls.intersects(poly.getExteriorRing())) {
					if (ex != null) throw new Exception("Skeletons don't form closed loop on exterior of polygon");
					ex = ls;
				}
			}
			return ex;
		}
	}

}
