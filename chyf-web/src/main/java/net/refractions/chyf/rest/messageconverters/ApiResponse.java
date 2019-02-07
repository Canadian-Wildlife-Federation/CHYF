package net.refractions.chyf.rest.messageconverters;

import net.refractions.chyf.rest.PourpointParameters;
import net.refractions.chyf.rest.SharedParameters;


public class ApiResponse {
	
	private Object data;
	private int srs;
	private long executionTime;
	private String errorMsg;
	private String callback = "jsonp";
	private Double scale = null;

	private boolean includeStats = true;
	
	public ApiResponse(Object data) {
		this.data = data;
	}
	
	public Object getData() {
		return data;
	}

	public void setData(Object data) {
		this.data = data;
	}

	public boolean includeStats() {
		return this.includeStats;
	}
	
	public int getSrs() {
		return srs;
	}

	public void setSrs(int srs) {
		this.srs = srs;
	}

	public void setParams(SharedParameters params) {
		callback = params.getCallback();
		setSrs(params.getSrs());
		setScale(params.getScale());
	
		if (params instanceof PourpointParameters) {
			this.includeStats = ((PourpointParameters) params).getIncludeStats();
		}
	}
	
	public boolean isError() {
		return errorMsg != null;
	}
	
	public String getErrorMsg() {
		return errorMsg;
	}
	
	public void setErrorMsg(String msg) {
		this.errorMsg = msg;
	}

	public String getCallback() {
		return callback;
	}

	public void setExecutionTime(long executionTime) {
		this.executionTime = executionTime;
	}

	public long getExecutionTime() {
		return executionTime;
	}

	public Double getScale() {
		return scale;
	}

	public void setScale(Double scale) {
		this.scale = scale;
	}

}
