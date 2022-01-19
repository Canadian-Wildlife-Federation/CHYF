package nrcan.cccmeo.chyf.db;

import org.locationtech.jts.geom.Polygon;

public class Waterbody {
	
	private int definition;
	private double area = Double.NaN;
	private Polygon waterbody;

	Waterbody() {
		
	}
	
	Waterbody(int definition, double area, Polygon waterbody) {
		this.setDefinition(definition);
		this.setArea(area);
		this.setPolygon(waterbody);
	}

	public int getDefinition() {
		return definition;
	}

	public void setDefinition(int definition) {
		this.definition = definition;
	}

	public double getArea() {
		return area;
	}

	public void setArea(double area) {
		this.area = area;
	}

	public Polygon getPolygon() {
		return waterbody;
	}

	public void setPolygon(Polygon waterbody) {
		this.waterbody = waterbody;
	}
}
