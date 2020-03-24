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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.polygonize.Polygonizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.util.PolygonEdgeMatcher;
import net.refractions.chyf.util.ProcessStatistics;
import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.WaterSide;
import net.refractions.chyf.watershed.model.WatershedBoundaryEdge;

public class WatershedBoundaryMerger {
	static final Logger logger = LoggerFactory.getLogger(WatershedBoundaryMerger.class);
	
	private DataManager dm;
	
	public WatershedBoundaryMerger(DataManager dm) {
		this.dm = dm;
	}
	
	@SuppressWarnings("unchecked")
	public List<Catchment> merge() {
		ProcessStatistics stats = new ProcessStatistics();
		stats.reportStatus(logger, "starting watershed boundary merge process");
		List<Catchment> waterbodies = dm.getWaterbodies();
		List<HydroEdge> edges = dm.getHydroEdges(null);
		List<WatershedBoundaryEdge> boundaries = dm.getWatershedBoundaries();
		
		// build an index of all waterbodies
		STRtree wbIndex = new STRtree();
		for(Catchment c : waterbodies) {
			Polygon p = c.getPoly();
			wbIndex.insert(p.getEnvelopeInternal(), p);
		}

		// find the max drainageId
		int maxDrainageId = 0;		
		for(WatershedBoundaryEdge boundary : boundaries) {
			maxDrainageId = Math.max(maxDrainageId, boundary.getRegionID(WatershedBoundaryEdge.LEFT));
			maxDrainageId = Math.max(maxDrainageId, boundary.getRegionID(WatershedBoundaryEdge.RIGHT));
		}
		for(HydroEdge edge : edges) {
			maxDrainageId = Math.max(maxDrainageId, Optional.ofNullable(edge.getDrainageID()).orElse(0));
		}
		
		// create an array of lists, index by drainageId
		List<Object>[] boundaryGroups = new ArrayList[maxDrainageId+1];
		for(int i = 0; i < maxDrainageId+1; i++) {
			boundaryGroups[i] = new ArrayList<Object>();
		}

		stats.reportStatus(logger, "Grouping watershed boundary edges");

		// loop over boundary edges
		for(WatershedBoundaryEdge boundary : boundaries) {
			// ignore boundaries that are inside waterbodies
			List<Polygon> nearWaterbodies = wbIndex.query(boundary.getGeometry().getEnvelopeInternal());
			boolean isInWaterbody = false;
			for(Polygon waterbody : nearWaterbodies) {
				if(waterbody.contains(boundary.getGeometry())) {
					isInWaterbody = true;
					break;
				}
			}
			if(isInWaterbody) {
				continue;
			}
			
			// add each edge to the appropriate boundaryGroups for left and right
			int leftId = boundary.getRegionID(WatershedBoundaryEdge.LEFT);
			if(leftId >= 0) {
				boundaryGroups[leftId].add(boundary);
			}
			int rightId = boundary.getRegionID(WatershedBoundaryEdge.RIGHT);
			if(rightId >= 0) {
				boundaryGroups[rightId].add(boundary);
			}
		}	
		
		// loop over HydroEdge
		for(HydroEdge edge : edges) {
			// skip edges that don't have water on exactly one side
			if(WaterSide.NEITHER == edge.getWaterSide()
					|| WaterSide.BOTH == edge.getWaterSide()) {
				continue;
			}
			boundaryGroups[edge.getDrainageID()].add(edge);
			
		}
		
		int noEdgeCount = 0;
		
		stats.reportStatus(logger, "Polygonizing watershed boundary edges");

		List<Catchment> watersheds = new ArrayList<Catchment>(maxDrainageId+1);
		// loop over drainageID map and join edges into polygons
		for(int drainageId = 0; drainageId < maxDrainageId+1; drainageId++) {
			if(boundaryGroups[drainageId].isEmpty()) {
				//logger.warn("No edges for drainageId: " + drainageId);
				noEdgeCount++;
				continue;
			}
			// use a map to create a list of duplicate or near-duplicate lines
			// lines are dups or near-dups if either the first two or last two coordinates are the same
			HashMap<LineSegment,LineString> endSegs = new HashMap<LineSegment,LineString>(2*boundaryGroups[drainageId].size());
			List<List<LineString>> dups = new ArrayList<List<LineString>>();
			HashSet<LineString> lines = new HashSet<LineString>(boundaryGroups[drainageId].size());
			List<HydroEdge> hydroEdges = new ArrayList<HydroEdge>();
			for(Object edge : boundaryGroups[drainageId]) {
				LineString line = null;
				if(edge instanceof HydroEdge) {
					line = ((HydroEdge)edge).getLine();
					hydroEdges.add((HydroEdge)edge);
				} else if( edge instanceof WatershedBoundaryEdge) {
					line = ((WatershedBoundaryEdge)edge).getGeometry();
				}
				LineSegment startSeg = new LineSegment(line.getCoordinateN(0),line.getCoordinateN(1));
				startSeg.normalize();
				LineSegment endSeg = new LineSegment(line.getCoordinateN(line.getNumPoints()-1),line.getCoordinateN(line.getNumPoints()-2));
				endSeg.normalize();
				// look for a duplicate using the start seg
				LineString myDup = endSegs.get(startSeg);
				// if we didn't find one and our line is longer than 2 coords
				if(myDup == null && line.getNumPoints() > 2) {
					// look for a duplicate using the end seg
					myDup = endSegs.get(endSeg);
				}
				// if we found a dup
				if(myDup != null) {
					// if the two lines are not exactly equal
					if(!myDup.norm().equals(line.norm())) {
						// add the two lines to dup list, and remove the original from the main line set
						lines.remove(myDup);
						dups.add(List.of(myDup,line));
					}
				} else {
					// no dups, add this line to the endSegs for each end and to the main line set
					endSegs.put(startSeg, line);
					endSegs.put(endSeg, line);
					lines.add(line);
				}
				//System.out.println(line.toText());
			}
			Polygonizer polygonizer = new Polygonizer();
			List<Polygon> polys = Collections.EMPTY_LIST;
			
			// if we have dups, try one from each pair of dups until something works
			if(dups.size() > 0) {
				// bitSet is used to iterate over the choices, 
				// and each bit specifies which dup to try from each pair
				for(int bitSet = 0; bitSet < Math.pow(2, dups.size()); bitSet++) {
					polygonizer = new Polygonizer();
					polygonizer.add(lines);
					for(int dupSet = 0; dupSet < dups.size(); dupSet++) {
						polygonizer.add(dups.get(dupSet).get((bitSet >> dupSet) & 1));	
					}
					polys = (List<Polygon>)polygonizer.getPolygons();
					if(polys.size() > 0) break;
					
				}
			} else {
				polygonizer = new Polygonizer();
				polygonizer.add(lines);
				polys = (List<Polygon>)polygonizer.getPolygons();
			}
			
			// if we have multiple polys, keep the one that touches a hydro edge
			if(polys.size() > 1) {
				for(int polyIdx = 0; polyIdx < polys.size(); polyIdx++) {
					Polygon p = polys.get(polyIdx);
					boolean touchesHydroEdge = false;
					for(HydroEdge edge : hydroEdges) {
						Coordinate c0 = edge.getLine().getCoordinateN(0);
						Coordinate c1 = edge.getLine().getCoordinateN(1);
						int result = PolygonEdgeMatcher.compare(p, c0, c1);
						// we don't want polys on the water side of HydroEdges
						if((edge.isWaterLeft() && result == -1) 
								|| (edge.isWaterRight() && result == 1)) {
							polys.remove(polyIdx);
							//System.out.println(p.toText());
							polyIdx--;
							touchesHydroEdge = false;
							break;
						} else if(result != 0) {
							touchesHydroEdge = true;
						}
					}
					// in multi-poly cases, a poly that touches a hydro edge is the right one
					if(touchesHydroEdge) {
						polys = List.of(p);
					}
				}
			}
			
			// if we still have multiple polys, keep the larger one (likely a donut situation)
			Polygon largest = null;
			if(polys.size() > 1) {
				for(Polygon p : polys) {
					if(largest == null || p.getEnvelopeInternal().contains(largest.getEnvelopeInternal())) { 
						largest = p;
					}
				}
				polys = List.of(largest); 
			}
			
			if(!polygonizer.getDangles().isEmpty() || !polygonizer.getCutEdges().isEmpty() || !polygonizer.getInvalidRingLines().isEmpty() || polys.size() != 1) {
				logger.warn("error merging polygon for drainageId: " + drainageId);
			}
			// if there are no hydroEdges in the boundary it must be a reach catchment (1), otherwise it is a bank catchment(2)
			for(Polygon poly : polys) {
				watersheds.add(new Catchment(UUID.randomUUID().toString(), hydroEdges.isEmpty() ? 1 : 2, poly));
			}
		}
		stats.reportStatus(logger, "watershed boundary merging complete");
		if(noEdgeCount > 0) {
			stats.reportStatus(logger, "Warning:" + noEdgeCount + " drainageIds had no edges to merge; block processing is likely incomplete");
		}

		return watersheds;
	}

}
