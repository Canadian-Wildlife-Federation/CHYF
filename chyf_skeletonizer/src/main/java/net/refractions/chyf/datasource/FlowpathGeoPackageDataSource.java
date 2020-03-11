/*
 * Copyright 2019 Government of Canada
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */
package net.refractions.chyf.datasource;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.skeletonizer.points.ConstructionPoint;
import net.refractions.chyf.skeletonizer.points.PolygonInfo;

/**
 * Extension of the geopackage data source for flowpath computation
 * 
 * @author Emily
 *
 */
public class FlowpathGeoPackageDataSource extends ChyfGeoPackageDataSource{

	static final Logger logger = LoggerFactory.getLogger(FlowpathGeoPackageDataSource.class.getCanonicalName());
	
	public static final String CONSTRUCTION_PNTS_LAYER = "ConstructionPoints";
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

	
	private boolean cIsMulti = false;
	
	private List<LineString> skeletonWriteCache;
	
	
	public FlowpathGeoPackageDataSource(Path geopackageFile) throws Exception {
		super(geopackageFile);
		this.geopackageFile = geopackageFile;
		read();
	}
	
	protected void read() throws Exception{
		super.read();
		addInternalIdAttribute();
		if (ChyfDataSource.findAttribute(getFeatureType(Layer.EFLOWPATHS), ChyfAttribute.DIRECTION) == null) {
			addDirectionAttribute();
		}
	}
	
	public void updateCoastline(FeatureId fid, LineString newls, Transaction tx) throws IOException {
		try(SimpleFeatureWriter writer = geopkg.writer(getEntry(Layer.SHORELINES), false, ff.id(fid), tx)){
			if (newls.getCoordinateSequence().getDimension() == 3) {
				writer.getFeatureType().getGeometryDescriptor().getUserData().put(Hints.COORDINATE_DIMENSION, 3);
			}else {
				writer.getFeatureType().getGeometryDescriptor().getUserData().put(Hints.COORDINATE_DIMENSION, 2);
			}
			if (writer.hasNext()) {
				SimpleFeature sf = writer.next();
				if (sf.getDefaultGeometry() instanceof MultiLineString) {
					sf.setDefaultGeometry(newls.getFactory().createMultiLineString(new LineString[] {newls}));
				}else {
					sf.setDefaultGeometry(newls);
				}
				writer.write();
			}else {
				throw new IOException("No coastline feature with fid " + fid.toString() + "found to update");
			}
		}
		
	}
	
	/**
	 * Returns all points from the terminal node layer.  The user data associated
	 * with each point is the value of the FlowDirection attribute associated
	 * with the TerminalNode.
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<Point> getTerminalNodes() throws Exception{
		List<Point> pnt = new ArrayList<>();
		try(SimpleFeatureReader reader = getFeatureReader(Layer.TERMINALNODES, Filter.INCLUDE, null)){
			Name flowdirAttribute = ChyfDataSource.findAttribute(reader.getFeatureType(), ChyfAttribute.FLOWDIRECTION);
			while(reader.hasNext()){
				SimpleFeature point = reader.next();
				Point pg = ChyfDataSource.getPoint(point);
				pg.setUserData(FlowDirection.parseValue( (Integer)point.getAttribute(flowdirAttribute)) );
				pnt.add(pg);
			}
		}
		return pnt;
	}
	
	/**
	 * All points from the boundary layer with the userdata set to the FlowDirection
	 * type
	 * 
	 * @return
	 * @throws Exception
	 */
	public Set<Coordinate> getOutputConstructionPoints() throws Exception{
		Set<Coordinate> pnt = new HashSet<>();
		Filter filter = ff.equals(ff.property(ChyfAttribute.FLOWDIRECTION.getFieldName()), ff.literal(FlowDirection.OUTPUT.getChyfValue()));
		try(SimpleFeatureReader reader = geopkg.reader(geopkg.feature(CONSTRUCTION_PNTS_LAYER), filter, null)){
			while(reader.hasNext()){
				SimpleFeature point = reader.next();
				pnt.add(ChyfDataSource.getPoint(point).getCoordinate());
			}
		}
		return pnt;
	}
	
