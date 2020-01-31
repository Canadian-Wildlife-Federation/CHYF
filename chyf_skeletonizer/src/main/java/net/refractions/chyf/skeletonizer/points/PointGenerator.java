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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.linemerge.LineMerger;

import net.refractions.chyf.ChyfProperties;
import net.refractions.chyf.ChyfProperties.Property;
import net.refractions.chyf.skeletonizer.points.ConstructionPoint.Direction;
import net.refractions.chyf.skeletonizer.points.ConstructionPoint.Type;

/**
 * Generates input/output points for skeletonizer
 * 
 * @author Emily
 *
 */
public class PointGenerator {
	
	private Set<ConstructionPoint> points;
	
	private Polygon workingWaterbody = null;
	private Set<Polygon> workingPolygons = new HashSet<>();
	private Set<Coordinate[]> insertCoordinates = new HashSet<>();
		
	private List<BoundaryEdge> boundaries;
	
	private ChyfProperties props;
	
	public PointGenerator(List<BoundaryEdge> boundaries, ChyfProperties props) {
		points = new HashSet<>();		
		this.boundaries = boundaries;
		this.props = props;
	}
	
	
	public Set<ConstructionPoint> getPoints(){
		return this.points;
	}
	
	public Collection<Polygon> getUpdatedPolygons(){
		ArrayList<Polygon> items = new ArrayList<>();
		if (PolygonInfo.isModified(workingWaterbody)) items.add(workingWaterbody);
		for (Polygon p : workingPolygons) if (PolygonInfo.isModified(p)) items.add(p);
		return items;
	}
	
	public void processPolygon(Polygon waterbodya, List<Polygon> touches, List<LineString> flowpaths) throws Exception {
		Set<ConstructionPoint> wbpoints = new HashSet<>();
	
		workingWaterbody = waterbodya;
		workingPolygons.clear();
		workingPolygons.addAll(touches);
		insertCoordinates.clear();
				
		if (flowpaths.isEmpty() && touches.isEmpty()) {
			wbpoints.addAll( processIsolated(workingWaterbody) );
			
		}else {
			//intersect the flowpaths with the exterior of the waterbody
			Set<Coordinate> inpoints = new HashSet<>();
			Set<Coordinate> outpoints = new HashSet<>();
			
			for (LineString ls : flowpaths) {
				outpoints.add(ls.getCoordinateN(0));
				inpoints.add(ls.getCoordinateN(ls.getNumPoints() - 1));
			}
		
			//create in/out points for linestring intersections
			processLineString(workingWaterbody.getExteriorRing(), inpoints, outpoints, wbpoints);
			for (int i = 0; i < workingWaterbody.getNumInteriorRing(); i ++) {
				processLineString(workingWaterbody.getInteriorRingN(i), inpoints, outpoints, wbpoints);	
			}
			
			//processing waterbodies intersections
			for (Polygon p : touches) {
				Collection<LineString> g = getIntersection(p, workingWaterbody);
				if (g == null) continue;
				for (LineString ls : g) {
					List<Coordinate> items = new ArrayList<>();
					for (Coordinate n : ls.getCoordinates()) items.add(n);
					wbpoints.add(new ConstructionPoint(findMidpoint(items, true), Type.WATER, Direction.UNKNOWN, (PolygonInfo) workingWaterbody.getUserData()));
				}
			}
			
			for (BoundaryEdge b : boundaries) {
				if (b.getLineString().getEnvelopeInternal().intersects(workingWaterbody.getEnvelopeInternal()) &&
						b.getLineString().intersects(workingWaterbody)) {
					if (b.getInOut() != null) {
						//use the b.getinout point
						List<Coordinate> items = new ArrayList<>();
						for (Coordinate n : b.getLineString().getCoordinates()) items.add(n);
						wbpoints.add(new ConstructionPoint(b.getInOut().getCoordinate(), Type.WATER, b.getDirection(), (PolygonInfo) workingWaterbody.getUserData()));
					}
				}
			}
		}
		//if there are only in nodes then we need to add an out node
		//TODO: make sure these points are not created
		//where waterbodies intersect
		int incount = 0;
		int outcount = 0;
		int unknowncount = 0;
		for (ConstructionPoint p : wbpoints) {
			if (p.getDirection() == Direction.IN) incount ++;
			if (p.getDirection() == Direction.OUT) outcount ++;
			if (p.getDirection() == Direction.UNKNOWN) unknowncount ++;
		}
		if (outcount == 0 && incount > 0 && unknowncount == 0) {
			//list all the points from the  
			Coordinate c = findLongestMidPoint(workingWaterbody.getExteriorRing(), touches, wbpoints.stream().map(a->a.getCoordinate()).collect(Collectors.toSet()), false);
			wbpoints.add(new ConstructionPoint(c, Type.TERMINAL, Direction.OUT, (PolygonInfo) workingWaterbody.getUserData()));
		}
		//if there are only outnodes then we need to add an in node
		if (incount == 0 && outcount > 0 && unknowncount == 0) {
			Coordinate c = findLongestMidPoint(workingWaterbody.getExteriorRing(), touches, wbpoints.stream().map(a->a.getCoordinate()).collect(Collectors.toSet()), false);
			wbpoints.add(new ConstructionPoint(c, Type.HEADWATER, Direction.IN, (PolygonInfo) workingWaterbody.getUserData()));
		}
		if (unknowncount == 1 && incount == 0 && outcount == 0) {
			Coordinate c = findLongestMidPoint(workingWaterbody.getExteriorRing(), touches, wbpoints.stream().map(a->a.getCoordinate()).collect(Collectors.toSet()), false);
			//TODO: incorrect classification; thought could be either headwater or terminal, but it is a degree1 node
			wbpoints.add(new ConstructionPoint(c, Type.HEADWATER, Direction.UNKNOWN, (PolygonInfo) workingWaterbody.getUserData()));
		}
		
		//add bank points, ensuring they are not added
		//at the waterbody intersections
		Set<ConstructionPoint> banks = addBanks(workingWaterbody, wbpoints, workingPolygons);
		points.addAll(wbpoints);
		points.addAll(banks);		
		//add verticies to all polygons as necessary
		addVerticies();
	}
	
