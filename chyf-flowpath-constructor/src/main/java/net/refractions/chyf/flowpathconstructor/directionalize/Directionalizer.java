package net.refractions.chyf.flowpathconstructor.directionalize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.geotools.filter.identity.FeatureIdImpl;
import org.locationtech.jts.geom.Coordinate;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.DirectionType;
import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.flowpathconstructor.ChyfProperties;
import net.refractions.chyf.flowpathconstructor.ChyfProperties.Property;
import net.refractions.chyf.flowpathconstructor.datasource.ChyfAngle;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.BridgeFinder;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.DEdge;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.DGraph;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.DNode;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.DPath;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.EdgeInfo;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.Partition;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.PathDirectionalizer;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.SubGraph;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.TreeDirection;

/**
 * Engine for directionalizing a flow network graph
 * 
 * @author Emily
 *
 */
public class Directionalizer {

	private static final Logger logger = LoggerFactory.getLogger(Directionalizer.class.getCanonicalName());

	private Set<FeatureId> toflip = new HashSet<>();
	private Set<FeatureId> processed  = new HashSet<>();
	
	private ChyfAngle angleComputer = null;
	private ChyfProperties prop;
	
	public Directionalizer(CoordinateReferenceSystem sourceCRS, ChyfProperties prop) {
		angleComputer = new ChyfAngle(sourceCRS);
		this.prop = prop;
	}
	
	/**
	 * features to flip
	 * @return
	 */
	public Set<FeatureId> getFeaturesToFlip(){
		return toflip;
	}
	
	/**
	 * all processed features
	 * @return
	 */
	public Set<FeatureId> getProcessedFeatures(){
		return processed;
	}
	
	/**
	 * Order of the sink coordinates matters - most important
	 * ones should be listed first.
	 * 
	 * @param graph
	 * @param sinkpoints
	 * @return set of featureids from edges that should be flipped
	 * @throws Exception
	 */
	public void directionalize(DGraph graph, List<Coordinate> sinkpoints) throws Exception{
		
		toflip.clear();
		processed.clear();

		DNode[] sinkNodes = new DNode[sinkpoints.size()];
		
		graph.removeSameEdges();

		boolean nosink = true;
		for (DNode n : graph.getNodes()) {
			for (int i = 0; i < sinkpoints.size(); i ++) {
				Coordinate c = sinkpoints.get(i);
				if (c.equals2D(n.getCoordinate())) {
					n.setSink(true);
					sinkNodes[i] = n;
					nosink = false;
				}
			}			
		}
		
		if (nosink) throw new Exception("No sink points found for graph.");
		for (int i = 0; i < sinkNodes.length; i ++) {
			if (sinkNodes[i] == null) {
				throw new Exception("no flowpath meets sink node defined at POINT(" + sinkpoints.get(i).x + " " + sinkpoints.get(i).y + ")");
			}
		}

		directionalizeGraph(graph, sinkNodes);
		
		//process any non-directionalized edges here; generally these
		//should be small isolated areas and sinks are picked at random
		logger.info("Processing isloated areas");
		for (DEdge e : graph.edges) {
			if (e.getDType() == DirectionType.KNOWN) continue;
			processNoSink(graph, e);
		}
		
		for (DEdge e : graph.edges) {
			if (e.getDType() == DirectionType.UNKNOWN) {
				logger.error("Edge not directionalized: " + e.toString());
			}
		}
	}
	
