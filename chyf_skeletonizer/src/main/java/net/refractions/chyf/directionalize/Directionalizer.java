package net.refractions.chyf.directionalize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfDataSource.DirectionType;
import net.refractions.chyf.datasource.ChyfDataSource.EfType;
import net.refractions.chyf.directionalize.graph.BridgeFinder;
import net.refractions.chyf.directionalize.graph.DEdge;
import net.refractions.chyf.directionalize.graph.DGraph;
import net.refractions.chyf.directionalize.graph.DNode;
import net.refractions.chyf.directionalize.graph.Partition;
import net.refractions.chyf.directionalize.graph.PathDirectionalizer;
import net.refractions.chyf.directionalize.graph.SubGraph;
import net.refractions.chyf.directionalize.graph.TreeDirection;

/**
 * Main class for directionalizing a flow network graph
 * 
 * @author Emily
 *
 */
public class Directionalizer {

	private static final Logger logger = LoggerFactory.getLogger(Directionalizer.class.getCanonicalName());

	private Set<FeatureId> toflip;
	private Set<FeatureId> processed ;
	
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
		
		toflip = new HashSet<>();
		processed = new HashSet<>();

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
			if (sinkNodes[i] == null) throw new Exception("no flowpath meets sink node defined at POINT(" + sinkpoints.get(i).x + " " + sinkpoints.get(i).y + ")");
		}
		
		directionalizeGraph(graph, sinkNodes);
		
		//process any non-directionalized edges here; generally these
		//should be small isolated areas and sinks are picked at random
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
			if (sink == null) {
				System.out.println("break");
			}
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
				if (subg.getEdges().size() == 1)  throw new Exception("Sub graph with only a single edge");
			}

			//directionalize each of the subgraphs
			for (DGraph subg : pp.getSubGraphs()) {
				PathDirectionalizer.directionalize(subg);
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
			logger.warn("An (isolated) subgraph without any defined sinks is larger then 5 edges in size @ " + sub.nodes.get(0).toString());
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
	
}
