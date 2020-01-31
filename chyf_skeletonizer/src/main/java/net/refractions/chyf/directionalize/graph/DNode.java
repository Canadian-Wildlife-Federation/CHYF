package net.refractions.chyf.directionalize.graph;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;

/**
 * Represents a node in the directionalizer graph
 * 
 * @author Emily
 *
 */
public class DNode {
	
	private Coordinate c;
	private List<DEdge> edges = new ArrayList<>();
	private boolean isSink = false;
	
	//these two variable to used in
	//the bridge edge calculations	
	protected int bridgePre = -1;
	protected int bridgeMin = -1;

	//used for computing shortest paths
	protected double pathdistance = 0;
	//true if not is on an existing created path
	protected boolean pathnode;
	//visited flag used by shortest path finder
	protected boolean pathvisited;
	
	//used for computing subgraphs
	protected boolean visited = false;
	
	public DNode(Coordinate c) {
		this.c = c;
		this.isSink = false;
	}
	
	public boolean isSink() {
		return this.isSink;
	}
	public void setSink(boolean issink) {
		this.isSink = issink;
	}
	
	public Coordinate getCoordinate() {
		return this.c;
	}
	
	public int getDegree() {
		return edges.size();
	}

	public void addEdge(DEdge e) {
		this.edges.add(e);
	}
	public void removeEdge(DEdge e) {
		this.edges.remove(e);
	}
	public List<DEdge> getEdges(){
		return this.edges;
	}

	
	public String toString() {
		return "POINT( " + c.x + " " + c.y + ")";
	}
	public void print() {
		System.out.println(toString());
	}
	
	
}