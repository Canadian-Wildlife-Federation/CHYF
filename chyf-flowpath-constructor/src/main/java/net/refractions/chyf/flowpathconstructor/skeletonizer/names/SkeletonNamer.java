/*
 * Copyright 2021 Canadian Wildlife Federation
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
package net.refractions.chyf.flowpathconstructor.skeletonizer.names;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfLogger;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.ConstructionPoint;

/**
 *
 * Tool to apply names to individual waterbody based on construction point names.
 * 
 * @author Emily
 *
 */
public enum SkeletonNamer {

	INSTANCE;
	
	static final Logger logger = LoggerFactory.getLogger(SkeletonNamer.class.getCanonicalName());

	private GeometryFactory gf = new GeometryFactory();
	
	private SkeletonNamer() { }
	

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
	public HashMap<FeatureId, String[]> nameFlowpaths(Collection<LineString> skeletons, 
			List<ConstructionPoint> cpoints, Polygon waterbody) throws Exception {
		
		//build a graph from the linestrings
		NGraph graph = new NGraph();

		for (LineString ls : skeletons) {
			Object[] data = (Object[]) ls.getUserData();
			
			FeatureId fid = (FeatureId) data[0];
			RankType type = (RankType) data[1];
			
			graph.addEdge(ls,  fid, type);
		}
		
		//process only name1 names
		//create a map between name1 and other names
		//we will use this later to apply other names based on name1 names
		
		//assign names to graph
		
		HashMap<Integer, Set<String>> allnameids = new HashMap<>();
		
		for (ConstructionPoint p : cpoints) {
			if (p.getNameIds() == null) continue;
			for (NNode n : graph.getNodes()) {
				if (n.getCoordinate().equals2D(p.getCoordinate())) {
					for (String[] s : p.getNameIds()) {
						for (int i = 0; i < s.length; i ++) {
							if (s[i] == null) continue;
							
							if (!allnameids.containsKey(i)) allnameids.put(i,  new HashSet<>());
							allnameids.get(i).add(s[i]);
							n.addName(i, s[i]);
						}
					}
				}
			}
		}

		for (int index = 0; index < allnameids.size(); index ++) {
			//load river names from waterbody
			String[] wbNames = (String[]) waterbody.getUserData();
			
			Set<String> nameids = allnameids.get(index);
			
			for (String name : nameids) {
				//count innode and out
				
				int incnt = 0;
				int outcnt = 0;
				List<NNode> inNode = new ArrayList<>();
				List<NNode> outNode = new ArrayList<>();
				for (NNode n : graph.getNodes()) {
					if (n.getInDegree() == 0 && n.getNames(index).contains(name)) {
						//headwater
						incnt ++;
						inNode.add(n);
					}
					if (n.getOutDegree() == 0 && n.getNames(index).contains(name)) {
						outcnt ++;
						outNode.add(n);
					}
				}
				
				
				List<NEdge> path = null;
				
				boolean wbNameMatch = false;
				boolean wbHasName = false;
				if (wbNames != null && wbNames.length > 0 
						&& wbNames[index] != null && !wbNames[index].trim().isBlank()) {
					String s = wbNames[index];
					if (s != null && s.equals(name)) wbNameMatch = true;
					if (s != null) wbHasName = true;
				}
				
				
				if (incnt == 0 && outcnt == 0 && wbNameMatch) {
					//find longest primary path from input to output
//					ChyfLogger.INSTANCE.logWarning(ChyfLogger.Process.NAMING, "NAME: CASE 1", waterbody.getInteriorPoint());
					path = findLongestPrimaryPath(graph);
					
				}else if (incnt == 1 && outcnt == 0) {
					//no output
					//if waterbody has the same name then push down primary path until output
					if (wbNameMatch) {
						//ChyfLogger.INSTANCE.logWarning("NAME: CASE 2-A", ((new GeometryFactory()).createPoint(inNode.get(0).getCoordinate())));
						path =  findPrimaryPathDown(inNode.get(0), null);
					}
				}else if (incnt == 0 && outcnt == 1) {
					if (wbNameMatch) {
						//start at output, walk up to all inputs picking longest path
						List<NEdge> longestpath = null;
						double pathlength = -1;
						for (NNode n : graph.getNodes()) {
							if (n.getInDegree() == 0) {
								List<NEdge> edges = findPath(n, outNode.get(0), graph);
								if (edges != null) {
									double length = 0;
									for (NEdge e : edges) length += e.getLength();
									if (length > pathlength) {
										longestpath = edges;
										pathlength = length;
									}
								}
							}
						}
						path = longestpath;
					}
				}else if (incnt == 1 && outcnt == 1) {
					//search for primary path between input and output
					path =  findPrimaryPathDown(inNode.get(0), outNode.get(0));
					if (path == null) {
						//search for any path
						path = findPath(inNode.get(0), outNode.get(0), graph);
						if (path == null) {
							//likely a side channel
							String msg = "Could not find any path between input and output points for name id (?side channel): " + name;
							ChyfLogger.INSTANCE.logWarning(ChyfLogger.Process.NAMING, msg, toMultiPoint(inNode, outNode, waterbody.getInteriorPoint()));
							logger.warn(msg);
						}else {
							
							String msg = "Path for name contains secondary flowpaths between input and output points for name id: " + name;
							List<Coordinate> pnts = new ArrayList<>();
							pnts.add(waterbody.getInteriorPoint().getCoordinate());
							for (NEdge e : path) if (e.getRank() == RankType.SECONDARY) pnts.add(e.getFromCoordinate());
							
							ChyfLogger.INSTANCE.logWarning(ChyfLogger.Process.NAMING, msg, gf.createMultiPointFromCoords(pnts.toArray(new Coordinate[pnts.size()])));
							logger.warn(msg);
						}
					}
	
					
					if (path != null && !wbNameMatch && wbHasName) {
						//waterbody has a different name then the input/output name
						String msg = "Waterbody has different name then input/output name.";
						ChyfLogger.INSTANCE.logWarning(ChyfLogger.Process.NAMING, msg, waterbody.getInteriorPoint());
						logger.warn(msg);
	
					}
				}else if (incnt > 1 && outcnt == 0){
					String msg = name + " has multiple inputs (" + incnt + ") and outputs (" + outcnt + ")";
					ChyfLogger.INSTANCE.logWarning(ChyfLogger.Process.NAMING, msg, toMultiPoint(inNode, outNode, waterbody.getInteriorPoint()));
					logger.warn(msg);
				}else if (incnt == 0 && outcnt > 1 ){
					String msg = name + " has multiple inputs (" + incnt + ") and outputs (" + outcnt + ")";
					ChyfLogger.INSTANCE.logWarning(ChyfLogger.Process.NAMING, msg, toMultiPoint(inNode, outNode, waterbody.getInteriorPoint()));
					logger.warn(msg);
				}else if (incnt > 1 && outcnt > 1) {
					//multiple input and outputs					
					List<NEdge> longestpath = null;
					double distance = -1;
					
					//this searches for only primary paths
					for (NNode in : inNode) {
						for (NNode out : outNode) {
							List<NEdge> edges = findPrimaryPathDown(in, out);
							if (edges == null) continue;
							double pd = 0;
							for (NEdge e : edges) pd += e.getLength();
							if (pd > distance) {
								longestpath = edges;
								distance= pd;
							}
						}
					}
					if (longestpath != null) {
						path = longestpath;
					}else {
						//search secondary paths
						for (NNode in : inNode) {
							for (NNode out : outNode) {
								List<NEdge> edges = findPath(in, out, graph);
								if (edges == null) continue;
								double pd = 0;
								for (NEdge e : edges) pd += e.getLength();
								if (pd > distance) {
									longestpath = edges;
									distance= pd;
								}
							}
						}
					}
					if (path == null) {
						String msg = name + " has multiple inputs (" + incnt + ") and outputs + (" + outcnt + "). Could not find any path between any of the points.";
						logger.warn(msg);
						ChyfLogger.INSTANCE.logWarning(ChyfLogger.Process.NAMING, msg, toMultiPoint(inNode, outNode, waterbody.getInteriorPoint()));
					}else {
						path = longestpath;
						String msg = name + " has multiple inputs (" + incnt + ") and outputs + (" + outcnt + "). A path was picked between any of these points.";
						logger.warn(msg);
						ChyfLogger.INSTANCE.logWarning(ChyfLogger.Process.NAMING, msg, toMultiPoint(inNode, outNode, waterbody.getInteriorPoint()));
					}
				}
				
				//name edges
				if (path != null) for (NEdge e : path) e.addName(index, name);	
			}
		}
		
		

		HashMap<FeatureId, String[]> names = new HashMap<>();
		for (NEdge e : graph.getEdges()) {
			if (!e.hasNames()) {
				names.put(e.getID(), null);
			}else {		
				String[] n1 = new String[allnameids.size()];
				for (int i = 0; i < allnameids.size(); i ++) {
					n1[i] = e.getNames(i);
				}
				names.put(e.getID(), n1);
			}
		}
		return names;
	}
	