	/**
	 * Create a new layer for skeleton construction points
	 * @param points
	 * @throws IOException
	 */
	public void createConstructionsPoints(List<ConstructionPoint> points) throws IOException {
		
		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
		builder.add("the_geom", Point.class, crs);
		builder.add(ChyfAttribute.INTERNAL_ID.getFieldName(), String.class);
		builder.add(CATCHMENT_INTERNALID_ATTRIBUTE, String.class);
		builder.add("node_type", Integer.class);
		builder.add(ChyfAttribute.FLOWDIRECTION.getFieldName(), Integer.class);
		builder.setName("ConstructionPoints");
		SimpleFeatureType pntType = builder.buildFeatureType();
		
		FeatureEntry entry = new FeatureEntry();
		entry.setTableName(CONSTRUCTION_PNTS_LAYER);
		entry.setM(false);
		List<SimpleFeature> features = new ArrayList<>();
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(pntType);
		GeometryFactory gf = new GeometryFactory();

		for (ConstructionPoint pnt : points) {
			String uuid = UUID.randomUUID().toString();
			featureBuilder.set("the_geom", gf.createPoint(pnt.getCoordinate()));
			featureBuilder.set(NODETYPE_ATTRIBUTE, pnt.getType().getChyfValue());
			featureBuilder.set(ChyfAttribute.FLOWDIRECTION.getFieldName(), pnt.getDirection().getChyfValue());
			featureBuilder.set(ChyfAttribute.INTERNAL_ID.getFieldName(), uuid);
			featureBuilder.set(CATCHMENT_INTERNALID_ATTRIBUTE, pnt.getWaterbodyInfo().getCatchmentId());
			SimpleFeature sf = featureBuilder.buildFeature(uuid);
			features.add(sf);
		}
		geopkg.add(entry, DataUtilities.collection(features));
	}

	/**
	 * Reads construction points from geopackage for the catchment
	 * associated with the provided catchment id.
	 * 
	 * @param catchmentId
	 * @return
	 * @throws IOException
	 */
	public synchronized List<ConstructionPoint> getConstructionsPoints(String catchmentId) throws IOException{
		Filter filter = ff.equals(ff.property(CATCHMENT_INTERNALID_ATTRIBUTE), ff.literal(catchmentId));
		List<ConstructionPoint> points = new ArrayList<>();
		try(SimpleFeatureReader reader = geopkg.reader(geopkg.feature(CONSTRUCTION_PNTS_LAYER), filter, null)){
        	while(reader.hasNext()) {
        		SimpleFeature sf = reader.next();
        		
        		Coordinate c = ((Point)sf.getDefaultGeometry()).getCoordinate();
        		NodeType type = NodeType.parseValue((Integer)sf.getAttribute(NODETYPE_ATTRIBUTE));
        		FlowDirection fd = FlowDirection.parseValue((Integer)sf.getAttribute(ChyfAttribute.FLOWDIRECTION.getFieldName()));
        		ConstructionPoint p = new ConstructionPoint(c, type, fd, null);
        		points.add(p);
        	}
        }
        return points;
	}
	
//	public SimpleFeatureReader getCoastlines(ReferencedEnvelope bounds) throws IOException{
//		return query(bounds, Layer.SHORELINES, null);
//	}
//	