	/**
	 * Intersects the two polygons and returns the linestrings
	 * where they overlap. 
	 * 
	 * @param p1
	 * @param p2
	 * @return
	 */
	private Collection<LineString> getIntersection(Polygon p1, Polygon p2) {
		Geometry g = p1.intersection(p2);
		if (g == null) return null;
		if (g.getNumGeometries() == 0) return null;
		
		LineMerger m = new LineMerger();
		for (int i = 0; i < g.getNumGeometries(); i ++) {
			m.add(g.getGeometryN(i));
		}
		ArrayList<LineString> items = new ArrayList<>(m.getMergedLineStrings());
		
		//for the case where an interior ring touches the boundary
		//at a single point
		int i = 0;
		List<LineString> interiors = new ArrayList<>();
		for (int j = 0; j < p1.getNumInteriorRing(); j ++) interiors.add(p1.getInteriorRingN(j));
		for (int j = 0; j < p2.getNumInteriorRing(); j ++) interiors.add(p2.getInteriorRingN(j));
		while (i < items.size()) {
			LineString ls = items.get(i);
			
			for (LineString ls2 : interiors) {
				//does a coordinate on this interior ring match a coordinate on the linestring??
				Coordinate[] cc = ls2.getCoordinates();
				
				for (int k = 1; k < ls.getCoordinates().length-1; k ++) {
					boolean found = false;
					for (int l = 0; l < cc.length; l ++) {
						if (ls.getCoordinates()[k].equals2D(cc[l])) {
							found = true;
							break;
						}
					}
					if (found) {
						//split linestring; reset counter and continue
						LineString l1 = (new  GeometryFactory()).createLineString(Arrays.copyOfRange(ls.getCoordinates(), 0, k+1));
						LineString l2 = (new  GeometryFactory()).createLineString(Arrays.copyOfRange(ls.getCoordinates(), k, ls.getCoordinates().length));
						items.remove(ls);
						items.add(l1);
						items.add(l2);
						i = 0;
						continue;
					}
				}
			}
			i++;
		}
		return items;
	}

