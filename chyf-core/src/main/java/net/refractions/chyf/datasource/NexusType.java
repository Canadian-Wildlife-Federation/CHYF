package net.refractions.chyf.datasource;

public enum NexusType {

	HEADWATER(1),
	TERMINAL_ISOLATE(2),
	TERMINAL_BOUNDARY(3),
	FLOWPATH(4),
	WATER(5),
	BANK(6),
	INFERRED(7),
	UNKNOWN(99);
	
	int code;
	
	NexusType(int code){
		this.code = code;
	}
	
	public int getCode() {
		return this.code;
	}
}
