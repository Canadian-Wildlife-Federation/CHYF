package net.refractions.chyf.flowpathconstructor.datasource;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.data.Transaction;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.ConstructionPoint;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkelLineString;

public interface IFlowpathDataSource extends ChyfDataSource{

	public static final String CATCHMENT_INTERNALID_ATTRIBUTE = "ecatchment_id";
	public static final String NODETYPE_ATTRIBUTE = "node_type";
	
	/**
	 * Node type associated with construction points 
	 *
	 */
	public enum NodeType{
		BANK(1),
		FLOWPATH(2),
		WATER(3),
		HEADWATER(4),
		TERMINAL(5);
		
		private int modelValue;
		
		private NodeType(int modelValue) {
			this.modelValue = modelValue;
		}
		
		public int getChyfValue() {
			return this.modelValue;
		}
		
		public static NodeType parseValue(int value) throws IOException{
			for (NodeType d : NodeType.values()) {
				if (d.modelValue == value) return d;
			}
			throw new IOException("Type value of " + value + " is invalid for NodeType");
		}
	}

	public CoordinateReferenceSystem getCoordinateReferenceSystem();
	public List<Polygon> getAoi() throws IOException ;
	
	public void updateWaterbodyGeometries(Collection<Polygon> polygons) throws IOException;
	public void updateCoastline(FeatureId fid, LineString newls, Transaction tx) throws IOException;
	public List<Point> getTerminalNodes() throws Exception;
	public void createConstructionsPoints(List<ConstructionPoint> points) throws IOException;
	public void writeSkeletons(Collection<SkelLineString> skeletons) throws IOException;
	public void removeExistingSkeletons(boolean bankOnly) throws SQLException;
	public List<ConstructionPoint> getConstructionsPoints(Object catchmentId) throws IOException;


	public void addRankAttribute() throws Exception;
	public void flipFlowEdges(Collection<FeatureId> pathstoflip, Collection<FeatureId> processed) throws Exception;
	public Set<Coordinate> getOutputConstructionPoints() throws Exception;
	public void writeRanks(Map<FeatureId, RankType> ranks) throws Exception;


}