	private List<NEdge> findLongestPrimaryPath(NGraph graph){
		List<NNode> startNodes = new ArrayList<>();
		for (NNode n : graph.getNodes()) {
			if (n.getInDegree() == 0) startNodes.add(n);
		}
		
		List<NEdge> longestpath = new ArrayList<>();
		double pathlength = 0;
		
		for (NNode start : startNodes) {
			
			List<NEdge> path = new ArrayList<>();
			double length = 0;
			while(start != null) {
				NNode next = null;
				for (NEdge out : start.getOutEdges()) {
					if (out.getRank() == RankType.SECONDARY) continue;
					path.add(out);
					length += out.getLength();
					next = out.getToNode();
					break;
				}
				start = next;
			}
			
			if (length > pathlength) {
				longestpath = path;
				pathlength = length;
			}
		}
		
		return longestpath;
	}
	
	private Geometry toMultiPoint(List<NNode> nodes, List<NNode> nodes2, Point pnt) {
		List<Coordinate> cs = new ArrayList<>();
		if (nodes != null) nodes.forEach(n->cs.add(n.getCoordinate()));
		if (nodes2 != null) nodes2.forEach(n->cs.add(n.getCoordinate()));
		if (pnt != null) cs.add(pnt.getCoordinate());
		return gf.createMultiPointFromCoords(cs.toArray(new Coordinate[cs.size()]));
	}