	/**
	 * Finds the midpoint of the longest segment broken at the points
	 * 
	 * Used for adding terminal or headwater points if required
	 * 
	 * @param ls
	 * @param points
	 */
	private Coordinate findLongestMidPoint(LineString ls, List<Polygon> touches, Set<Coordinate> points, boolean interpolate) {
		
		
		//first remove any shared edges from this linestring
		Geometry temp = ls;
		for (Polygon p : touches) {
			if (!p.getExteriorRing().getEnvelopeInternal().intersects(temp.getEnvelopeInternal())) continue;
			temp = temp.difference(p.getExteriorRing());
		}
		for (BoundaryEdge g : boundaries) {
			if (!g.getLineString().getEnvelopeInternal().intersects(temp.getEnvelopeInternal())) continue;
			temp = temp.difference(g.getLineString());
		}
		
		//if its a linestring
		if (!(temp instanceof LineString)) throw new RuntimeException("not a linestring");
		
		ls = (LineString)temp;

		
		HashMap<Double, Coordinate> options = new HashMap<>();
		
		Coordinate[] cs = ls.getCoordinates();
		int start = 0;
		for(int i = 0; i < cs.length; i ++) {
			if (points.contains(cs[i])) {
				start = i;
				break;
			}
		}
		
		int cnt = (start+1) % (cs.length - 1);
		double distance = 0;
		List<Coordinate> coords = new ArrayList<>();
		coords.add(ls.getCoordinateN(start));
		while(cnt != start) {
			Coordinate n = ls.getCoordinateN(cnt);
			coords.add(n);
			distance += n.distance(coords.get(coords.size() - 1));
			
			if (points.contains(n)) {
				//find the midpoint between first and cnt and create node
				options.put(distance, findMidpoint(coords, interpolate));
				coords = new ArrayList<>();
				coords.add(n);
				distance = 0;
			}
			cnt = (cnt+1) % (ls.getCoordinates().length - 1);
		}
		//find midpoint between first and cnt and create node
		options.put(distance, findMidpoint(coords, interpolate));
	
		return options.get(options.keySet().stream().reduce(Math::max).get());
	}
	
	
	private void processLineString(LineString ls, Set<Coordinate> inpoints, Set<Coordinate> outpoints, Set<ConstructionPoint> points ) {
		for (int i = 0; i < ls.getCoordinates().length-1; i ++) {
			Coordinate c = ls.getCoordinateN(i);
			if (outpoints.contains(c)) {
				points.add(new ConstructionPoint(c, Type.FLOWPATH, Direction.OUT, (PolygonInfo) workingWaterbody.getUserData()));
			}else if (inpoints.contains(c)) {
				points.add(new ConstructionPoint(c, Type.FLOWPATH, Direction.IN, (PolygonInfo) workingWaterbody.getUserData()));
			}
		}
	}
	
	/**
	 * Add Bank point
	 * @param waterbody the polygon to add banks to
	 * @param flowpoints in/out points
	 * @param donotuse set of points to not use for bank flows
	 * @return
	 */
	private Set<ConstructionPoint> addBanks(Polygon waterbody, Set<ConstructionPoint> flowpoints,  Set<Polygon> otherpolys){
		if (flowpoints.isEmpty()) return Collections.emptySet();

		Set<ConstructionPoint> banks = new HashSet<>();
		
		Set<Coordinate> flows = new HashSet<>();
		boolean hasheadwater = false;
		for(ConstructionPoint p : flowpoints) {
			flows.add(p.getCoordinate());
			if (p.getType() == Type.HEADWATER) hasheadwater = true;
			if (p.getType() == Type.TERMINAL) hasheadwater = true;
		}
				
		List<LineString> toprocess = new ArrayList<>();
		toprocess.add(waterbody.getExteriorRing());
		waterbody.getExteriorRing().setUserData(hasheadwater);
		
		for (int i = 0; i < waterbody.getNumInteriorRing(); i ++) {
			toprocess.add(waterbody.getInteriorRingN(i));
			waterbody.getInteriorRingN(i).setUserData(false);
		}
		
		for (LineString ls : toprocess) {
			Geometry temp = ls;
			for (Polygon p : otherpolys) {
				if (!p.getExteriorRing().getEnvelopeInternal().intersects(temp.getEnvelopeInternal())) continue;
				temp = temp.difference(p.getExteriorRing());
			}
			for (BoundaryEdge g : boundaries) {
				if (!g.getLineString().getEnvelopeInternal().intersects(temp.getEnvelopeInternal())) continue;
				temp = temp.difference(g.getLineString());
			}
			List<LineString> items = new ArrayList<>();
			if (temp instanceof LineString) {
				items.add((LineString)temp);
			}else if (temp instanceof MultiLineString) {
				for (int i = 0; i < temp.getNumGeometries(); i ++) {
					items.add((LineString)temp.getGeometryN(i));
				}
			}else {
				throw new RuntimeException("Invalid geometry type");
			}

			LineMerger merger = new LineMerger();
			merger.add(items);
			Collection<LineString> items2 = merger.getMergedLineStrings();
			
			for (LineString i : items2) {
				generateBanks(i, flows, (boolean)ls.getUserData(), banks);
			}
		}

		return banks;
	}
	
