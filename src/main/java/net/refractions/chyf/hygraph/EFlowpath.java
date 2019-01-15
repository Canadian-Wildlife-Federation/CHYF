package net.refractions.chyf.hygraph;

import java.util.UUID;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

import net.refractions.chyf.enumTypes.FlowpathType;
import net.refractions.chyf.enumTypes.Rank;
import net.refractions.chyf.indexing.SpatiallyIndexable;

public class EFlowpath implements SpatiallyIndexable {
	private final int id;
	private final Nexus fromNode;
	private final Nexus toNode;
	private final double length;
	private final FlowpathType type;
	private final Rank rank;
	private final UUID nameId;
	private final String name;
	private Integer strahlerOrder;
	private Integer hortonOrder;
	private Integer hackOrder;
	private final ECatchment catchment;
	private final LineString lineString;
	
	public EFlowpath(int id, Nexus fromNode, Nexus toNode, double length, FlowpathType type, Rank rank, String name, UUID nameId,
			ECatchment catchment, LineString lineString) {
		this.id = id;
		this.fromNode = fromNode;
		this.toNode = toNode;
		this.length = length;
		this.type = type;
		this.rank = rank;
		this.name = name;
		this.nameId = nameId;
		this.catchment = catchment;
		this.lineString = lineString;
	}

	public int getId() {
		return id;
	}

	public Nexus getFromNode() {
		return fromNode;
	}

	public Nexus getToNode() {
		return toNode;
	}

	public LineString getLineString() {
		return lineString;
	}

	public double getLength() {
		return length;
	}
	
	public FlowpathType getType() {
		return type;
	}

	public Rank getRank() {
		return rank;
	}
	
	public String getName() {
		return name;
	}

	public UUID getNameId() {
		return nameId;
	}

	public Integer getStrahlerOrder() {
		return strahlerOrder;
	}

	public void setStrahlerOrder(Integer strahlerOrder) {
		this.strahlerOrder = strahlerOrder;
	}

	public Integer getHortonOrder() {
		return hortonOrder;
	}

	public void setHortonOrder(Integer hortonOrder) {
		this.hortonOrder = hortonOrder;
	}

	public Integer getHackOrder() {
		return hackOrder;
	}

	public void setHackOrder(Integer hackOrder) {
		this.hackOrder = hackOrder;
	}

	public ECatchment getCatchment() {
		return catchment;
	}

	public Nexus getOtherNode(final Nexus node) {
		if(node == toNode) {
			return fromNode;
		}
		if(node == fromNode) {
			return toNode;
		}
		return null;
	}

	@Override
	public Envelope getEnvelope() {
		return lineString.getEnvelopeInternal();
	}

	@Override
	public double distance(Point p) {
		return lineString.distance(p);
	}

}
