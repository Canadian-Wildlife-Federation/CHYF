package nrcan.cccmeo.chyf.db;

import org.locationtech.jts.geom.LineString;

public class Flowpath {
	
	private String type;
	private String rank;
	private String nameId;
	private String name;
	private double length;
	private LineString linestring;
	
	public Flowpath() {
		
	}
	
	public Flowpath(String type, String rank, String name, String nameId,  LineString linestring) {
		this.type = type;
		this.rank = rank;
		this.name = name;
		this.nameId = nameId;
		this.linestring = linestring;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getRank() {
		return rank;
	}

	public void setRank(String rank) {
		this.rank = rank;
	}

	public String getNameId() {
		return nameId;
	}

	public void setNameId(String nameId) {
		this.nameId = nameId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public double getLength() {
		return length;
	}

	public void setLength(double length) {
		this.length = length;
	}

	public LineString getLinestring() {
		return linestring;
	}

	public void setLinestring(LineString linestring) {
		this.linestring = linestring;
	}

}
