package net.refractions.chyf.directionalize.graph;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a path in the graph
 * 
 * @author Emily
 *
 */
public class DPath {

	protected List<DNode> nodes;
	protected List<DEdge> edges;
	
	public DPath() {
		nodes = new ArrayList<>();
		edges = new ArrayList<>();
	}
}
