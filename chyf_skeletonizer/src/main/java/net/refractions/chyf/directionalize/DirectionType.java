package net.refractions.chyf.directionalize;

public enum DirectionType {

	UNKNOWN(-1),
	KNOWN(1);
	
	int type;
	
	DirectionType(int type) {
		this.type =type;
	}
}
