package net.refractions.chyf.directionalize.graph;

import java.util.ArrayList;
import java.util.List;

public class DPath {

	protected List<DNode> nodes;
	protected List<DEdge> edges;
	
	public DPath() {
		nodes = new ArrayList<>();
		edges = new ArrayList<>();
	}
}
