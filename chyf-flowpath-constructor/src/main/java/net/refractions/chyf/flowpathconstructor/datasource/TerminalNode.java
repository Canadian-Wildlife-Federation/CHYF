package net.refractions.chyf.flowpathconstructor.datasource;

import org.locationtech.jts.geom.Point;

import net.refractions.chyf.datasource.FlowDirection;

public class TerminalNode {

	private Point point;
	private FlowDirection fd;
	
	private String[] names;
		
	public TerminalNode(Point point, FlowDirection fd) {
		this.point = point;
		this.fd = fd;
	}
	
	public Point getPoint() {
		return this.point;
	}
	
	public FlowDirection getDirection() {
		return this.fd;
	}
	
	public void setNames(String[] ids) {
		this.names = ids;
	}
	
	public String[] getNameIds(){
		return this.names;
	}
}
