package net.refractions.chyf.directionalize;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.filter.identity.FeatureId;

import net.refractions.chyf.directionalize.graph.BridgeFinder;
import net.refractions.chyf.directionalize.graph.DEdge;
import net.refractions.chyf.directionalize.graph.DGraph;
import net.refractions.chyf.directionalize.graph.DNode;
import net.refractions.chyf.directionalize.graph.DNode.NodeType;
import net.refractions.chyf.directionalize.graph.Partition;
import net.refractions.chyf.directionalize.graph.PathDirectionalizer;
import net.refractions.chyf.directionalize.graph.SubGraph;
import net.refractions.chyf.directionalize.graph.TreeDirection;

public class Directionalizer {
	
	/**
	 * Order of the sink coordinates matters - most important
	 * ones should be listed first.
	 * 
	 * @param graph
	 * @param sinkpoints
	 * @return
	 * @throws Exception
	 */
	public Set<FeatureId> directionalize(DGraph graph, List<Coordinate> sinkpoints) throws Exception{
		
		Set<FeatureId> toflip = new HashSet<>();

		List<DNode> toProcess = new ArrayList<>();
		DNode[] sinkNodes = new DNode[sinkpoints.size()];
		
		graph.removeSameEdges();

		boolean nosink = true;
		for (DNode n : graph.getNodes()) {
			for (int i = 0; i < sinkpoints.size(); i ++) {
				Coordinate c = sinkpoints.get(i);
				if (c.equals2D(n.getCoordinate())) {
					n.setType(NodeType.SINK);
					toProcess.add(n);
					sinkNodes[i] = n;
					nosink = false;
				}
			}
		}
		if (nosink) throw new Exception("No sink points found for graph.");
		
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
//			sub.edges.forEach(e->e.print());
			//directionalize each of the subgraphs
			for (DGraph subg : pp.getSubGraphs()) {
				PathDirectionalizer.directionalize(subg);
			}
			
			
			//figure out which edges need flipping
			for (DEdge e : sub.getEdges()) {
				if (e.getID() != null && e.isFlipped()) toflip.add(e.getID());
				if (e.getSameEdges() == null) continue;
				for (DEdge comp : e.getSameEdges()) {
					if (comp.getNodeA() != e.getNodeA()) {
						toflip.add(comp.getID());
					}
				}
			}
			
			for (DGraph subg : pp.getSubGraphs()) {
				for (DEdge e : subg.getEdges()) {
					if (e.getID() != null && e.isFlipped()) toflip.add(e.getID());
					if (e.getSameEdges() == null) continue;
					for (DEdge comp : e.getSameEdges()) {
						if (comp.getNodeA() != e.getNodeA()) {
							toflip.add(comp.getID());
						}
					}
				}
			}
		}
		return toflip;
	}
	
	
}
