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
package net.refractions.chyf.directionalize.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfAngle;
import net.refractions.chyf.datasource.ChyfDataSource.DirectionType;

/**
 * Directionalizing a graph by finding paths between input and output nodes
 * then paths between existing paths.
 * 
 * @author Emily
 *
 */
public class PathDirectionalizer {

	private static final Logger logger = LoggerFactory.getLogger(PathDirectionalizer.class.getCanonicalName());
	
	private ChyfAngle angle;
	public PathDirectionalizer(ChyfAngle angle) {
		this.angle = angle;
	}
	
	/**
	 * Directionalizes a subgraph using paths.  Source and sink nodes
	 * are determined by analyzing the nodes in the subgraph and the edges
	 * that link to those nodes that are not a part of the subgraph.  These edges
	 * provide a reference to the bigger graph.
	 */
	public void directionalize(DGraph subGraph) throws Exception {
		
		Set<DNode> sinks = new HashSet<>();
		Set<DNode> sources = new HashSet<>();
		
		for (DNode n : subGraph.nodes) {
			if (n.isSink()) {
				//existing sink is a sink
				sinks.add(n);
				continue;
			}
			
			//find the edge not in the graph
			for (DEdge e : n.getEdges()) {
				if (!subGraph.getEdges().contains(e)) {
					if (e.getNodeA() == subGraph.collapseNode) {
						sinks.add(n);
					}else {
						sources.add(n);
					}
				}
			}	
			if (sinks.contains(n)) continue;
			
			boolean source = true;
			for (DEdge e : n.getEdges()) {
				if (e.getDType() == DirectionType.UNKNOWN) {
					source = false;
				}else {
					if (e.getNodeA() != n) {
						source = false;
					}
				}
			}
			if (source) {
				sources.add(n);
			}

		};
		
		if (sinks.isEmpty()) {
			throw new Exception("Partitioning subgraphs created a subgraph with no sinks: " + subGraph.edges.get(0).toString());
		}
		
		if (sources.isEmpty()) {
			//loop cases 
			
			//find a known direction edge we can use 
			Set<DNode> donotuse = new HashSet<>();		
			DNode source = null;
			//first loop for known edges that can be used as a source
			for (DNode n : subGraph.nodes) {
				//if one of the edges is know and flows out of this node
				for(DEdge e : n.getEdges()) {
					if (source == null && e.getDType() == DirectionType.KNOWN && e.getNodeA() == n && !sinks.contains(n)) {
						source = n;
					}else {
						if (e.getDType() == DirectionType.KNOWN && e.getNodeB() == n) {
							//caanot use this node
							source = null;
							donotuse.add(n);
							break;
						}
					}
					
				}
				if (source != null) break;
			}
			//everything is unknown, just pick something
			if (source == null) {
				for (DNode n : subGraph.nodes) {
					//pick degree2 nodes first as these are likely where banks hook in
					if (!donotuse.contains(n) && !sinks.contains(n) && n.getDegree() == 2) {
						sources.add(n);
					}
				}
				if (sources.isEmpty()) {
					for (DNode n : subGraph.nodes) {		
						if (!donotuse.contains(n) && !sinks.contains(n)) {
							sources.add(n);
							break;
						}
					}	
				}
			}else {
				sources.add(source);
			}
			
			//push outflow from this node
			DNode src = sources.iterator().next();
			for (DEdge out : src.getEdges()) {
				if (out.getNodeA() != src) {
					out.flip();
				}else {
					out.setKnown();
				}
			}
			logger.warn("Loop case detected where a random source node will be created. Source node created at: " + src.toString());
		}
		
		dir(subGraph, sinks, sources);
	}
	
