package nrcan.cccmeo.chyf.db;

import org.locationtech.jts.geom.Polygon;

public class Boundary {

	private Polygon boundary;

	Boundary() {
		
	}
	
	Boundary(Polygon boundary) {
		this.setPolygon(boundary);
	}

	public Polygon getPolygon() {
		return boundary;
	}

	public void setPolygon(Polygon boundary) {
		this.boundary = boundary;
	}
}
