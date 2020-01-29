package net.refractions.chyf.directionalize.graph;

import java.util.ArrayList;
import java.util.List;

import net.refractions.chyf.directionalize.DirectionType;

/**
 * Directionalizes a non-directionalized tree structured
 * based on a set of sink nodes.  The order of the sink nodes
 * matters.  The first sink node is the primary and if a path
 * exists from any node to that sink that path is used for
 * directionalizing first.  Then the next node is processed
 * etc until all edges are directionalized.  Known direction edges
 * are kept.
 * 
 * @author Emily
 *
 */
public class TreeDirection {

	/**
	 * Order of the sinks matters, especially in cases where
	 * most of the graph is not directionalized.
	 * 
	 * @param graph
	 * @param localSinks
	 */
	public void directionalize(DGraph graph, List<DNode> localSinks) throws Exception {
		graph.edges.forEach(e->e.visited = false);
		
		//process main sink first
		DNode sink = localSinks.get(0);
		visitUpstream(sink);
		
		//process remaining sinks; walking up until degree 3 node found
		for (int i = 1; i < localSinks.size(); i ++) {
			sink = localSinks.get(i);
			//walk up until first degree 3 node
			DNode prev = sink;
			while(sink != null) {
				if (sink.getDegree() > 2) break;
				
				DEdge e = null;
				if (sink.getEdges().size() == 1) {
					e = sink.getEdges().get(0);
				}else {
					e = sink.getEdges().get(0);
					if (e.getOtherNode(sink) == prev) {
						e = sink.getEdges().get(1);
					}
				}
				if (e.getNodeB() != sink) {
					e.flip();
				}
				e.visited = true;
				prev = sink;
				sink = e.getOtherNode(sink);
			}
			//now walk up any unvisited edges
			visitUpstream(sink);
		}
	
		for (DEdge d : graph.edges) {
			if (!d.visited) {
				throw new Exception("Not all edges visited when directionalizing tree.  At least one eddge missed: " + d.toString());
			}
		}
	}
	
	private void visitUpstream(DNode sink) throws Exception{
		List<DNode> tovisit = new ArrayList<>();
		tovisit.add(sink);
		while(!tovisit.isEmpty()) {
			DNode current = tovisit.remove(0);
	
			for (DEdge e : current.getEdges()) {
				if (e.visited) continue;
				if (e.getDType() == DirectionType.KNOWN) {
					if (e.getNodeB() == current) {
						e.visited = true;
						tovisit.add(e.getOtherNode(current));
					}else {
						//we want to flip but we can't 
						//maybe there is another sink node that can 
						//flip this later
					}
				}else {
					e.visited = true;
					if (e.getNodeB() != current) {
						if (e.getDType() == DirectionType.KNOWN) {
							throw new Exception("Flipped a known edge: " + e.toString());
						}
						e.flip();
					}
					tovisit.add(e.getOtherNode(current));
				}
			}
		}
	}
	
	
}