	private void directionalizeGraph(DGraph graph, DNode[] sinkNodes) throws Exception {

		List<DNode> toProcess = new ArrayList<>();
		for (DNode n : sinkNodes) toProcess.add(n);
		
		while(!toProcess.isEmpty()) {
			
			DNode sink = toProcess.remove(0);
			
			//find connected components
			DGraph sub = SubGraph.computeSubGraph(graph, sink);
			
			//remove all sink nodes from processing list
			for (DNode d : sub.getNodes())  if (d.isSink()) toProcess.remove(d);
			
			//find sorted list of local sinks
			List<DNode> localSinks = new ArrayList<DNode>();
			for (DNode s : sinkNodes) {
				if (sub.getNodes().contains(s)) {
					localSinks.add(s);
				}
			}
			
			
			if (localSinks.size() > 1) {
				//we need at least one degree 3 node in the network otherwise it is basically a loop
				boolean degree3 = false;
				for (DNode n : sub.getNodes()) {
					if (n.getDegree() > 2) {
						degree3 = true;
						break;
					}
				}
				if (degree3) {
					//generally not an error, but I've seen a lot of direction errors
					//in the dataset when this is the case so warn user
					StringBuilder sb = new StringBuilder();
					sb.append("A sub network has multiple sinks: " );
					for (DNode n : localSinks) sb.append(n.toString() + " " );
					logger.warn(sb.toString());			
				
					//add a single sink node and join all sinks to that node
					Coordinate c = localSinks.get(0).getCoordinate();
					
					DNode tempsink = new DNode(new Coordinate(c.x, c.y));
					graph.getNodes().add(tempsink);
					//link sink nodes to edges
					for (DNode s : localSinks) {
						DEdge temp = new DEdge(s,tempsink,s.getCoordinate(),tempsink.getCoordinate(), 
								new EdgeInfo(s.getCoordinate(), s.getCoordinate(), tempsink.getCoordinate(), 
										tempsink.getCoordinate(), EfType.REACH, new FeatureIdImpl("temp-sink-node"), 
										0, DirectionType.KNOWN));
						tempsink.addEdge(temp);
						s.getEdges().add(temp);
						graph.getEdges().add(temp);
					}
					
					//the sink node needs to have only one in edge
					DNode tempsink2 = new DNode(new Coordinate(c.x, c.y));
					tempsink.setSink(true);
					graph.getNodes().add(tempsink2);
					DEdge temp = new DEdge(tempsink,tempsink2,tempsink.getCoordinate(),tempsink2.getCoordinate(), 
							new EdgeInfo(tempsink.getCoordinate(), tempsink.getCoordinate(), tempsink2.getCoordinate(), 
									tempsink2.getCoordinate(), EfType.REACH, new FeatureIdImpl("temp-sink-node2"), 0,
									DirectionType.KNOWN));
					tempsink.addEdge(temp);
					tempsink2.getEdges().add(temp);
					graph.getEdges().add(temp);
					
					
					localSinks.clear();
					localSinks.add(tempsink2);
					sink = tempsink2;
					sub = SubGraph.computeSubGraph(graph, sink);
				}
			}
			//find bridge nodes
			BridgeFinder bb = new BridgeFinder();
			bb.computeBridges(sub, sink);

			//partition graph at bridge nodes
			Partition pp = new Partition();
			pp.partion(sub);
			
			//directionalized main tree
			TreeDirection td = new TreeDirection();
			td.directionalize(sub, localSinks);
			
			//if there are any subgraphs with a single edge this is an error
			for (DGraph subg : pp.getSubGraphs()) {
				if (subg.getEdges().size() == 1) {
					throw new Exception("Sub graph with only a single edge");
				}
			}

			//directionalize each of the subgraphs
			PathDirectionalizer pd = new PathDirectionalizer(angleComputer, prop);
			//int cnt = 1;
			//int total = pp.getSubGraphs().size();
			for (DGraph subg : pp.getSubGraphs()) {
				//logger.info("Processing subgraph " + (cnt++) + "/" + total);
				pd.directionalize(subg);
				postProcessShortEdges(subg);
			}
			
			//figure out which edges need flipping
			for (DEdge e : sub.getEdges()) {
				if (e.getID() != null) {
					if (e.isFlipped()) toflip.add(e.getID());
					if (e.getDType() == DirectionType.KNOWN) processed.add(e.getID());
				}
				if (e.getSameEdges() == null) continue;
				for (DEdge comp : e.getSameEdges()) {
					processed.add(comp.getID());
					if (comp.getNodeA() != e.getNodeA()) {
						toflip.add(comp.getID());
					}
				}
			}
			
			for (DGraph subg : pp.getSubGraphs()) {
				for (DEdge e : subg.getEdges()) {
					if (e.getID() != null) {
						if (e.isFlipped()) toflip.add(e.getID());
						if (e.getDType() == DirectionType.KNOWN) processed.add(e.getID());
					}
					if (e.getSameEdges() == null) continue;
					
					for (DEdge comp : e.getSameEdges()) {
						processed.add(comp.getID());
						if (comp.getNodeA() != e.getNodeA()) {
							toflip.add(comp.getID());
						}
					}
				}
			}
		}

	}
	