	public void dir(DGraph graph, Set<DNode> sinks, Set<DNode> sources) throws Exception{
		//set of paths created
		List<DPath> paths = new ArrayList<>();

		//order sources by distance to sink; max first
		ArrayList<DNode> ordered = new ArrayList<>(sources);
		sources.forEach(e->{
			double max = 0;
			for (DNode s : sinks) {
				double t = e.getCoordinate().distance(s.getCoordinate());
				if (t>max) max = t;	
			}
			e.pathdistance = max;
		});
		ordered.sort((a,b)-> -1* Double.compare(a.pathdistance, b.pathdistance));
		
		//find shortest path from every source to every sink
		for (DNode sourcenode : ordered) {
			DPath path = PathFinder.findPath(sourcenode,  sinks, graph);
			

			//directionalize path
			if (path != null) {
				directionalizePath(path);				
				paths.add(path);
			}else {
				logger.error("Path shouldn't be null @ " + sourcenode.toString());
			}
		}

		//for remaining edges; find shortest path from that edge to sink
		//lets process known edges before unknown
		
		//this fail set keeps track of edges that we couldn't 
		//find a path for (only happens when some edges) are already
		//directionalized, we don't want to retry them
		Set<DEdge> fail = new HashSet<>();
		
		while(true) {
			//find edge to visit next
			DEdge toprocess = findUnvisitedEdge(graph, paths, sinks, fail);
			if (toprocess == null) break;
			
			
			//create a path
			DPath temp = PathFinder.findPath(toprocess.getNodeB(), sinks, graph);
			
			if (temp == null) {
				//not path found
				toprocess.resetKnown();
				fail.add(toprocess);
				continue;
			}
			//add start node/edge to path
			temp.nodes.add(0, toprocess.getNodeA());
			temp.edges.add(0, toprocess);
			directionalizePath(temp);
			
			boolean canflip = true;
			//check for cycle and flip is necessary
			if (cycleCheck(temp, graph)) {
				canflip = false;
				//flip
				Collections.reverse(temp.edges);
				Collections.reverse(temp.nodes);
				for (DEdge e : temp.edges) e.flip();
				if (cycleCheck(temp, graph)) {
					throw new Exception("Cannot add path as both directions for this path creates a cycle. Start Edge: " + temp.edges.get(0).toString());
				}
			}
			
			if (canflip) {
				//can go either way without cycle
				//lets look at angles (both start and end) and see if other way might be better
				for (DEdge e : temp.edges) {
					if (e.getRawType() == DirectionType.KNOWN) {
						canflip = false;
						break;
					}
				}
				
				if (canflip) {
					//in angle
					DNode start = temp.nodes.get(0);
					DEdge first = temp.edges.get(0);
					//find a known edge that flows into start
					Double inangle = null;
					for (DEdge in : start.getEdges()) {
						Coordinate c1 = null;
						Coordinate c2 = start.getCoordinate();
						Coordinate c3 = first.getNextToA();
						if (in.getDType() == DirectionType.KNOWN && in.getNodeB() == start) {
							c1 = in.getNextToB();
						}else if (!graph.edges.contains(in) && in.getDType() == DirectionType.KNOWN) {
							
							if (in.getNodeB() == graph.collapseNode) {
								c1 = in.getNextToB();
							}else if (in.getNodeA() == graph.collapseNode) {
								c1 = in.getNextToA();
							}else {
								throw new IllegalStateException();
							}
						}
						if (c1 != null) {
							double a = findAngle(c1, c2, c3);
							if ( inangle == null || a < inangle) inangle = a;
						}
					}
					
					//out angle
					DNode enode =  temp.nodes.get(temp.nodes.size() - 1);
					DEdge eedge =  temp.edges.get(temp.edges.size() - 1);
					Double outangle = null;
					for (DEdge in : enode.getEdges()) {
						Coordinate c1 = null;
						Coordinate c2 = enode.getCoordinate();
						Coordinate c3 = eedge.getNextToB();
						if (!temp.edges.contains(in) && in.getDType() == DirectionType.KNOWN && in.getNodeB() == enode) {
							//compute angle
							c1 = in.getNextToB();
							
						}else if (!graph.edges.contains(in) && in.getDType() == DirectionType.KNOWN) {
							
							if (in.getNodeB() == graph.collapseNode) 
								c1= in.getNextToB();
							else if (in.getNodeA() == graph.collapseNode) 
								c1= in.getNextToA();
							else 
								throw new IllegalStateException();
						}
						if (c1 != null) {
							double a = findAngle(c1,c2,c3);
							if (outangle == null || a > outangle) outangle = a;
						}
					}
				  if ((outangle != null && inangle != null &&
						  Math.abs(outangle - inangle) > 30 * Math.PI / 180 &&
						  Math.abs(Math.PI*2 - outangle) < Math.abs(Math.PI*2 - inangle) ) 
						  ) {
						Collections.reverse(temp.edges);
						Collections.reverse(temp.nodes);
						for (DEdge e : temp.edges) e.flip();
						
						if (cycleCheck(temp, graph)) {
							Collections.reverse(temp.edges);
							Collections.reverse(temp.nodes);
							for (DEdge e : temp.edges) e.flip();
						}else {
							System.out.println("flipped based on END angle:" + temp.nodes.get(0).toString());
							System.out.println(Math.toDegrees(inangle) + ":" + Math.toDegrees(outangle));

						}
					}
				}
			}

			paths.add(0, temp);
		}
			
		
		//check all edges in graph are part of path 
		for (DEdge e : graph.edges) {
			if (!e.pathedge) {
				throw new Exception("Not all edges in the subgraph were directionalized: " + e.toString());
			}
		}
	}
	