	/**
	 * Updates the geometry of the give polygon.  The polygon must
	 * have a user data set to PolygonInfo class.
	 * 
	 * @param polygon
	 * @throws IOException
	 */
	public void updateWaterbodyGeometries(Collection<Polygon> polygons) throws IOException{
		
		try(DefaultTransaction tx = new DefaultTransaction()){
			try {
				for (Polygon polygon : polygons) {
					FeatureId fid = PolygonInfo.getFeatureId(polygon);
					try(SimpleFeatureWriter writer = geopkg.writer(getEntry(Layer.ECATCHMENTS), false, ff.id(fid), tx)){
						if (polygon.getExteriorRing().getCoordinateSequence().getDimension() == 3) {
							writer.getFeatureType().getGeometryDescriptor().getUserData().put(Hints.COORDINATE_DIMENSION, 3);
						}else {
							writer.getFeatureType().getGeometryDescriptor().getUserData().put(Hints.COORDINATE_DIMENSION, 2);
						}
						if (writer.hasNext()) {
							SimpleFeature sf = writer.next();
							if (cIsMulti) {
								sf.setDefaultGeometry(polygon.getFactory().createMultiPolygon(new Polygon[] {polygon}));
							}else {
								sf.setDefaultGeometry(polygon);
							}
							writer.write();
						}else {
							throw new IOException("No feature with fid " + fid.toString() + "found to update");
						}
					}
				}
				tx.commit();
			}catch (IOException ex) {
				tx.rollback();
				throw ex;
			}
		}
	}
	
	
	/**
	 * updates the ranks of features in the datasource
	 * 
	 * @param polygon
	 * @throws IOException
	 */
	public void writeRanks(Map<FeatureId, RankType> ranks) throws Exception{
		
		try(DefaultTransaction tx = new DefaultTransaction()){
			try(SimpleFeatureWriter writer = geopkg.writer(getEntry(Layer.EFLOWPATHS), false, Filter.INCLUDE, tx)){
				Name rankatt = ChyfDataSource.findAttribute(writer.getFeatureType(), ChyfAttribute.RANK);
				while(writer.hasNext()) {
					SimpleFeature sf = writer.next();
					FeatureId fid = sf.getIdentifier();
					RankType rank = ranks.get(fid);
					if (rank == null) {
						//bank
						rank = RankType.PRIMARY;
					}
					
					sf.setAttribute(rankatt, rank.getChyfValue());
					writer.write();
				}
				
			}
			tx.commit();
		}
	}
	
	/**
	 * Updates the geometry of the give polygon.  The polygon must
	 * have a user data set to PolygonInfo class.
	 * 
	 * @param polygon
	 * @throws IOException
	 */
	public void flipFlowEdges(Collection<FeatureId> pathstoflip, Collection<FeatureId> processed) throws Exception{
		
		try(DefaultTransaction tx = new DefaultTransaction()){
			try {
				//do 3d first
				try(SimpleFeatureWriter writer = geopkg.writer(getEntry(Layer.EFLOWPATHS), false, Filter.INCLUDE, tx)){

					Name diratt = ChyfDataSource.findAttribute(writer.getFeatureType(), ChyfAttribute.DIRECTION);
					writer.getFeatureType().getGeometryDescriptor().getUserData().put(Hints.COORDINATE_DIMENSION, 3);
					
					while(writer.hasNext()) {
						SimpleFeature sf = writer.next();
						FeatureId fid = sf.getIdentifier();
						
						if (pathstoflip.contains(fid)) {
							LineString ls = ChyfDataSource.getLineString(sf);
							if (ls.getCoordinateSequence().getDimension() != 3) continue;
							ls = (LineString) ls.reverse();
							if (cIsMulti) {
								sf.setDefaultGeometry(ls.getFactory().createMultiLineString(new LineString[] {ls}));
							}else {
								sf.setDefaultGeometry(ls);
							}
							sf.setAttribute(diratt, DirectionType.KNOWN.getChyfValue());
							writer.write();
						}else if (processed.contains(fid)) {
							sf.setAttribute(diratt, DirectionType.KNOWN.getChyfValue());
							writer.write();
						}
					}
					
				}
				//do everything else
				try(SimpleFeatureWriter writer = geopkg.writer(getEntry(Layer.EFLOWPATHS), false, Filter.INCLUDE, tx)){

					Name diratt = ChyfDataSource.findAttribute(writer.getFeatureType(), ChyfAttribute.DIRECTION);
					writer.getFeatureType().getGeometryDescriptor().getUserData().put(Hints.COORDINATE_DIMENSION, 2);
					
					while(writer.hasNext()) {
						SimpleFeature sf = writer.next();
						FeatureId fid = sf.getIdentifier();
						
						if (pathstoflip.contains(fid)) {
							LineString ls = ChyfDataSource.getLineString(sf);
							if (ls.getCoordinateSequence().getDimension() == 3) continue;
							ls = (LineString) ls.reverse();
							if (cIsMulti) {
								sf.setDefaultGeometry(ls.getFactory().createMultiLineString(new LineString[] {ls}));
							}else {
								sf.setDefaultGeometry(ls);
							}
							sf.setAttribute(diratt, DirectionType.KNOWN.getChyfValue());
							writer.write();
						}else if (processed.contains(fid)) {
							sf.setAttribute(diratt, DirectionType.KNOWN.getChyfValue());
							writer.write();
						}
					}
				}
				
				tx.commit();
			}catch (IOException ex) {
				ex.printStackTrace();
				tx.rollback();
				throw ex;
			}
		}
	}
	