	/**
	 * Generate bank flowpaths
	 * 
	 * @param the linestring representing the bank edit
	 * @param flows
	 * @param hasheadwater
	 * @param banks
	 * @param donotuse coordinates to not use for flowpath node
	 */
	private void generateBanks(LineString ls, Set<Coordinate> flows, boolean hasheadwater,  Set<ConstructionPoint> banks) {
		Coordinate[] cs = ls.getCoordinates();
		if (cs[0].equals2D(cs[cs.length - 1])) {
			int start = 0;
			//ring start at first in/out point
			for(int i = 0; i < cs.length; i ++) {
				if (flows.contains(cs[i])) {
					start = i;
					break;
				}
			}
			int cnt = (start+1) % (cs.length - 1);
			
			List<Coordinate> coords = new ArrayList<>();
			coords.add(cs[start]);
			
			while(cnt != start) {
				Coordinate n = cs[cnt];
				coords.add(n);
				if (flows.contains(n)) {
					//find the midpoint between first and cnt and create node
					banks.add(new ConstructionPoint(findMidpoint(coords, false),Type.BANK, Direction.IN, (PolygonInfo) workingWaterbody.getUserData()));
					coords = new ArrayList<>();
					coords.add(n);
				}
				cnt = (cnt+1) % (cs.length - 1);
			}
			//find midpoint between first and cnt and create node
			coords.add(cs[start]);
			if (coords.size() > 1 && !hasheadwater) banks.add(new ConstructionPoint(findMidpoint(coords, false),Type.BANK, Direction.IN, (PolygonInfo) workingWaterbody.getUserData()));
		}else {
			List<Coordinate> coords = new ArrayList<>();
			for (int i = 0; i < cs.length; i ++) {
				coords.add(cs[i]);
				if (flows.contains(cs[i])) {
					banks.add(new ConstructionPoint(findMidpoint(coords, false),Type.BANK, Direction.IN, (PolygonInfo) workingWaterbody.getUserData()));
					coords.clear();
					coords.add(cs[i]);	
					
				}
			}
			if (!hasheadwater) banks.add(new ConstructionPoint(findMidpoint(coords, false),Type.BANK, Direction.IN, (PolygonInfo) workingWaterbody.getUserData()));
		}
		
	

	}
	
