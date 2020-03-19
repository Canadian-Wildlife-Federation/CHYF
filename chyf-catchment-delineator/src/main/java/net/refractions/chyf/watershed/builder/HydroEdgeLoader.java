/*******************************************************************************
 * Copyright 2020 Government of Canada
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
 *******************************************************************************/
package net.refractions.chyf.watershed.builder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.EcType;
import net.refractions.chyf.util.ProcessStatistics;
import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.WaterSide;

public class HydroEdgeLoader {
	static final Logger logger = LoggerFactory.getLogger(HydroEdgeLoader.class);
	private DataManager dm;
	private GeometryFactory gf;
	private int nextDrainageId = 1;
	List<HydroEdge> edges;
	
	private Map<LineSegment, HydroChainLink> segmentMap = new HashMap<LineSegment, HydroChainLink>();
	private Set<Coordinate> nodeSet = new HashSet<Coordinate>();
	
	public HydroEdgeLoader(DataManager dm) {
		this.dm = dm;
		gf = dm.getGeometryFactory();
	}
	
	public void load() {
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "Loading all hydro edges and assigning drainageIds");
		dm.deleteHydroEdges();
		edges = new ArrayList<HydroEdge>();
		
		// build a maximal envelope
		Envelope overallEnv = new Envelope();

		// load flowpaths
		List<Geometry> flowpaths = dm.getInputFlowpaths();
		for(Geometry g : flowpaths) {
			for (int i = 0; i < g.getNumGeometries(); i++) {
				Geometry geomN = g.getGeometryN(i);
				if (geomN instanceof LineString) {
					LineString ls = (LineString) geomN;
					overallEnv.expandToInclude(ls.getEnvelopeInternal());
					HydroEdge edge = new HydroEdge(ls, nextDrainageId++, WaterSide.NEITHER);
					edges.add(edge);
					nodeSet.add(ls.getCoordinateN(0));
					nodeSet.add(ls.getCoordinateN(ls.getNumPoints() - 1));
				} else {
					logger.warn("non-Linestring encountered in flowpaths");
				}
			}
		}
		
		// load waterbodies
		List<Geometry> waterbodies = dm.getInputWaterbodies();
		for(Geometry g : waterbodies) {
			if(!g.isValid()) {
				g = g.buffer(0);
			}
			for (int i = 0; i < g.getNumGeometries(); i++) {
				Geometry geomN = g.getGeometryN(i);
				if (geomN instanceof Polygon) {
					Polygon poly = (Polygon) geomN;
					overallEnv.expandToInclude(poly.getEnvelopeInternal());
					LineString ls = poly.getExteriorRing();
					WaterSide side = WaterSide.RIGHT;
					if(Orientation.isCCW(ls.getCoordinateSequence())) {
						side = WaterSide.LEFT;
					}
					addSegments(ls, side);
					for(int n = 0; n < poly.getNumInteriorRing(); n++) {
						ls = poly.getInteriorRingN(n);
						side = WaterSide.LEFT;
						if(Orientation.isCCW(ls.getCoordinateSequence())) {
							side = WaterSide.RIGHT;
						}
						addSegments(ls, side);
					}
				} else {
					logger.warn("non-polygon encountered in waterbodies");
				}
			}
		}
		
		// load coastlines
		List<Geometry> coastlines = dm.getInputShorelines();
		for(Geometry g : coastlines) {
			for (int i = 0; i < g.getNumGeometries(); i++) {
				Geometry geomN = g.getGeometryN(i);
				if (geomN instanceof LineString) {
					LineString ls = (LineString) geomN;
					overallEnv.expandToInclude(ls.getEnvelopeInternal());
					addSegments(ls, WaterSide.RIGHT);
				} else {
					logger.warn("non-Linestring encountered in coastlines");
				}
			}
		}
		
		dm.setWorkingExtent(overallEnv);

