package net.refractions.chyf.model;

import java.util.StringJoiner;

import org.geotools.geometry.jts.ReferencedEnvelope;

import net.refractions.chyf.controller.GraphExportParameters;

public class GraphExport {

	
	private String fpQuery;
	private String nexusQuery;
	private String catchmentQuery;
	private ReferencedEnvelope bounds;
	
	private String aoi;
	
	public GraphExport(String fpQuery, String nexusQuery, String catchmentQuery, ReferencedEnvelope bounds,
			GraphExportParameters params) {
		this.fpQuery = fpQuery;
		this.nexusQuery = nexusQuery;
		this.catchmentQuery = catchmentQuery;
		this.bounds = bounds;
		setAreaOfInterest(params);
		
	}
	
	public ReferencedEnvelope getBounds() {
		return this.bounds;
	}
	
	public String getFlowpathQuery() {
		return this.fpQuery;
	}
	
	public String getNexusQuery() {
		return this.nexusQuery;
	}
	
	public String getCatchmentQuery() {
		return this.catchmentQuery;
	}
	
	public String getAoi() {
		return this.aoi;
	}
	
	private void setAreaOfInterest(GraphExportParameters params) {
		if (params.getBbox() != null) {
			aoi = "BBOX: (" + params.getBbox().getMinX() + ", " + params.getBbox().getMinY() + ", " + params.getBbox().getMaxX() + ", " + params.getBbox().getMaxY() + ")";
		}else if (params.getAois() != null) {
			StringJoiner sj = new StringJoiner(",");
			for (String x : params.getAois()) sj.add(x);
			aoi = "AOIs: " + sj.toString();
		}else {
			aoi = "unknown";
		}
	}
}