	/**
	 * Find the mid-point of the set of coordinates. If one of the coordinates
	 * is already flagged as an in/out point it will return this coordinate. 
	 * Otherwise this will return an existing coordinate if the mid-point is "NEAR" the
	 * middle of the coordinates, otherwise it will return a new coordinate
	 * 
	 * @param interpolate if false will always return an existing coordinate (unless there are
	 * only two coordinate in which case it will add a new one).  if true then if
	 * the existing coordinate is more then a given distance from the centerpoint
	 * it will add a new coorindate at the centerpoint
	 */
	private Coordinate findMidpoint(List<Coordinate> items, boolean interpolate) {		
		//if there is already a point on this line, reuse the exact same point
		for (Coordinate c : items) {
			for (ConstructionPoint p : points) {
				if (p.getCoordinate().equals2D(c))  return c;
			}
		}

		double vertexDistance = props.getProperty(Property.PNT_VERTEX_DISTANCE);

		Coordinate[] mid = findMidPoint(items,  vertexDistance, interpolate);
		if (mid == null) return null;
		if (mid[1] != null) { 
			insertCoordinates.add(new Coordinate[] {mid[1], mid[2], mid[0]});
		}
		return mid[0];
//		if (items.size() == 2) {
//			//we are going to have to add another vertex to waterbody
//			Coordinate c = LineSegment.midPoint(items.get(0),  items.get(1));
//			insertCoordinates.add(new Coordinate[] {items.get(0), items.get(1), c});
//			return c;
//		}
//		
//		
//		//if mid coordinate is not near the center in terms of distance
//		//then add a vertex note: vertex will need to be added to all waterbodies here
//		double length = 0;
//		for (int i = 1; i < items.size(); i ++) {
//			length += items.get(i).distance(items.get(i-1));
//		}
//		double target = length / 2.0;
//		double distance = 0;
//		
//		for (int i = 1; i < items.size(); i ++) {
//			double lastdistance = distance;
//			distance += items.get(i-1).distance(items.get(i));
//			if (distance > target) {
//				//stop here
//				if (target - lastdistance < distance - target) {
//					if (interpolate && (target-lastdistance) > vertexDistance) {
//						//compute a coordinate nearest to the center
//						LineSegment seg = new LineSegment(items.get(i-1), items.get(i));
//						Coordinate c = seg.pointAlong((target-lastdistance) / seg.getLength());
//						
//						insertCoordinates.add(new Coordinate[] {items.get(i-1), items.get(i), c});
//						return c;
//					}else {
//						return items.get(i-1);
//					}
//				}else {
//					if (interpolate &&  distance - target > vertexDistance) {
//						//add new
//						LineSegment seg = new LineSegment(items.get(i-1), items.get(i));
//						Coordinate c = seg.pointAlong( 1 - ((distance-target) / seg.getLength()));
//						insertCoordinates.add(new Coordinate[] {items.get(i-1), items.get(i), c});
//						return c;
//					}else {
//						return items.get(i);
//					}
//				}
//			}
//		}
//		return null;
	}
	
	public static Coordinate[] findMidPoint(List<Coordinate> items, double vertexDistance, boolean interpolate) {
		
		if (items.size() == 2) {
			//we are going to have to add another vertex to waterbody
			Coordinate c = LineSegment.midPoint(items.get(0),  items.get(1));
			return new Coordinate[] {c, items.get(0), items.get(1)};
//			insertCoordinates.add(new Coordinate[] {items.get(0), items.get(1), c});
//			return c;
		}
		
		//if mid coordinate is not near the center in terms of distance
		//then add a vertex note: vertex will need to be added to all waterbodies here
		double length = 0;
		for (int i = 1; i < items.size(); i ++) {
			length += items.get(i).distance(items.get(i-1));
		}
		double target = length / 2.0;
		double distance = 0;
		
		for (int i = 1; i < items.size(); i ++) {
			double lastdistance = distance;
			distance += items.get(i-1).distance(items.get(i));
			if (distance > target) {
				//stop here
				if (target - lastdistance < distance - target) {
					if (interpolate && (target-lastdistance) > vertexDistance) {
						//compute a coordinate nearest to the center
						LineSegment seg = new LineSegment(items.get(i-1), items.get(i));
						Coordinate c = seg.pointAlong((target-lastdistance) / seg.getLength());
						
//						insertCoordinates.add(new Coordinate[] {items.get(i-1), items.get(i), c});
//						return c;
						return new Coordinate[] {c, items.get(i-1), items.get(i)};
					}else {
						return new Coordinate[] {items.get(i-1), null, null};
					}
				}else {
					if (interpolate &&  distance - target > vertexDistance) {
						//add new
						LineSegment seg = new LineSegment(items.get(i-1), items.get(i));
						Coordinate c = seg.pointAlong( 1 - ((distance-target) / seg.getLength()));
//						insertCoordinates.add(new Coordinate[] {items.get(i-1), items.get(i), c});
//						return c;
						return new Coordinate[] {c, items.get(i-1), items.get(i)};
					}else {
//						return items.get(i);
						return new Coordinate[] {items.get(i), null, null};

					}
				}
			}
		}
		return null;
	}
	
