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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.locationtech.jts.algorithm.Angle;
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.flowpathconstructor.ChyfProperties;
import net.refractions.chyf.flowpathconstructor.ChyfProperties.Property;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.ConstructionPoint;

/**
 * Generates skeletons for a waterbody from a set of in/out points
 * @author Emily
 *
 */
public class SkeletonGenerator {

	private ChyfProperties workingProperties;
	private ChyfProperties properties;

	public SkeletonGenerator(ChyfProperties prop) {
		this.properties = prop;
		System.out.println(properties.getProperty(Property.SKEL_DENSIFY_FACTOR));
	}

	
	public SkeletonResult generateSkeleton(Polygon waterbody, List<ConstructionPoint> inoutPoints) throws Exception {
		if (inoutPoints.size() < 2) throw new IOException("invalid number of input/output points");

		//copy the properties so we can change it as required for this run
		this.workingProperties = properties.clone();
		
		//if the construction points are closer than the densify factor
		//we need smaller densify factor
		//try 10x smaller
		boolean increase = false;
		for (ConstructionPoint p : inoutPoints) {
			for (ConstructionPoint p2 : inoutPoints) {
				if (p == p2) continue;
				if (p.getCoordinate().equals2D(p2.getCoordinate())) continue;
				if (p.getCoordinate().distance(p2.getCoordinate()) < workingProperties.getProperty(Property.SKEL_DENSIFY_FACTOR)) {
					increase = true;
					break;
				}
			}
			if (increase) break;
		}
		if (increase) {
			workingProperties.setProperty(Property.SKEL_DENSIFY_FACTOR, workingProperties.getProperty(Property.SKEL_DENSIFY_FACTOR) / 10);
			LoggerFactory.getLogger(SkeletonGenerator.class).info("Increaing densify factor by 10 for waterbody @ " + waterbody.getInteriorPoint().toText() );
		}
		
		
		List<Coordinate> points = preprocess2(waterbody, inoutPoints);	

		VoronoiDiagramBuilder builder = new VoronoiDiagramBuilder();
		builder.setSites(points);
		Envelope env = new Envelope(waterbody.getEnvelopeInternal());
		env.expandBy(1);
		builder.setClipEnvelope(env);
		Geometry voronoi = builder.getDiagram(waterbody.getFactory());
		
		Collection<LineSegment> segments = processVoronoi(waterbody, inoutPoints, voronoi);
		Collection<LineString> linestrings = filterExcess(segments, inoutPoints, waterbody.getFactory());
		Collection<SkeletonResult.Error> errors = validate(linestrings, waterbody, inoutPoints);
		
		Collection<SkelLineString> skeletons = linestrings.stream().map(ls->new SkelLineString(ls, (EfType) ls.getUserData())).collect(Collectors.toList());
		SkeletonResult result = new SkeletonResult(skeletons, errors);

		return result;
	}
	
	/**
	 * Perform basic QA:
	 * 1) ensure each skeleton line is wholly contained within the waterbody polygon
	 * 2) ensure each in/out point touches exactly one skeleton line
	 * 3) ensure no dangles in skeletons except at in/out points 
	 * 
	 * @param skeletons
	 * @param waterbody
	 * @param inoutPoints
	 */
	private Collection<SkeletonResult.Error> validate(Collection<LineString> skeletons, Polygon waterbody, List<ConstructionPoint> inoutPoints) {
		ArrayList<SkeletonResult.Error> errors = new ArrayList<>();
		//ensure linestring wholly contained within polygon
		HashMap<Coordinate, Integer> nodes = new HashMap<>();
		
		PreparedPolygon pp = new PreparedPolygon(waterbody);
		
		for (LineString ls : skeletons) {
			if (!pp.covers(ls)) {
				//error
				errors.add(new SkeletonResult.Error("Skeleton line crosses waterbody. ", ls));
			}
			
			Coordinate c1 = ls.getCoordinates()[0];
			Coordinate c2 = ls.getCoordinates()[ls.getCoordinates().length - 1];
			
			Integer s1 = nodes.get(c1);
			if (s1 == null){
				s1 = 1;
			}else {
				s1 = s1 + 1;
			}
			
			Integer s2 = nodes.get(c2);
			if (s2 == null){
				s2 = 1;
			}else {
				s2 = s2 + 1;
			}
			nodes.put(c1, s1);
			nodes.put(c2, s2);
		}
		
		Set<Coordinate> inout = inoutPoints.stream().map(e->e.getCoordinate()).collect(Collectors.toSet());
		//every degree 1 node should be in the inout points
		for (Entry<Coordinate, Integer> node : nodes.entrySet()) {
			if (node.getValue() != 1) continue;
			if (!inout.contains(node.getKey())) {
				//ERROR
				errors.add(new SkeletonResult.Error("Degree one node not at in/out point.",  waterbody.getFactory().createPoint(new Coordinate(node.getKey().x, node.getKey().y))));
			}
			
		}
		//every inout point should have a degree 1 node
		for (ConstructionPoint pnt : inoutPoints) {
			Integer d = nodes.get(pnt.getCoordinate());
			if (d == null) {
				//ERROR
				errors.add(new SkeletonResult.Error("No node at in/out point.",  waterbody.getFactory().createPoint(pnt.getCoordinate())));
			}else if (d != 1) {
				//ERROR
				errors.add(new SkeletonResult.Error("No node at in/out point has degree > 1.",  waterbody.getFactory().createPoint(pnt.getCoordinate())));
			}
		}
		return errors;
	}
	
	
	/**
	 * Removes the extra voronoi lines that are not required to
	 * create a path from an input to an output
	 * @return
	 */
	private Collection<LineString> filterExcess(Collection<LineSegment> segments, List<ConstructionPoint> inoutPoints, GeometryFactory gf){
		SkeletonGraph graph = SkeletonGraph.buildGraph(segments, inoutPoints);
		graph.mergedegree2();
		graph.trim(inoutPoints);
		graph.collapseShortEdges(workingProperties.getProperty(Property.SKEL_MINSIZE));
		graph.directionalizeBanks(inoutPoints);

		return graph.getSkeletons(workingProperties, gf);
			
	}
	