	private void processNoSink(DGraph graph, DEdge start) throws Exception {
		//find connected components
		DGraph sub = SubGraph.computeSubGraph(graph, start.getNodeA());
		
		//do a quick qa check of this graph and see if there are more than 5 
		//non-skeleton edges; if so print a warning
		int cnt = 0;
		for (DEdge e : sub.edges) {
			if (e.getType() != EfType.SKELETON ) cnt++;
		}
		if (cnt > 5) {
			logger.warn("An (isolated) subgraph without any defined sinks is larger then 5 edges in size (actual size: " + cnt + ") @ " + sub.nodes.get(0).toString() + ". These will be directionalized by computing a sink based on the network.");
		}
		

		Set<DNode> sinks = new HashSet<>();
		
		//NOTE: we do not look for sinks from directionalized edges here
		//as those should have be generated in the main processing
		
		//find all degree1 nodes that are downstream from a known edge
		List<DNode> toprocess = new ArrayList<>();
		for (DEdge e : sub.edges) e.setVisited(false);
		for (DEdge e : sub.edges) {
			if (e.getDType() == DirectionType.UNKNOWN) continue;
			e.setVisited(true);

			toprocess.add(e.getNodeB());
			while(!toprocess.isEmpty()) {
				DNode n = toprocess.remove(0);
				for (DEdge eo : n.getEdges()) {
					if (eo.isVisited()) continue;
					if (eo.getDType() == DirectionType.KNOWN) continue;
					eo.setVisited(true);
					DNode next = eo.getOtherNode(n);
					if (next.getDegree() == 1) {
						sinks.add(next);
					}else {
						toprocess.add(next);
					}
				}
			}
		}
		
		if (sinks.isEmpty()) {
			//look for a degree 1 node whose edge is skeleton
			for (DNode n : sub.nodes) {
				if (n.getDegree() == 1 && n.getEdges().get(0).getType() == EfType.SKELETON) {
					sinks.add(n);
					break;
				}
			}
		}
		if (sinks.isEmpty()) {
			//look for any degree 1 node
			for (DNode n : sub.nodes) {
				if (n.getDegree() == 1) {
					sinks.add(n);
					break;
				}
			}
		}
		
		if (sinks.isEmpty()) {
			sinks.add(sub.nodes.get(0));
		}
		directionalizeGraph(sub, sinks.toArray(new DNode[sinks.size()]));
	}
	
	
	private void postProcessShortEdges(DGraph g) throws Exception {
		double minlength = prop.getProperty(Property.DIR_SHORT_SEGMENT);
		for (DEdge e : g.getEdges()) {
			
			if (e.getRawLength() > minlength) continue;
			if (e.getRawType() == DirectionType.KNOWN) continue;
			
			if (e.getNodeA().getDegree() < 3 || e.getNodeB().getDegree() < 3 ) continue;

			//if flipped edges causes a source or sink node don't flip
			int aout = 0;
			for (DEdge in : e.getNodeA().getEdges()) {
				if (in == e) continue;
				if (in.getNodeA() == e.getNodeA()) aout ++;
			}
			if (aout == 0) {
				continue;
			}
			
			int bout = 0;
			for (DEdge in : e.getNodeB().getEdges()) {
				if (in == e) continue;
				if (in.getNodeA() == e.getNodeB()) bout ++;
			}
			if (bout == 0) {
				continue;
			}
			
			//may we flip
//			System.out.println("short:" + e.toString());
			
			//look at flow direction
			List<Coordinate> dirs = new ArrayList<>();
			Coordinate start = e.getNodeA().getCoordinate();
			for (DEdge in : e.getNodeA().getEdges()) {
				if (in == e) continue;
				if (!g.getEdges().contains(in)) continue;
				Coordinate a = in.getNodeA().getCoordinate();
				Coordinate b = in.getNodeB().getCoordinate();
				double xoff = a.x - start.x;
				double yoff = a.y - start.y;
					
				Coordinate c2 = new Coordinate(b.x - xoff, b.y - yoff);
				dirs.add(c2);
			}
			
			for (DEdge out : e.getNodeB().getEdges()) {
				if (out == e) continue;
				if (!g.getEdges().contains(out)) continue;
				Coordinate a = out.getNodeA().getCoordinate();
				Coordinate b = out.getNodeB().getCoordinate();
					
				double xoff = a.x - start.x;
				double yoff = a.y - start.y;
					
				Coordinate c2 = new Coordinate(b.x - xoff, b.y - yoff);
				dirs.add(c2);
			}
			
			double cx = dirs.stream().mapToDouble(value->value.x).average().orElse(Double.NaN);
			double cy = dirs.stream().mapToDouble(value->value.y).average().orElse(Double.NaN);
			if (Double.isNaN(cx) || Double.isNaN(cy)) {
				continue;
			}
			Coordinate end = new Coordinate(cx, cy);
			double bearing = angleComputer.computeBearing(start,  end);
				
			double bearing2 = angleComputer.computeBearing(e.getNodeA().getCoordinate(),  e.getNodeB().getCoordinate());	
			double d1 = Math.abs(bearing - bearing2);
			if (d1 > Math.PI) d1 = Math.PI * 2 - d1;
				
			double bearing3 = angleComputer.computeBearing(e.getNodeB().getCoordinate(),  e.getNodeA().getCoordinate());	
			double d2 = Math.abs(bearing - bearing3);
			if (d2 > Math.PI) d2 = Math.PI * 2 - d2;
				
			if (d2 < d1) {
				e.flip();
				
				DPath temp = new DPath();
				temp.getEdges().add(e);
				temp.getNodes().add(e.getNodeA());
				temp.getNodes().add(e.getNodeB());
				//check for cycles
				if (Directionalizer.cycleCheck(temp, g)) {
					//revert flip
					e.flip();
				}
			}			
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
	public static boolean cycleCheck(DPath path, DGraph graph) {
		return findCycles(path, graph);
	}
	
	private static boolean findCycles(DPath path, DGraph graph) {
		graph.getEdges().forEach(e->e.setVisited(false));
		for(DEdge f : path.getEdges()) {
			if(findCycles(f, new HashSet<DEdge>(), graph)) {
				return true;
			}
		}
		return false;
	}
	
	private static boolean findCycles(DEdge f, HashSet<DEdge> visited, DGraph graph) {
		if (f.isVisited()) return false;
		if(visited.contains(f)) {
			return true;
		}
		visited.add(f);
		for (DEdge u : f.getNodeA().getEdges()) {
			if (u.getDType() == DirectionType.UNKNOWN) continue;
			if (!graph.getEdges().contains(u)) continue;
			if (u.getNodeB() == f.getNodeA()) {
				if (findCycles(u, visited, graph)) {
					return true;
				}
			}
		}
		f.setVisited(true);
		visited.remove(f);
		return false;
	}
}