	private Double findAngle(Coordinate c1, Coordinate c2, Coordinate c3) throws Exception {
		double a = angle.angle(c1,  c2,  c3);
//		if (a < 0) a+=Math.PI;
//		if (a > Math.PI) a=a-Math.PI;
		return a;
	}
	
	/**
	 * Finds an edge that hasn't been processed and is of the given type.  The edge
	 * source node must be on an existing path
	 * @param paths
	 * @param dtype
	 * @return
	 */
	private static DEdge findUnvisitedEdge(DGraph graph, List<DPath> paths, Set<DNode> sinks, Set<DEdge> failed) throws Exception{
		DEdge undir = null;
		boolean needsflip = false;
		for (DPath path : paths) {
			for (DNode n : path.nodes) {
				if (sinks.contains(n)) continue;
				
				for (DEdge e : n.getEdges()) {
					if (e.pathedge) continue;
					if (!graph.edges.contains(e)) continue;
					
					if (failed.contains(e)) continue;
					
					if (e.getDType() == DirectionType.KNOWN && e.getNodeA() != n) continue;
					
					if (e.getDType() == DirectionType.KNOWN) return e;
					
					if (undir == null) {
						undir = e;
						needsflip = undir.getNodeB() == n;
					}
					
				}
			}
		}
		if (undir != null) {
			if (needsflip) {
				undir.flip();
			}else {
				undir.setKnown();
			}
			return undir;
		}
		
		//check the sink nodes; they could possibly be a source node in the case of 
		//multiple sinks (where an edge connects the two sinks)
		for (DNode n : sinks) {
			for (DEdge e : n.getEdges()) {
				if (e.pathedge) continue;
				if (!graph.edges.contains(e)) continue;
				if (failed.contains(e)) continue;
				if (e.getDType() == DirectionType.KNOWN && e.getNodeA() != n) continue;
				if (e.getDType() == DirectionType.KNOWN) return e;
				if (undir == null) {
					undir = e;
					needsflip = undir.getNodeB() == n;
				}
				
			}
		}
		if (undir != null) {
			if (needsflip) {
				undir.flip();
			}else {
				undir.setKnown();
			}
			return undir;
		}
		
		return null;
	}
	
	private void directionalizePath(DPath path) throws Exception{
		for (int k = 0; k < path.edges.size(); k ++) {
			if (path.edges.get(k).getNodeA() != path.nodes.get(k)) {
				path.edges.get(k).flip();
			}else {
				path.edges.get(k).setKnown();
			}
			path.edges.get(k).pathedge = true;
		}
	}
	
	/**
	 * Checks to see if adding this path to the graph
	 * results in a cycle added to the graph.  Starts
	 * at the end of the path, follows edges downstream
	 * and see if we visit the start node in the path 
	 * 
	 * @param path
	 * @param graph
	 * @return
	 */
	private boolean cycleCheck(DPath path, DGraph graph) {
		DNode start = path.nodes.get(0);
		DNode end = path.nodes.get(1);
		
		HashSet<DNode> visited = new HashSet<>();
		
		List<DNode> tocheck = new ArrayList<>();
		tocheck.add(end);
		while(!tocheck.isEmpty()) {
			DNode c = tocheck.remove(0);
			if (c == start) return true;
			if (visited.contains(c)) continue;
			
			visited.add(c);
			for (DEdge d : c.getEdges()) {
				if (!graph.edges.contains(d)) continue;
				if (d.getDType() == DirectionType.KNOWN) {
					if (d.getNodeA() == c) {
						tocheck.add(d.getNodeB());
					}
				}
			}
		}
		return false;
		
	}
	
	
}