	private List<NEdge> findPrimaryPathDown(NNode start, NNode end){
		
		List<NEdge> path = new ArrayList<>();
		
		boolean found = false;
		NNode working = start;
		while(working != null) {
			NNode next = null;
			for (NEdge out : working.getOutEdges()) {
				if (out.getRank() == RankType.SECONDARY) continue;
				path.add(out);
				next = out.getToNode();
			}
			if (end != null && next == end) {
				found = true;
				break;
			}
			working = next;
		}
		if (end == null) return path;
		if (found) return path;
		return null;
	}
	
	public static List<NEdge> findPath(NNode source, NNode sink, NGraph graph) {
		
		HashMap<NNode, Object[]> prev = new HashMap<>();
		List<NNode> tovisit = new ArrayList<>();

		for (NNode n : graph.nodes) {
			n.pathdistance = Double.MAX_VALUE;
			n.pathvisited = false;
		}
		
		source.pathdistance = 0;
		tovisit.add(source);
		
		while(!tovisit.isEmpty()) {
			NNode max = null;
			//find the path nearest node
			for (NNode n : tovisit) {
				if (max == null || n.pathdistance < max.pathdistance) {
					max = n;
				}
			}

			if (sink.equals(max) ) {
				//stop
				//follow prev to source to get path
				List<NEdge> path = new ArrayList<>();
				NNode c = max;
				Object[] pv = prev.get(c);
				while(pv != null) {
					path.add(0, (NEdge)pv[1]);	
					pv = prev.get((NNode)pv[0]);	
				}
				
				return path;
			}
			
			tovisit.remove(max);
			max.pathvisited = true;
			//note: graph edges are sorted primary first so this
			//this prioritize primary flowpaths
			for (NEdge e : max.getOutEdges()) {
				NNode o = e.getToNode();
				if (o.pathvisited) continue;
				
				double alt = max.pathdistance + e.getLength();
				if (alt < o.pathdistance) {
					o.pathdistance = alt;
					prev.put(o, new Object[] {max, e});
					tovisit.add(o);
				}
			}
		}
		return null;
		
	}
}
