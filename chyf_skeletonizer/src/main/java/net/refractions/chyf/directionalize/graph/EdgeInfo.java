package net.refractions.chyf.directionalize.graph;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.filter.identity.FeatureId;

import net.refractions.chyf.datasource.ChyfDataSource.EfType;
import net.refractions.chyf.directionalize.DirectionType;

public class EdgeInfo {

	private EfType type = EfType.SKELETON;
	private FeatureId fid;
	private Double length;
	private DirectionType dtype;
	
	private Coordinate c0, c1;
	private boolean flipped = false;
	
	public EdgeInfo(Coordinate c0, Coordinate c1, EfType type, FeatureId id, double length, DirectionType dtype) {
		this.type = type;
		this.fid = id;
		this.length = length;
		this.dtype = dtype;
		this.c0 = c0;
		this.c1 = c1;
	}
	
	public Coordinate getStart() { return this.c0; }
	public Coordinate getEnd() { return this.c1; }
	public boolean isFlipped() {return this.flipped;}
	public void setFlipped() {
		if (this.flipped) {
			this.flipped = false;
		}else {
			this.flipped = true;
		}
		
	}
	public FeatureId getID() {
		return this.fid;
	}
	public DirectionType getDirectionType() {
		return this.dtype;
	}
	
	public void setDirectionType(DirectionType dtype) {
		this.dtype = dtype;
	}
	
	
	public double getLength() {
		return length;
	}
	
	public FeatureId getFeatureId() {
		return this.fid;
	}
	
	public EfType getType() {
		return this.type;
	}
}