	/**
	 * Pass an empty collection to write any remaining features in the write cache
	 * 
	 * @param skeletons
	 * @throws IOException
	 */
	public synchronized void writeSkeletons(Collection<LineString> skeletons) throws IOException {
		if (skeletonWriteCache == null) skeletonWriteCache = new ArrayList<>();
		skeletonWriteCache.addAll(skeletons);
				
		if (skeletonWriteCache.size() > 1000 || skeletons.isEmpty()) {
			logger.info("Writing skeletons to geopackage");
			try(Transaction tx = new DefaultTransaction()) {	
				try(SimpleFeatureWriter fw = geopkg.writer(getEntry(Layer.EFLOWPATHS), true, Filter.EXCLUDE, tx)){
					//all skeletons are always 2d
					fw.getFeatureType().getGeometryDescriptor().getUserData().put(Hints.COORDINATE_DIMENSION, 2);
					
					Name diratt = ChyfDataSource.findAttribute(fw.getFeatureType(), ChyfAttribute.DIRECTION);
					Name eftypeatt = ChyfDataSource.findAttribute(fw.getFeatureType(), ChyfAttribute.EFTYPE);
					Name idatt = ChyfDataSource.findAttribute(fw.getFeatureType(), ChyfAttribute.INTERNAL_ID);
							
					for (LineString item : skeletonWriteCache) {
						SimpleFeature fs = fw.next();
						fs.setAttribute(eftypeatt, ((EfType)item.getUserData()).getChyfValue() );
						fs.setAttribute(diratt, DirectionType.UNKNOWN.getChyfValue());
						fs.setAttribute(idatt, UUID.randomUUID().toString());
						fs.setDefaultGeometry(item);
						fw.write();
					}
					tx.commit();
				} catch(Exception ioe) {
					tx.rollback();
					throw ioe;
				}
			}
			skeletonWriteCache.clear();
			logger.info("Finished skeleton write");
		}
	}
	
	/**
	 * Remove any existing skeleton lines in the dataset.
	 * If bankOnly is true than only existing bank skeletons
	 * are removed; otherwise all skeletons are removed
	 * @throws SQLException 
	 * 
	 */
	public void removeExistingSkeletons(boolean bankOnly) throws SQLException {
		Connection c = geopkg.getDataSource().getConnection();
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("DELETE FROM ");
			sb.append(getEntry(Layer.EFLOWPATHS).getTableName());
			sb.append(" WHERE ");
			sb.append(ChyfAttribute.EFTYPE.getFieldName());
			sb.append(" IN (" );
			sb.append(EfType.BANK.getChyfValue());
			if (!bankOnly) {
				sb.append(",");
				sb.append(EfType.SKELETON.getChyfValue());	
			}
			sb.append(")");
			
			c.createStatement().execute(sb.toString());
		}catch (Exception ex) {
			
		}
	}
	
	
	private void addDirectionAttribute() throws Exception{	
		String tablename = getEntry(Layer.EFLOWPATHS).getTableName();
		
		Connection c = geopkg.getDataSource().getConnection();
		String query = "ALTER TABLE " + tablename + " add column " + ChyfAttribute.DIRECTION.getFieldName()  + " integer ";
		c.createStatement().execute(query);
		
		query = "UPDATE TABLE " + tablename + " set " + ChyfAttribute.DIRECTION.getFieldName()  + " = " + DirectionType.UNKNOWN.getChyfValue();
		c.createStatement().execute(query);
		
		geopkg.close();
		read();
	}
	
	public void addRankAttribute() throws Exception{	
		String tablename = getEntry(Layer.EFLOWPATHS).getTableName();
		Connection c = geopkg.getDataSource().getConnection();
		String query = "ALTER TABLE " + tablename + " add column " + ChyfAttribute.RANK.getFieldName()  + " integer ";
		c.createStatement().execute(query);
		
		geopkg.close();
		read();
	}
	
	

}
