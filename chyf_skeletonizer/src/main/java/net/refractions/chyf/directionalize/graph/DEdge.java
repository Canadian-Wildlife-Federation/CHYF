package net.refractions.chyf.directionalize.graph;

import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.filter.identity.FeatureId;

import net.refractions.chyf.datasource.ChyfDataSource.EfType;
import net.refractions.chyf.directionalize.DirectionType;

public class DEdge {

	private DNode n1;
	private DNode n2;
	
	private EdgeInfo info;
	private boolean isbridge = false;
	
	//visited flagged
	protected boolean visited = false;
	
	//identifies if this edge is part of 
	//a path or not
	protected boolean pathedge = false;
	
	
	private DirectionType rawdt;
	
	private List<DEdge> components = new ArrayList<>();
	
	public DEdge(DNode n1, DNode n2, EdgeInfo info) {
		this.n1 = n1;
		this.n2 = n2;
		this.info = info;
		rawdt = info.getDirectionType();
	}
	
	public boolean isVisited() {
		return this.visited;
	}
	
	public void setVisited(boolean isvisited) {
		this.visited = isvisited;
	}
	
	public void resetKnown() throws Exception{
		if (rawdt == info.getDirectionType()) return;
		

		if (rawdt == DirectionType.KNOWN ) {
			//currently unknown; resetting to known; should never happen
			throw new Exception("Should reset an edge from unknown dir to known direction");
		}
		//known resetting to unknown; if flipped lets flip back
		if (isFlipped()) {
			flip();
		}
		info.setDirectionType(DirectionType.UNKNOWN);
	}
	/**
	 * 
	 * @return set of edges that were the same as this edge and removed
	 * from the graph
	 */
	public List<DEdge> getSameEdges(){
		return this.components;
	}
	
	/**
	 * Other edges with exact same end nodes
	 */
	public void addSameEdge(DEdge other) {
		if (components == null) components = new ArrayList<>();
		if (components.contains(other)) return;
		components.add(other);
	}
	
	
	/**
	 * This field is only valid after the BridgeFinder
	 * is called.  Will always return false before then.
	 * 
	 * @return true if edge is bridge node
	 */
	public boolean isBridge() {
		return this.isbridge;
	}
	
	/**
	 * Sets the edge bridge flag to true
	 *
	 */
	public void setBridge() {
		this.isbridge = true;
	}
	public double getLength() {
		return info.getLength();
	}
	
	public EfType getType() {
		return info.getType();
	}
	
	public FeatureId getID() {
		return info.getFeatureId();
	}
	public boolean isFlipped() {
		return info.isFlipped();
	}
	public DNode getNodeA() {
		return this.n1;
	}
	public DNode getNodeB() {
		return this.n2;
	}
	
	public void setNodeA(DNode node) {
		this.n1 = node;
	}
	public void setNodeB(DNode node) {
		this.n2 = node;
	}
	public DNode getOtherNode(DNode node) {
		if (n1 == node) return n2;
		if (n2 == node) return n1;
		return null;
	}
	public DirectionType getDType() {
		return info.getDirectionType();
	}
	
	public void setKnown() {
		info.setDirectionType(DirectionType.KNOWN);
	}
	public void flip() throws Exception{
		if (rawdt == DirectionType.KNOWN) {
//			throw new Exception("Cannot flip direction of known edge: " + toString());
			System.out.println("Cannot flip direction of known edge: " + toString());
		}
		DNode temp = n1;
		n1 = n2;
		n2 = temp;
		info.setDirectionType(DirectionType.KNOWN);
		info.setFlipped();
	}
	
	public void print() {
		System.out.println(toString());
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("LINESTRING( ");
		Coordinate c = n1.getCoordinate();
		sb.append(c.x + " " + c.y);
		c = n2.getCoordinate();
		sb.append("," + c.x + " " + c.y);
		sb.append(")");
		return sb.toString();
	}
	
}