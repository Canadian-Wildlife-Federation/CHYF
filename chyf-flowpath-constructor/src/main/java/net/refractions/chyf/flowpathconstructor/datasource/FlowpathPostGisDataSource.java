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
package net.refractions.chyf.flowpathconstructor.datasource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
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
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureStore;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfPostGisDataSource;
import net.refractions.chyf.datasource.DirectionType;
import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.FlowDirection;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.ConstructionPoint;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.PolygonInfo;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkelLineString;

/**
 * Extension of the geopackage data source for flowpath computation
 * 
 * @author Emily
 *
 */
public class FlowpathPostGisDataSource extends ChyfPostGisDataSource implements IFlowpathDataSource{

	static final Logger logger = LoggerFactory.getLogger(FlowpathPostGisDataSource.class.getCanonicalName());
	
	private boolean cIsMulti = false;
	
	private List<SkelLineString> skeletonWriteCache;

	public static final String CONSTRUCTION_PNTS_TABLE = "construction_points";

	public FlowpathPostGisDataSource(String connectionString, String inschema, String outschema) throws Exception {

		super(connectionString, inschema, outschema);
		
		if (ChyfDataSource.findAttribute(getFeatureType(Layer.EFLOWPATHS), ChyfAttribute.DIRECTION) == null) {
			addDirectionAttribute();
		}
	}

	@Override
	protected void createWorkingTables() throws IOException {
		super.createWorkingTables();
		
		SimpleFeatureType stype = null;
		try{
			stype = workingDataStore.getSchema(CONSTRUCTION_PNTS_TABLE);
		}catch (Exception ex) {
			
		}
		if (stype == null) {
			StringBuilder sb = new StringBuilder();
			
			sb.append("CREATE TABLE " );
			sb.append(workingSchema + "." + CONSTRUCTION_PNTS_TABLE);
			sb.append("(");
			sb.append(getAoiFieldName(null) + " uuid not null references " + getTableName(Layer.AOI) + "(" + getAoiFieldName(Layer.AOI) + "),");
			sb.append(ChyfAttribute.INTERNAL_ID.getFieldName() + " uuid Primary Key,");
			sb.append(CATCHMENT_INTERNALID_ATTRIBUTE + " UUID,");
			sb.append(NODETYPE_ATTRIBUTE + " integer,");
			sb.append(ChyfAttribute.FLOWDIRECTION.getFieldName() + " integer,");
			sb.append(" the_geom GEOMETRY(POINT, 4326)");
			sb.append(") ");
			
		    
		    try {
		    	getConnection().createStatement().executeUpdate(sb.toString());
			} catch (SQLException e) {
				throw new IOException(e);
			}
		}
	}

	@Override
	public void setAoi(String aoi) throws IOException{
		super.setAoi(aoi);
		clearAoiOutputTables();
	}
	
	/**
	 * clears existing aoi data from working tables and 
	 * copies raw data into the working tables
	 * @throws IOException
	 */
	public void clearAoiOutputTables() throws IOException {
		
		Connection c = getConnection();
		
		StringBuilder sb = new StringBuilder();
    	sb.append("DELETE FROM ");
		sb.append(workingSchema + "." + CONSTRUCTION_PNTS_TABLE);
		sb.append(" WHERE aoi_id = ?");
		try(PreparedStatement ps = c.prepareStatement(sb.toString())){
			ps.setObject(1, aoiUuid);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new IOException(e);
		}

		    
	    //check if working tables exist;
	    List<Layer> toProcess = new ArrayList<>();
	    for (Layer l : Layer.values()) {
	    	if (l == Layer.AOI) continue;
	    	toProcess.add(l);
	    }
	    toProcess.add(Layer.AOI);
	    
	    for (Layer l : toProcess) {
	    	sb = new StringBuilder();
    		//delete everything from aoi
    		sb.append("DELETE FROM ");
    		sb.append( workingSchema + "." + getTypeName(l) );
    		sb.append(" WHERE ");
    		sb.append(getAoiFieldName(l));
    		sb.append(" = ? ");
	    		
    		try(PreparedStatement ps = c.prepareStatement(sb.toString())){
    			ps.setObject(1, aoiUuid);
    			ps.executeUpdate();
    		}catch(SQLException ex) {
    			throw new IOException(ex);
    		}
	    		
    		SimpleFeatureType rtype = rawDataStore.getSchema(getTypeName(l));
    		Name internalidatt = ChyfDataSource.findAttribute(rtype, ChyfAttribute.INTERNAL_ID);
    		
    		sb = new StringBuilder();
    		sb.append("INSERT INTO ");
    		sb.append(workingSchema + "." + getTypeName(l));
    		sb.append(" SELECT ");
    		if (l != Layer.AOI && internalidatt == null) {
    			sb.append("uuid_generate_v4() as " + ChyfAttribute.INTERNAL_ID.getFieldName());
    			sb.append(",");
    		}
    		sb.append(" a.* ");
    		sb.append(" FROM " + rawSchema + "." + getTypeName(l) + " a ");
    		sb.append(" WHERE ");
    		sb.append(getAoiFieldName(l));
    		sb.append(" = " );
    		sb.append (" ? ");
    		
    		try(PreparedStatement ps = c.prepareStatement(sb.toString())){
    			ps.setObject(1, aoiUuid);
    			ps.executeUpdate();
    		}catch(SQLException ex) {
    			throw new IOException(ex);
    		}	    		   
	    }
	}
	