		assignIds();
		for(HydroChainLink link : segmentMap.values()) {
			edges.add(link.edge);
		}
		dm.deleteECatchments(EcType.WATER);
		dm.writeWaterbodies(waterbodies);
		dm.writeHydroEdges(edges);
		stats.reportStatus(logger, "Hydro edges loaded and drainageIds assigned.");
	}
	
	public void addSegments(LineString line, WaterSide side) {
		Coordinate[] pts = line.getCoordinates();
		for (int i = 0; i < pts.length - 1; i++) {
			LineSegment seg = new LineSegment(pts[i], pts[i + 1]);
			LineSegment normSeg = new LineSegment(pts[i], pts[i + 1]);
			normSeg.normalize();
			HydroChainLink link = segmentMap.get(normSeg);
			if (link != null) {
				// this is a duplicate segment, that means it is a water on both sides 
				link.edge.setWaterSide(WaterSide.BOTH);
				//segmentMap.remove(normSeg);
				// the place where the shared edge meets the water on 1-side edges counts as a node
				// ie. we want a watershed boundary at these locations
				nodeSet.add(normSeg.p0);
				nodeSet.add(normSeg.p1);
			} else {
				segmentMap.put(normSeg, new HydroChainLink(seg, new HydroEdge(seg.toGeometry(gf), null, side)));
			}
		}
	}
	
	public void assignIds() {
		// count edges incident to coordinates
		Map<Coordinate,Integer> coordCountMap = new HashMap<Coordinate,Integer>();
		for(HydroChainLink link : segmentMap.values()) {
			for(Coordinate c : new Coordinate[] {link.seg.p0, link.seg.p1}) {
				if(!nodeSet.contains(c)) {
					coordCountMap.merge(c, 1, Integer::sum);
				}
			}
		}
		// any coordinate with more than 2 incident lines should be a node 
		for(Entry<Coordinate, Integer> entry: coordCountMap.entrySet()) {
			if(entry.getValue() > 2) {
				nodeSet.add(entry.getKey());
			}
		}
		// use a map of coordinates to link up HydroChainLinks
		Map<Coordinate,HydroChainLink> coordMap = new HashMap<Coordinate,HydroChainLink>();
		for(HydroChainLink link : segmentMap.values()) {
			for(Coordinate c : new Coordinate[] {link.seg.p0, link.seg.p1}) {
				if(!nodeSet.contains(c)) {
					HydroChainLink joiner = coordMap.get(c);
					if(joiner == null) {
						coordMap.put(c, link);
					} else {
						link.connect(joiner);
					}
				}
			}
		}
		for(HydroChainLink link : segmentMap.values()) {
			if(link.edge.getDrainageID() == null 
					&& link.edge.getWaterSide() != WaterSide.BOTH) {
				HydroChainLink.assignDrainageId(link, nextDrainageId++);
			}
		}
	}

	public List<HydroEdge> getEdges() {
		return edges;
	}
	
}
	
class HydroChainLink {
	LineSegment seg;
	HydroEdge edge;
	private HydroChainLink left, right;
	
	HydroChainLink(LineSegment seg, HydroEdge edge) {
		this.seg = seg;
		this.edge = edge;
	}
	
	static void assignDrainageId(HydroChainLink firstLink, Integer id) {
		Deque<HydroChainLink> stack = new ArrayDeque<HydroChainLink>();
		stack.addFirst(firstLink);
		while(!stack.isEmpty()) {
			HydroChainLink link = stack.removeFirst();
			if(id.equals(link.edge.getDrainageID())) continue;
			Assert.isTrue(link.edge.getDrainageID() == null);
			link.edge.setDrainageID(id);
			if(link.left != null) {
				stack.add(link.left);
			} 
			if(link.right != null) {
				stack.add(link.right);
			}
		}
	}
	
	void connect(HydroChainLink joiner) {
		if(seg.p0.equals(joiner.seg.p0)) {
			Assert.isTrue(left == null);
			left = joiner;
			Assert.isTrue(joiner.left == null);
			joiner.left = this;
		} else if(seg.p0.equals(joiner.seg.p1)) {
			Assert.isTrue(left == null);
			left = joiner;
			Assert.isTrue(joiner.right == null);
			joiner.right = this;
		}  else if(seg.p1.equals(joiner.seg.p0)) {
			Assert.isTrue(right == null);
			right = joiner;
			Assert.isTrue(joiner.left == null);
			joiner.left = this;
		} else if(seg.p1.equals(joiner.seg.p1)) {
			Assert.isTrue(right == null);
			right = joiner;
			Assert.isTrue(joiner.right == null);
			joiner.right = this;
		}
	}
	
	public String toString() {
		return edge.getDrainageID() + ":" + seg.toString();
	}
}
