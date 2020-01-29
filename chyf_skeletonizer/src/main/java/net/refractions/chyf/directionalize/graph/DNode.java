package net.refractions.chyf.directionalize.graph;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;

import net.refractions.chyf.directionalize.DirectionType;

public class DNode {
	
	private Coordinate c;
	private List<DEdge> edges = new ArrayList<>();

	
	public enum NodeType{
		SOURCE,
		SINK,
		SOURCE_OR_SINK
	}
	List<DEdge> temp = new ArrayList<>();
	NodeType type;
	
	
	//these two variable to used in
	//the bridge edge calculations	
	protected int bridgePre = -1;
	protected int bridgeMin = -1;
	

//	public boolean collapsenode = false;
	public boolean hassource = false;

	//used for computing shortest paths
	protected double pathdistance = 0;
	//true if not is on an existing created path
	protected boolean pathnode;
	//visited flag used by shortest path finder
	protected boolean pathvisited;
	
	public DNode(Coordinate c) {
		this.c = c;
		this.type = null;
	}
	
	public boolean isSink() {
		return type != null && type == NodeType.SINK;
	}
	public boolean isSource() {
		return type != null && type == NodeType.SOURCE;
	}
	public void setType(NodeType type) {
		this.type = type;
	}
	public NodeType getType() {
		return this.type;
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
	
	public List<DEdge> getKnownEdges(){
		temp.clear();
		for (DEdge e : edges) {
			if (e.getDType() == DirectionType.KNOWN) temp.add(e);
		}
		return temp;
	}
	
	public List<DEdge> getUnKnownEdges(){
		temp.clear();
		for (DEdge e : edges) {
			if (e.getDType() == DirectionType.UNKNOWN) temp.add(e);
		}
		return temp;
	}
	
	public String toString() {
		return "POINT( " + c.x + " " + c.y + ")";
	}
	public void print() {
		System.out.println(toString());
	}
	
	public boolean visited = false;
	public int order = 0;
	public int suborder = 0;
}