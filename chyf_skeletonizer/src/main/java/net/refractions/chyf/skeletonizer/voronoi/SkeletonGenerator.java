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
package net.refractions.chyf.skeletonizer.voronoi;

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
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder;

import net.refractions.chyf.ChyfProperties;
import net.refractions.chyf.ChyfProperties.Property;
import net.refractions.chyf.skeletonizer.points.SkeletonPoint;
import net.refractions.fastPIP.FastPIP;
import net.refractions.fastPIP.FastSegInPolygon;

/**
 * Generates skeletons for a waterbody from a set of in/out points
 * @author Emily
 *
 */
public class SkeletonGenerator {
	
	private static final double IN_OUT_PNT_DISTANCE_PERCENT = 0.9;
	
	private ChyfProperties properties;

	public SkeletonGenerator(ChyfProperties prop) {
		this.properties = prop;
	}

	
	public SkeletonResult generateSkeleton(Polygon waterbody, List<SkeletonPoint> inoutPoints) throws Exception {
		if (inoutPoints.size() < 2) throw new IOException("invalid number of input/output points");
		
		ArrayList<Coordinate> points = preprocess(waterbody, inoutPoints);
	
		VoronoiDiagramBuilder builder = new VoronoiDiagramBuilder();
		builder.setSites(points);
		builder.setClipEnvelope(waterbody.getEnvelopeInternal());
		Geometry voronoi = builder.getDiagram(waterbody.getFactory());
		
		Collection<LineSegment> segments = processVoronoi(waterbody, inoutPoints, voronoi);
		Collection<LineString> skeletons = filterExcess(segments, inoutPoints, waterbody.getFactory());
		Collection<String> errors = validate(skeletons, waterbody, inoutPoints);
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
	private Collection<String> validate(Collection<LineString> skeletons, Polygon waterbody, List<SkeletonPoint> inoutPoints) {
		ArrayList<String> errors = new ArrayList<>();
		//ensure linestring wholly contained within polygon
		HashMap<Coordinate, Integer> nodes = new HashMap<>();
		
		PreparedPolygon pp = new PreparedPolygon(waterbody);
		
		for (LineString ls : skeletons) {
			if (!pp.covers(ls)) {
				//error
				errors.add("Skeleton line crosses waterbody: " + ls.toText());
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
				errors.add("Degree one node not at in/out point: POINT(" + node.getKey().x + " " + node.getKey().y + ")");
			}
			
		}
		//every inout point should have a degree 1 node
		for (SkeletonPoint pnt : inoutPoints) {
			Integer d = nodes.get(pnt.getCoordinate());
			if (d == null) {
				//ERROR
				errors.add("No node at in/out point: POINT(" + pnt.getCoordinate().x + " " + pnt.getCoordinate().y + ")");
			}else if (d != 1) {
				//ERROR
				errors.add("No node at in/out point has degree > 1: POINT(" + pnt.getCoordinate().x + " " + pnt.getCoordinate().y + ")");
			}
		}
		return errors;
	}
	
	
	/**
	 * Removes the extra voronoi lines that are not required to
	 * create a path from an input to an output
	 * @return
	 */
	private Collection<LineString> filterExcess(Collection<LineSegment> segments, List<SkeletonPoint> inoutPoints, GeometryFactory gf){
		SkeletonGraph graph = SkeletonGraph.buildGraph(segments, inoutPoints);
		graph.mergedegree2();
		graph.trim(inoutPoints);
		graph.collapseShortEdges(properties.getProperty(Property.SKEL_MINSIZE));
		graph.directionalize(inoutPoints);

		return graph.getSkeletons(properties, gf);
			
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
	private Set<LineSegment> processVoronoi(Polygon waterbody, List<SkeletonPoint> inoutPoints, Geometry voronoi) throws Exception{
		Set<LineSegment> segments = new HashSet<>();
		Set<LineSegment> inoutsegments = new HashSet<>();
		
		FastPIP outer = new FastPIP(waterbody);
		
		//this is for the cases where there are acute angle in the
		//boundary of the polygons and the segment 
		//crosses the boundary
		FastSegInPolygon segInPoly = new FastSegInPolygon(waterbody);
		
		for (int i = 0; i < voronoi.getNumGeometries(); i ++) {
			Polygon p = (Polygon) voronoi.getGeometryN(i);		
			for (int j = 1; j < p.getCoordinates().length; j ++) {
				Coordinate c = p.getCoordinates()[j-1];
				Coordinate c2 = p.getCoordinates()[j];
				
				boolean in1 = outer.PIP(c);
				boolean in2 = outer.PIP(c2);
				if (in1 && in2) {
					if (segInPoly.testSegment(c, c2)) {
						segments.add(createSegment(c, c2));
					}
				}else if (in1 || in2) {
					inoutsegments.add(createSegment(c, c2));
				}
			}
		}
		//only keep the inout segments that are closest to input/output point
		//and truncate to original inout point
		for (SkeletonPoint spnt : inoutPoints) {
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
				if (outer.PIP(nearest.p0)) {
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
	private ArrayList<Coordinate> preprocess(Polygon waterbody, List<SkeletonPoint> inoutPoints) {
		//find all rings
		List<LineString> outside = new ArrayList<>();
		outside.add(waterbody.getExteriorRing());
		for (int i = 0; i < waterbody.getNumInteriorRing(); i ++) outside.add(waterbody.getInteriorRingN(i));
				
		HashSet<Coordinate> inoutset = new HashSet<>();
		for (SkeletonPoint p : inoutPoints) inoutset.add(p.getCoordinate());
				
		//for each linestring
		//1. densify
		//2. remove points at in/out point and add a point close by on either side
		ArrayList<Coordinate> inputPoints = new ArrayList<>();

		double minAngle = properties.getProperty(Property.SKEL_ACUTE_ANGLE_RAD);
		double maxAngle = 2*Math.PI - minAngle;
		
		for (LineString ls : outside) {		
			Coordinate[] cs = ls.getCoordinates();
			boolean skipnext = false;
			boolean closed = false;
			for (int i = 0; i < cs.length -1; i ++) {
				Coordinate c = ls.getCoordinateN(i);
				if (inoutset.contains(c)) {
					LineSegment seg1 = null;
					if (i == 0) {
						closed = true;
						seg1 = new LineSegment(cs[cs.length - 2], c);
					}else {
						seg1 = new LineSegment(cs[i-1], c);
					}
					LineSegment seg2 = new LineSegment(c, cs[i+1]);
					
					Coordinate c1 = seg1.pointAlong(IN_OUT_PNT_DISTANCE_PERCENT);
					double d1 = c1.distance(c);
					if (d1 > properties.getProperty(Property.SKEL_DENSIFY_FACTOR)) {
						c1 = seg1.pointAlong((seg1.getLength() - properties.getProperty(Property.SKEL_DENSIFY_FACTOR)) / seg1.getLength());
						d1 = c1.distance(c);
					}
					Coordinate c2 = seg2.pointAlong(1-IN_OUT_PNT_DISTANCE_PERCENT);
					double d2 = c2.distance(c);
					if (d2 > properties.getProperty(Property.SKEL_DENSIFY_FACTOR)) {
						c2 = seg2.pointAlong(1 - ((seg2.getLength() - properties.getProperty(Property.SKEL_DENSIFY_FACTOR)) / seg2.getLength()));
						d2 = c2.distance(c);
					}
					
					if (d1 < d2) {
						c2 = seg2.pointAlong(d1/seg2.getLength());
					}else {
						c1 = seg1.pointAlong(1 - d2/seg1.getLength());
					}
									
					double angle = Angle.interiorAngle(seg1.p0, c, seg2.p1);
					if(angle < minAngle || angle > maxAngle) {
						densify(seg1.p0, c1, properties.getProperty(Property.SKEL_DENSIFY_FACTOR) / 10.0, inputPoints);
						inputPoints.add(c1);
						densify(c2, seg2.p1, properties.getProperty(Property.SKEL_DENSIFY_FACTOR) / 10.0, inputPoints);
					}else {
						densify(seg1.p0, c1, properties.getProperty(Property.SKEL_DENSIFY_FACTOR), inputPoints);
						inputPoints.add(c1);
						densify(c2, seg2.p1, properties.getProperty(Property.SKEL_DENSIFY_FACTOR), inputPoints);
						
					}
					skipnext = true;
				}else if (i != 0 && !skipnext) {
					densify(cs[i-1], cs[i], properties.getProperty(Property.SKEL_DENSIFY_FACTOR), inputPoints);
				}else {
					skipnext = false;
				}
			}
			if (!closed) {
				densify(cs[cs.length - 2], cs[0], properties.getProperty(Property.SKEL_DENSIFY_FACTOR), inputPoints);
			}
		}
		return inputPoints;
	}
	
	/**
	 * Densify the linesegment to the given distance.  The first coordinate is always
	 * added, the second coordinate is never added
	 * @param cs
	 * @param minSegmentDistance
	 * @return
	 */
	private static void densify(Coordinate c1, Coordinate c2, double minSegmentDistance,  ArrayList<Coordinate> results) {		
		Coordinate last = c1;
		results.add(c1);

		double dist = c1.distance(c2);
		if (dist > minSegmentDistance) { // need to densify this segment
			int ndivisions = (int) (dist / minSegmentDistance);
			if (ndivisions < 1) ndivisions = 1;
			// allways use the first point, never use the last
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