	/**
	 * Create voronoi edges
	 * 
	 * @param waterbody
	 * @param inoutPoints
	 * @param voronoi
	 * @return
	 * @throws Exception
	 */
	private Set<LineSegment> processVoronoi(Polygon waterbody, List<ConstructionPoint> inoutPoints, Geometry voronoi) throws Exception{
		Set<LineSegment> segments = new HashSet<>();
		Set<LineSegment> inoutsegments = new HashSet<>();
		
		
		IndexedPointInAreaLocator outer = new IndexedPointInAreaLocator(waterbody);

		//this is for the cases where there are acute angle in the
		//boundary of the polygons and the segment 
		//crosses the boundary
		FastSegInPolygon segInPoly = new FastSegInPolygon(waterbody);
		
		for (int i = 0; i < voronoi.getNumGeometries(); i ++) {
			Polygon p = (Polygon) voronoi.getGeometryN(i);		
			for (int j = 1; j < p.getCoordinates().length; j ++) {
				Coordinate c1 = p.getCoordinates()[j-1];
				Coordinate c2 = p.getCoordinates()[j];
				
				int loc1 = outer.locate(c1);
				boolean in1 = (loc1 == Location.INTERIOR || loc1 == Location.BOUNDARY);
				int loc2 = outer.locate(c2);
				boolean in2 = (loc2 == Location.INTERIOR || loc2 == Location.BOUNDARY);
		
				if (in1 && in2) {
					if (segInPoly.testSegment(c1, c2)) {
						segments.add(createSegment(c1, c2));
					}else {
						inoutsegments.add(createSegment(c1, c2));	
					}
				}else if (in1 || in2) {
					//TODO: figure this out - not sure about this
//					if (loc1 == Location.INTERIOR || loc2 == Location.INTERIOR) {
						inoutsegments.add(createSegment(c1, c2));
//					}
				}
			}
		}

		
		//only keep the inout segments that are closest to input/output point
		//and truncate to original inout point
		for (ConstructionPoint spnt : inoutPoints) {
			LineSegment nearest = null;
			double d = Double.MAX_VALUE;
			Coordinate c = spnt.getCoordinate();
			for (LineSegment inout : inoutsegments) {
				double temp = inout.distance(c);
				if(temp < d) {
					nearest = inout;
					d = temp;
				}
			}
			if (nearest == null) {
				throw new Exception("FAIL");
			}else {
				int loc = outer.locate(nearest.p0);
				if (loc == Location.INTERIOR || loc == Location.BOUNDARY) { 
					segments.add(createSegment(c, nearest.p0));
				}else {
					segments.add(createSegment(c, nearest.p1));
				}
			}
		}
		return segments;
	}
		
	
	/**
	 * Preprocess the waterbody polygon, adding points to the left and
	 * right of the input points and densifying the waterbody polygon
	 * 
	 * @param waterbody
	 * @param inoutPoints
	 * @return
	 */
	private List<Coordinate> preprocess2(Polygon waterbody, List<ConstructionPoint> inoutPoints) {
		//find all rings
		List<LineString> outside = new ArrayList<>();
		outside.add(waterbody.getExteriorRing());
		for (int i = 0; i < waterbody.getNumInteriorRing(); i ++) outside.add(waterbody.getInteriorRingN(i));
				
		HashSet<Coordinate> inoutset = new HashSet<>();
		for (ConstructionPoint p : inoutPoints) inoutset.add(p.getCoordinate());
				
		List<LineSegment> segments = new ArrayList<>();
		
		Set<Coordinate> densifyMore = new HashSet<>();
		
		double minAngle = Math.toRadians( workingProperties.getProperty(Property.SKEL_ACUTE_ANGLE) );
		double maxAngle = 2*Math.PI - minAngle;
		double densify = workingProperties.getProperty(Property.SKEL_DENSIFY_FACTOR);

		for (LineString ls : outside) {		
			Coordinate[] cs = ls.getCoordinates();
			
			List<Coordinate> all = new ArrayList<>();
			all.add(cs[0]);
			for (int i = 1; i < cs.length; i ++) {
				if (inoutset.contains(cs[i]) && inoutset.contains(cs[i-1])) {
					//break line segment in half
					Coordinate c = (new LineSegment(cs[i-1], cs[i])).midPoint();
					all.add(c);
				}
				all.add(cs[i]);
			}
			
			
			boolean last = false;
			for (int i = 0; i < all.size() - 1; i ++) {
				if (i == all.size() - 2 && last) continue;
				
				Coordinate c = all.get(i);
				LineSegment seg2 = new LineSegment(c, all.get(i + 1));

				if (inoutset.contains(c)) {
					LineSegment seg1 = null;
					if (i == 0) {
						seg1 = new LineSegment(all.get(all.size() - 2), c);
						last = true;
					}else {
						seg1 = segments.remove(segments.size() - 1);
					}
					
					double angle = Angle.interiorAngle(seg1.p0, c, seg2.p1);
					if(angle < minAngle || angle > maxAngle) {
						densifyMore.add(c);
					}
					Coordinate c1 = null;
					double inoutdensity = densify * 0.25;
					if (seg1.getLength() < inoutdensity) {
						c1 = seg1.p0;
					}else {
						c1 = seg1.pointAlong((seg1.getLength() - inoutdensity) / seg1.getLength());
					}
					
					double d1 = c1.distance(c);
					Coordinate c2 = null;
					if (seg2.getLength() < inoutdensity) {
						c2 = seg2.p1;
					}else {
						c2 = seg2.pointAlong(1 - ((seg2.getLength() - inoutdensity) / seg2.getLength()));
					}
					double d2 = c2.distance(c);
					
					if (d1 < d2) {
						c2 = seg2.pointAlong(d1/seg2.getLength());
					}else {
						c1 = seg1.pointAlong(1 - d2/seg1.getLength());
					}
					segments.add(new LineSegment(seg1.p0, c1));
					segments.add(new LineSegment(c2, seg2.p1));
				}else {
					segments.add(seg2);
				}
			}
		}

		HashSet<Coordinate> inputPoints = new HashSet<>();

		for (LineSegment s : segments) {
			double df = densify;
			for (Coordinate c : densifyMore) {
				if (s.distance(c) < densify * 10) {
					df = df / 10.0;
					break;
				}
			}
			densify(s.p0, s.p1, df, inputPoints);
			inputPoints.add(s.p1);
		}
//		for (Coordinate c : inputPoints) System.out.println("POINT(" + c.x + " " + c.y + ")");
		return inputPoints.stream().collect(Collectors.toList());
	}
	
	/**
	 * Densify the linesegment to the given distance.  The first coordinate is always
	 * added, the second coordinate is never added
	 * @param cs
	 * @param minSegmentDistance
	 * @return
	 */
	private static void densify(Coordinate c1, Coordinate c2, double minSegmentDistance,  Set<Coordinate> results) {		
		Coordinate last = c1;
		results.add(c1);

		double dist = c1.distance(c2);
		if (dist > minSegmentDistance) { // need to densify this segment
			int ndivisions = (int) (dist / minSegmentDistance);
			if (ndivisions < 1) ndivisions = 1;
			// always use the first point, never use the last
			for (int u = 0; u < ndivisions; u++) {
				double r = ((double) u + 1) / ((double) ndivisions + 1);
				Coordinate c = new Coordinate(last.x + r * (c2.x - last.x), last.y + r * (c2.y - last.y));
				results.add(c);
			}
		}
	}
	
	private static LineSegment createSegment(Coordinate c1, Coordinate c2) {
		if (c1.x < c2.x)
			return new LineSegment(c1, c2);
		else if (c2.x < c1.x)
			return new LineSegment(c2, c1);
		else if (c1.y < c2.y)
			return new LineSegment(c1, c2);
		return new LineSegment(c2, c1);
	}
	

}
