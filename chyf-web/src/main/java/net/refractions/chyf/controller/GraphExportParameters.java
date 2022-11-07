package net.refractions.chyf.controller;

import java.beans.ConstructorProperties;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;

import io.swagger.v3.oas.annotations.Parameter;
import net.refractions.chyf.exceptions.InvalidParameterException;

public class GraphExportParameters {
	
	private static Envelope VALID_BOUNDS = new Envelope(-180,180,-90, 90);
	
	@Parameter(name="aoi", required = false, description = "Comma delimited list of AOI short names to export")
	private String aoilist;
	
	@Parameter(name="bbox", required = false, description = "The extent of features to include in export (minlong,minlat,maxlong,maxlat)")
	private String bbox;

	private List<String> aois = null;
	private Envelope env = null;
	
	//this is the only way I could figure out
	//how to provide names for query parameters and
	//use a POJO to represent these parameters
	//I needed custom name for max-results
	//https://stackoverflow.com/questions/56468760/how-to-collect-all-fields-annotated-with-requestparam-into-one-object
	@ConstructorProperties({"aoi", "bbox"})
	public GraphExportParameters(String aoi, String bbox) {
		this.aoilist = aoi;
		this.bbox = bbox;
	}
	
	public List<String> getAois() { return this.aois; }
	public Envelope getBbox() {return this.env; }
	
	/**
	 * Parses parameters into data types and validates values.
	 * Should be called before calling get functions
	 * 
	 */
	public void parseAndValidate() {
		if ((this.aoilist == null ||  this.aoilist.isBlank()) &&
			(this.bbox == null || this.bbox.isBlank())) {
			throw new InvalidParameterException("At least one of aoi or bbox must be provided");
		}
		if (this.aoilist != null && !this.aoilist.isBlank()) {
			String[] bits = aoilist.split(",");
			aois = new ArrayList<>();
			for (String b : bits) {
				if (!b.isBlank()) aois.add(b.toUpperCase());
			}
		}
		if (this.bbox != null && !this.bbox.isBlank()) this.env = parseBbox();
		
		if (this.aois != null && !this.aois.isEmpty() && this.env != null) {
			throw new InvalidParameterException("Only one of the aoi or bbox parameters can be supplied");
		}
	}

	
	private Envelope parseBbox() {
		String[] parts = bbox.split(",");
		
		if (parts.length != 4) {
			throw new InvalidParameterException("The bbox parameter is invalid. Must be of the form: bbox=<xmin>,<ymin>,<xmax>,<ymax>");
		}
		try {
			double xmin = Double.parseDouble(parts[0]);
			double ymin = Double.parseDouble(parts[1]);
			double xmax = Double.parseDouble(parts[2]);
			double ymax = Double.parseDouble(parts[3]);
			
			if (xmin > xmax) {
				double temp = xmin;
				xmin = xmax;
				xmax = temp;
			}
			if (ymin > ymax) {
				double temp = ymin;
				ymin = ymax;
				ymax = temp;
			}
			
			validateCoordinate(xmin, ymin);
			validateCoordinate(xmax, ymax);

			return new Envelope(xmin, xmax, ymin, ymax);
		}catch (Exception ex) {
			throw new InvalidParameterException("The bbox parameter is invalid. Must be of the form: bbox=<xmin>,<ymin>,<xmax>,<ymax>");
		}
	}
	
	
	private void validateCoordinate(double x, double y) {
		//ensure x,y is within the bounds of web mercator
		if (!VALID_BOUNDS.contains(new Coordinate(x,y))) {
			throw new InvalidParameterException(MessageFormat.format("The coordinates provided ({0}, {1}) are outside the valid bounds ({2},{3},{4},{5})",  x,y,VALID_BOUNDS.getMinX(),VALID_BOUNDS.getMinY(),VALID_BOUNDS.getMaxX(),VALID_BOUNDS.getMaxY()));
		}
	}
}