	@Override
	public void updateCoastline(FeatureId fid, LineString newls, Transaction tx) throws IOException {
		
		try(FeatureWriter<SimpleFeatureType, SimpleFeature> writer = workingDataStore.getFeatureWriter(getTypeName(Layer.SHORELINES), ff.id(fid), tx)){
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
	@Override
	public List<Point> getTerminalNodes() throws Exception{
		List<Point> pnt = new ArrayList<>();
		try(FeatureReader<SimpleFeatureType, SimpleFeature> reader = getFeatureReader(Layer.TERMINALNODES, getAoiFilter(Layer.TERMINALNODES), Transaction.AUTO_COMMIT)){
			Name flowdirAttribute = ChyfDataSource.findAttribute(reader.getFeatureType(), ChyfAttribute.FLOWDIRECTION);
			while(reader.hasNext()){
				SimpleFeature point = reader.next();
				Point pg = ChyfDataSource.getPoint(point);
				pg.setUserData(FlowDirection.parseValue( ((Number)point.getAttribute(flowdirAttribute)).intValue() ));
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
	@Override
	public Set<Coordinate> getOutputConstructionPoints() throws Exception{
		Set<Coordinate> pnt = new HashSet<>();
		
		Filter filter = ff.equals(ff.property(ChyfAttribute.FLOWDIRECTION.getFieldName()), ff.literal(FlowDirection.OUTPUT.getChyfValue()));
		
		Query query = new Query( CONSTRUCTION_PNTS_TABLE, ff.and(filter, getAoiFilter(null)));
	    try(FeatureReader<SimpleFeatureType, SimpleFeature> reader =  workingDataStore.getFeatureReader(query, Transaction.AUTO_COMMIT )){
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
	@Override
	public void createConstructionsPoints(List<ConstructionPoint> points) throws IOException {
		//delete existing features
		StringBuilder sb = new StringBuilder();
		sb.append("DELETE FROM ");
		sb.append(workingSchema + "." + CONSTRUCTION_PNTS_TABLE);
		sb.append (" WHERE ");
		sb.append(getAoiFieldName(null));
		sb.append(" = ?");
		
		try(PreparedStatement ps = getConnection().prepareStatement(sb.toString())){
			ps.setObject(1, aoiUuid);
			ps.executeUpdate();
		}catch (SQLException ex) {
			throw new IOException(ex);
		}
		
		SimpleFeatureType pntType = workingDataStore.getSchema(CONSTRUCTION_PNTS_TABLE);
		List<SimpleFeature> features = new ArrayList<>();
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(pntType);
		GeometryFactory gf = new GeometryFactory();

		for (ConstructionPoint pnt : points) {
			String uuid = UUID.randomUUID().toString();
			featureBuilder.set("the_geom", gf.createPoint(pnt.getCoordinate()));
			featureBuilder.set(getAoiFieldName(null), aoiUuid);
			featureBuilder.set(NODETYPE_ATTRIBUTE, pnt.getType().getChyfValue());
			featureBuilder.set(ChyfAttribute.FLOWDIRECTION.getFieldName(), pnt.getDirection().getChyfValue());
			featureBuilder.set(ChyfAttribute.INTERNAL_ID.getFieldName(), uuid);
			featureBuilder.set(CATCHMENT_INTERNALID_ATTRIBUTE, pnt.getWaterbodyInfo().getCatchmentId());
			SimpleFeature sf = featureBuilder.buildFeature(uuid);
			features.add(sf);
		}
						
		try(Transaction t1 = new DefaultTransaction("transaction 1")){
			FeatureStore<SimpleFeatureType, SimpleFeature> store = (FeatureStore<SimpleFeatureType, SimpleFeature>) workingDataStore.getFeatureSource(CONSTRUCTION_PNTS_TABLE);
			store.setTransaction(t1);
			store.addFeatures(DataUtilities.collection(features));
			t1.commit();
		}
	}

	/**
	 * Reads construction points from geopackage for the catchment
	 * associated with the provided catchment id.
	 * 
	 * @param catchmentId
	 * @return
	 * @throws IOException
	 */
	@Override
	public synchronized List<ConstructionPoint> getConstructionsPoints(Object catchmentId) throws IOException{
		Filter filter = ff.equals(ff.property(CATCHMENT_INTERNALID_ATTRIBUTE), ff.literal(catchmentId));
		List<ConstructionPoint> points = new ArrayList<>();
		
		Query query = new Query( CONSTRUCTION_PNTS_TABLE, ff.and(filter,getAoiFilter(null)));
		
		try(FeatureReader<SimpleFeatureType, SimpleFeature> reader =  workingDataStore.getFeatureReader(query, Transaction.AUTO_COMMIT)){
        	while(reader.hasNext()) {
        		SimpleFeature sf = reader.next();
        		
        		Coordinate c = ((Point)sf.getDefaultGeometry()).getCoordinate();
        		NodeType type = NodeType.parseValue( ((Number)sf.getAttribute(NODETYPE_ATTRIBUTE)).intValue());
        		FlowDirection fd = FlowDirection.parseValue(((Number)sf.getAttribute(ChyfAttribute.FLOWDIRECTION.getFieldName())).intValue());
        		ConstructionPoint p = new ConstructionPoint(c, type, fd, null);
        		points.add(p);
        	}
        }
        return points;
	}
	

	
	/**
	 * Updates the geometry of the give polygon.  The polygon must
	 * have a user data set to PolygonInfo class.
	 * 
	 * @param polygon
	 * @throws IOException
	 */
	@Override
	public void updateWaterbodyGeometries(Collection<Polygon> polygons) throws IOException{
		if (polygons.isEmpty()) return;

		try(DefaultTransaction tx = new DefaultTransaction()){
			try {
				for (Polygon polygon : polygons) {
					FeatureId fid = PolygonInfo.getFeatureId(polygon);
					try(FeatureWriter<SimpleFeatureType, SimpleFeature> writer = workingDataStore.getFeatureWriter(getTypeName(Layer.ECATCHMENTS), 
							ff.id(fid), tx)){
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
							throw new IOException("No feature with internal_id " + fid.toString() + "found to update");
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
	@Override
	public void writeRanks(Map<FeatureId, RankType> ranks) throws Exception{
		
		try(DefaultTransaction tx = new DefaultTransaction()){
			
			try(FeatureWriter<SimpleFeatureType, SimpleFeature> writer = workingDataStore.getFeatureWriter(getTypeName(Layer.EFLOWPATHS), Filter.INCLUDE, tx)){
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
	@Override
	public void flipFlowEdges(Collection<FeatureId> pathstoflip, Collection<FeatureId> processed) throws Exception{
		
		try(DefaultTransaction tx = new DefaultTransaction()){
			try {
				//do 3d first
				
				try(FeatureWriter<SimpleFeatureType, SimpleFeature> writer = workingDataStore.getFeatureWriter(getTypeName(Layer.EFLOWPATHS), Filter.INCLUDE, tx)){

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
				tx.commit();
			}catch (Exception ex) {
				tx.rollback();
				throw ex;
			}
		}
		try(DefaultTransaction tx = new DefaultTransaction()){
			try {
				//do everything else
				try(FeatureWriter<SimpleFeatureType, SimpleFeature> writer = workingDataStore.getFeatureWriter(getTypeName(Layer.EFLOWPATHS), Filter.INCLUDE, tx)){

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
	@Override
	public synchronized void writeSkeletons(Collection<SkelLineString> skeletons) throws IOException {
		
		if (skeletonWriteCache == null) skeletonWriteCache = new ArrayList<>();
		skeletonWriteCache.addAll(skeletons);
				
		if (skeletonWriteCache.size() > 1000 || skeletons.isEmpty()) {
			logger.info("Writing skeletons to database");
	
			
			List<SimpleFeature> features = new ArrayList<>();
			
			SimpleFeatureType skelType = getFeatureType(Layer.EFLOWPATHS);
			SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(skelType);

			Name diratt = ChyfDataSource.findAttribute(skelType, ChyfAttribute.DIRECTION);
			Name eftypeatt = ChyfDataSource.findAttribute(skelType, ChyfAttribute.EFTYPE);
			Name idatt = ChyfDataSource.findAttribute(skelType, ChyfAttribute.INTERNAL_ID);
			Name rankatt = ChyfDataSource.findAttribute(skelType, ChyfAttribute.RANK);
							
			for (SkelLineString item : skeletonWriteCache) {
						
				UUID uuid = UUID.randomUUID();
				featureBuilder.set(getAoiFieldName(Layer.EFLOWPATHS), aoiUuid);
				featureBuilder.set(eftypeatt, item.getEfType().getChyfValue() );
				featureBuilder.set(diratt, DirectionType.UNKNOWN.getChyfValue());
				featureBuilder.set(idatt, uuid);
						
				if (rankatt != null && item.getEfType() == EfType.BANK) {
					featureBuilder.set(rankatt, RankType.PRIMARY.getChyfValue());
				}else if (rankatt != null) {
					featureBuilder.set(rankatt, null);
				}
				if (diratt != null && item.getEfType() == EfType.BANK) {
					//bank flowpaths are directionalized
					featureBuilder.set(diratt, DirectionType.KNOWN.getChyfValue());
				}else if (diratt != null) {
					featureBuilder.set(diratt, null);
				}						
				//copy over existing attributes
				if (item.getFeature() != null) {
					featureBuilder.set(diratt, DirectionType.KNOWN.getChyfValue());
					for (AttributeDescriptor att : skelType.getAttributeDescriptors()) {
						if (att.getName().equals(diratt) || att.getName().equals(eftypeatt) 
								|| att.getName().equals(idatt) || att.getName().equals(rankatt)) {
							continue;
						}
						featureBuilder.set(att.getName(), item.getFeature().getAttribute(att.getName()));
					}
				}
				
				featureBuilder.set(skelType.getGeometryDescriptor().getName(), item.getLineString());
				features.add(featureBuilder.buildFeature(uuid.toString()));
			}
		
			
			try(Transaction t1 = new DefaultTransaction("transaction 1")){
				try {
					SimpleFeatureStore store = ((SimpleFeatureStore)workingDataStore.getFeatureSource(getTypeName(Layer.EFLOWPATHS)));
					store.setTransaction(t1);
					store.addFeatures(DataUtilities.collection(features));
					t1.commit();
				}catch (Exception ex) {
					t1.rollback();
					throw new IOException(ex);
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
	@Override
	public void removeExistingSkeletons(boolean bankOnly) throws SQLException {
		try {
			Connection c = getConnection();
			
			StringBuilder sb = new StringBuilder();
			sb.append("DELETE FROM ");
			sb.append(getTableName(Layer.EFLOWPATHS));
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
		}catch (IOException ex) {
			throw new SQLException(ex);
		}
		
	}
	
	private void addDirectionAttribute() throws Exception{
		
		String tablename = getTableName(Layer.EFLOWPATHS);

		Connection c = getConnection();
		String query = "ALTER TABLE " + tablename + " add column " + ChyfAttribute.DIRECTION.getFieldName()  + " integer ";
		c.createStatement().execute(query);
		
		query = "UPDATE TABLE " + tablename + " set " + ChyfAttribute.DIRECTION.getFieldName()  + " = " + DirectionType.UNKNOWN.getChyfValue();
		c.createStatement().execute(query);
		
		resetWorkingDataStore();
	}
	
	@Override
	public void addRankAttribute() throws Exception{
		if (ChyfDataSource.findAttribute(getFeatureType(Layer.EFLOWPATHS), ChyfAttribute.RANK) != null) return;

		String tablename = getTableName(Layer.EFLOWPATHS);

		Connection c = getConnection();
		String query = "ALTER TABLE " + tablename + " add column " + ChyfAttribute.RANK.getFieldName()  + " integer ";
		c.createStatement().execute(query);
		
		resetWorkingDataStore();
	}

}