	/**
	 * Process an isolated waterbody.  Adds in/out points
	 * at approximately opposite ends of the polygon
	 * 
	 * @param waterbody
	 * @return
	 */
	private Set<ConstructionPoint> processIsolated(Polygon waterbody) {
		
		Coordinate[] cs = waterbody.getExteriorRing().getCoordinates();
		
		double target = waterbody.getExteriorRing().getLength() / 2.0;
		Coordinate start = cs[0];
		double distance = 0;
		int index = -1;
		for (int i = 1; i < cs.length; i ++) {
			double lastdistance = distance;
			distance += cs[i-1].distance(cs[i]);
			if (distance > target) {
				//stop here
				if (target - lastdistance < distance - target) {
					index = i-1;
				}else {
					index = i;
				}
				break;		
			}
		}
		Coordinate end = cs[index];
		Set<ConstructionPoint> points = new HashSet<>();
		points.add(new ConstructionPoint(start, Type.HEADWATER, Direction.IN, (PolygonInfo) workingWaterbody.getUserData()));
		points.add(new ConstructionPoint(end, Type.TERMINAL, Direction.OUT, (PolygonInfo) workingWaterbody.getUserData()));
		return points;
	}
	
	/**
	 * If the coordinate intersects one of the working polygons but there
	 * is no vertex at this point then add a vertex 
	 * @param c
	 */
	private void addVerticies() {
		workingWaterbody = addVertex(workingWaterbody, insertCoordinates);
		
		HashSet<Polygon> newwb = new HashSet<>();
		for (Polygon p : workingPolygons) {
			newwb.add(addVertex(p, insertCoordinates));
		}
		this.workingPolygons = newwb;
		insertCoordinates.clear();
	}
	
	public static Polygon addVertex(Polygon p, Set<Coordinate[]> insertPoints) {
		boolean modified = false;
		 LineString[] in = new LineString[p.getNumInteriorRing()+1];
		 in[0] = p.getExteriorRing();
		 for (int i = 0; i < p.getNumInteriorRing(); i ++) {
			 in[i+1] = p.getInteriorRingN(i);
		 }
		
		 LineString[] out = new LineString[in.length];
		 
		 for (int i = 0; i < in.length; i ++) {
	 		 out[i] = in[i];
	 		 
	 		Coordinate[] cs = in[i].getCoordinates();
	 		List<Coordinate> newCoordinates = new ArrayList<>();
	 		newCoordinates.add(cs[0]);
			 for (int k = 1; k < cs.length; k ++) {
				 
				 for (Coordinate[] pnts : insertPoints) {
					 Coordinate c0 = pnts[0];
					 Coordinate c1 = pnts[1];
	 				 if (cs[k-1].equals2D(c0) && cs[k].equals2D(c1)) {
	 					 //insert pnts[2]
	 					newCoordinates.add(pnts[2]);
	 					modified = true;
	 				 }
	 				if (cs[k-1].equals2D(c1) && cs[k].equals2D(c0)) {
	 					newCoordinates.add(pnts[2]);
	 					modified = true;
	 				}
	 			 }
				newCoordinates.add(cs[k]);
	 		 }
	 		 if (cs.length != newCoordinates.size()) {
	 			 out[i] = p.getFactory().createLineString(newCoordinates.toArray(new Coordinate[newCoordinates.size()]));
	 		 }
		 }
		 if (!modified) return p;
		 
		 GeometryFactory gf = p.getFactory();
		 LinearRing[] rings = new LinearRing[out.length];
		 for (int i = 0; i < out.length; i ++) {
			 rings[i] = gf.createLinearRing(out[i].getCoordinates());
		 }
		 Polygon newp = p.getFactory().createPolygon(rings[0], Arrays.copyOfRange(rings, 1, rings.length));
		 newp.setUserData(p.getUserData());
		 PolygonInfo.setModified(newp, true);
		 return newp;
	}
	
	
}
