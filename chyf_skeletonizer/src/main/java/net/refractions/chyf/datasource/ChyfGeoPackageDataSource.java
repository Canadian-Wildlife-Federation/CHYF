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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.geometry.jts.Geometries;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.skeletonizer.points.ConstructionPoint;
import net.refractions.chyf.skeletonizer.points.PolygonInfo;

/**
 * Reads a geopackage input dataset.  
 * 
 * @author Emily
 *
 */
public class ChyfGeoPackageDataSource implements ChyfDataSource{

	static final Logger logger = LoggerFactory.getLogger(ChyfDataSource.class.getCanonicalName());
	
	private static FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();


	public static final String CONSTRUCTION_PNTS_LAYER = "ConstructionPoints";
	
	public static final String POLYID_ATTRIBUTE = "poly_id";
	public static final String POINTID_ATTRIBUTE = "point_id";
	public static final String NODETYPE_ATTRIBUTE = "node_type";
	
	private Path geopackageFile ;
	private GeoPackage geopkg;
	
	private FeatureEntry catchments;
	private FeatureEntry flowpaths;
	
	private boolean cIsMulti = false;
	
	private List<LineString> skeletonWriteCache;
	
	private List<String> wbToProcess;
	
	public ChyfGeoPackageDataSource(Path geopackageFile) throws Exception {
		this.geopackageFile = geopackageFile;
		read();
	}
	
	public CoordinateReferenceSystem getCoordinateReferenceSystem() {
		return flowpaths.getBounds().getCoordinateReferenceSystem();
	}
	
	private void read() throws Exception {
		geopkg = new GeoPackage(geopackageFile.toFile());
		geopkg.init();
		
		//TODO: report bug
//		List<Entry> items = geopkg.contents();
		
	    logger.info("waterbodies");
	    catchments = geopkg.feature(Layer.CATCHMENT.getLayerName());
	    //catchments.setM(false); //4d not supported
	    
        if (getCatchments().getGeometryType()== Geometries.MULTIPOLYGON) cIsMulti = true;
        
		logger.info("flowpaths");
		flowpaths = geopkg.feature(Layer.FLOWPATH.getLayerName());
		
		boolean add = true;
		try(SimpleFeatureReader reader = geopkg.reader(flowpaths, Filter.EXCLUDE, null)){
			if (ChyfDataSource.findAttribute(reader.getFeatureType(),  Attribute.DIRECTION) != null) {
				add = false;
			}
		}
		if (add) { addDirectionAttribute(); }
		
		
//		try(SimpleFeatureReader rreader = geopkg.reader(geopkg.feature("EFlowpaths"), Filter.INCLUDE, null)){
//
//			while(rreader.hasNext()) {
//				SimpleFeature ssf = rreader.next();
//				LineString ls3 = ChyfDataSource.getLineString(ssf);
//				for (Coordinate c : ls3.getCoordinates()) System.out.println(c.x +":" + c.y +":"+c.z);
////				System.out.println(ChyfDataSource.getLineString(ssf).toText());
//			}
//		}
		
//		flowpaths.setZ(true);flowpaths.setM(true);
		
//		 Query q = new Query(flowpaths.getTableName());
//        q.setFilter(Filter.INCLUDE);
//        q.getHints().put(Hints.JTS_COORDINATE_SEQUENCE_FACTORY,ChyfCoordinateSequenceFactory.instance());
//
////        Features.simple(geopkg.getDataSource().getFeatureReader(q, null))
//		try(SimpleFeatureReader reader = geopkg.reader(flowpaths, Filter.INCLUDE, null)){
//			
////			reader.getFeatureType().getGeometryDescriptor().getUserData().put(Hints.COORDINATE_DIMENSION, 4);
////			
////			reader.getFeatureType().getGeometryDescriptor().getUserData().put(Hints.JTS_COORDINATE_SEQUENCE_FACTORY,ChyfCoordinateSequenceFactory.instance());
////			reader.getFeatureType().getGeometryDescriptor().getUserData().put(Hints.CS_FACTORY,ChyfCoordinateSequenceFactory.instance());
//
//			while(reader.hasNext()) {
//				SimpleFeature sf = reader.next();
//				System.out.println(ChyfDataSource.getLineString(sf).toText());
//			}
//		}

	}
	
	private FeatureEntry getCatchments() throws IOException {
		return catchments;
	}
	private FeatureEntry getFlowpaths() throws IOException {
		return flowpaths;
	}
	
	public FeatureEntry getEntry(Layer layer) throws IOException {
		return geopkg.feature(layer.getLayerName());
	}
	//TODO: make sure z and m values are set it required
	public void updateCoastline(FeatureId fid, LineString newls) throws IOException {
		try(DefaultTransaction tx = new DefaultTransaction()){
			try {
				try(SimpleFeatureWriter writer = geopkg.writer(getEntry(Layer.COASTLINE), false, ff.id(fid), tx)){
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
				tx.commit();
			}catch (IOException ex) {
				tx.rollback();
				throw ex;
			}
		}
	}
	
	/**
	 * All points from the boundary layer with the userdata set to the FlowDirection
	 * type
	 * 
	 * @return
	 * @throws Exception
	 */
	public List<Point> getBoundaries() throws Exception{
		List<Point> pnt = new ArrayList<>();
		try(SimpleFeatureReader reader = query(null, geopkg.feature(Layer.BOUNDARY_POINTS.getLayerName()), null)){
			Name ioatt = ChyfDataSource.findAttribute(reader.getFeatureType(), Attribute.IOTYPE);
			while(reader.hasNext()){
				SimpleFeature point = reader.next();
				Point pg = ChyfDataSource.getPoint(point);
				pg.setUserData(IoType.parseType( (Integer)point.getAttribute(ioatt)) );
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
		
		Filter filter = ff.equals(ff.property(Attribute.IOTYPE.getFieldName()), ff.literal(ConstructionPoint.Direction.OUT.modelValue()));
		try(SimpleFeatureReader reader = query(null, geopkg.feature(CONSTRUCTION_PNTS_LAYER), filter)){
			while(reader.hasNext()){
				SimpleFeature point = reader.next();
				pnt.add(ChyfDataSource.getPoint(point).getCoordinate());
			}
		}
		return pnt;
	}
	
	public List<LineString> getCoastline() throws Exception{
		List<LineString> coastline = new ArrayList<>();
		if (geopkg.feature(Layer.COASTLINE.getLayerName()) != null){
			try(SimpleFeatureReader reader = query(null, geopkg.feature(Layer.COASTLINE.getLayerName()), null)){
				while(reader.hasNext()){
					SimpleFeature aoi = reader.next();
					LineString ring = ChyfDataSource.getLineString(aoi);
					coastline.add(ring);
				}
			}
		}
		return coastline;
	}
	
	/**
	 * Create a new layer for skeleton construction points
	 * @param points
	 * @throws IOException
	 */
	public void createConstructionsPoints(List<ConstructionPoint> points) throws IOException {
		
		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
		builder.add("the_geom", Point.class, getCatchments().getBounds().getCoordinateReferenceSystem());
		builder.add(POINTID_ATTRIBUTE, Integer.class);
		builder.add(POLYID_ATTRIBUTE, Integer.class);
		builder.add("node_type", Integer.class);
		builder.add(Attribute.IOTYPE.getFieldName(), Integer.class);
		builder.setName("ConstructionPoints");
		SimpleFeatureType pntType = builder.buildFeatureType();
		
		FeatureEntry entry = new FeatureEntry();
		entry.setTableName(CONSTRUCTION_PNTS_LAYER);
		entry.setM(false);
		List<SimpleFeature> features = new ArrayList<>();
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(pntType);
		GeometryFactory gf = new GeometryFactory();
		int pointid = 1;
		for (ConstructionPoint pnt : points) {
			featureBuilder.set("the_geom", gf.createPoint(pnt.getCoordinate()));
			featureBuilder.set(NODETYPE_ATTRIBUTE, pnt.getType().modelValue());
			featureBuilder.set(Attribute.IOTYPE.getFieldName(), pnt.getDirection().modelValue());
			featureBuilder.set(POINTID_ATTRIBUTE, pointid);
			featureBuilder.set(POLYID_ATTRIBUTE, pnt.getWaterbodyInfo().getPolygonId());
			SimpleFeature sf = featureBuilder.buildFeature(String.valueOf(pointid++));
			features.add(sf);
		}
		geopkg.add(entry, DataUtilities.collection(features));
	}

	/**
	 * Reads construction points from geopackage 
	 * 
	 * @param polyId
	 * @return
	 * @throws IOException
	 */
	public synchronized List<ConstructionPoint> getConstructionsPoints(int polyId) throws IOException{
		Filter filter = ff.equals(ff.property(POLYID_ATTRIBUTE), ff.literal(polyId));
		List<ConstructionPoint> points = new ArrayList<>();
		try(SimpleFeatureReader reader = geopkg.reader(geopkg.feature(CONSTRUCTION_PNTS_LAYER), filter, null)){
        	while(reader.hasNext()) {
        		SimpleFeature sf = reader.next();
        		
        		Coordinate c = ((Point)sf.getDefaultGeometry()).getCoordinate();
        		ConstructionPoint.Type type = ConstructionPoint.Type.getType((Integer)sf.getAttribute(NODETYPE_ATTRIBUTE));
        		ConstructionPoint.Direction fd = ConstructionPoint.Direction.getDirection((Integer)sf.getAttribute(Attribute.IOTYPE.getFieldName()));
        		ConstructionPoint p = new ConstructionPoint(c, type, fd, null);
        		points.add(p);
        	}
        }
        return points;
	}
	
	
	public Filter getWbTypeFilter() {
		return ff.equals(ff.property(Attribute.ECTYPE.getFieldName()), ff.literal(EcType.WATER.getType()));
	}
	
	public SimpleFeatureReader getCoastlines(ReferencedEnvelope bounds) throws IOException{
		FeatureEntry cs = geopkg.feature(Layer.COASTLINE.getLayerName());
		if (cs == null) return null;
		return query(bounds, cs, null);
	}
	
	public SimpleFeatureReader getWaterbodies(ReferencedEnvelope bounds) throws IOException{
		return query(bounds, getCatchments(), getWbTypeFilter());
	}
	
	public SimpleFeatureReader getFlowpaths(ReferencedEnvelope bounds) throws IOException{
		return query(bounds, getFlowpaths(), null);
	}

	
	public SimpleFeatureReader query(ReferencedEnvelope bounds, FeatureEntry source, Filter filter) throws IOException {
		if (source == null) throw new IOException("Required dataset not found.");
		if (bounds == null) {
			return geopkg.reader(source, filter, null);
		}
		
		String geom = source.getGeometryColumn();
		Filter filter1 = ff.bbox(ff.property(geom), bounds);
		Filter filter2 = ff.intersects(ff.property(geom), ff.literal(JTS.toGeometry(bounds)));
		List<Filter> all = new ArrayList<>();
		if (filter != null) all.add(filter);
		all.add(filter1);
		all.add(filter2);
		Filter filters = ff.and(all);
            
        return geopkg.reader(source, filters, null);
		
	}
	
	public SimpleFeatureReader getWaterbodies() throws IOException{
		return geopkg.reader(getCatchments(), getWbTypeFilter(), null);
//		return geopkg.reader(catchments, Filter.INCLUDE, null);
	}

	/**
	 * 
	 * @return gets the next waterbody that hasn't been processed updating the
	 * process flag to true
	 * 
	 * @throws IOException
	 */
	public SimpleFeature getNextWaterbody() throws IOException {
		return getNextWaterbody(null);
	}
	
	/**
	 * 
	 * @return gets the next waterbody that hasn't been processed updating the
	 * process flag to true, and the waterbody id to the give id if provided.  If null the waterbody
	 * id is not updated
	 * 
	 * @throws IOException
	 */
	public synchronized SimpleFeature getNextWaterbody(Integer polyId) throws IOException {
		if (wbToProcess.isEmpty()) return null;
		
		String fid = wbToProcess.get(0);
		
		Filter featureFilter = ff.id(new FeatureIdImpl(fid));
		
		SimpleFeature next = null;
		try(SimpleFeatureReader rr = geopkg.reader(getCatchments(), featureFilter, null)){
			//rr.getFeatureType().getGeometryDescriptor().getUserData().put(Hints.COORDINATE_DIMENSION, 3);
			if (rr.hasNext()) next = rr.next();
		}
		if (next == null) throw new IllegalStateException("Waterbody with feature id " + fid + " not found.");
//		setProcessed(next.getIdentifier(), polyId);
		wbToProcess.remove(fid.toString());

		return next;
	}

//	/**
//	 * Updates the waterbody layers, setting the given feature id
//	 * processed flag to true. If the polyId provided is not null then
//	 * the polygon id is also updated
//	 * 
//	 * @param fid feature id to update
//	 * @param polyId the polygon id; if null not updated
//	 * @throws IOException
//	 */
//	private void setProcessed(FeatureId fid, Integer polyId) throws IOException{
//		wbToProcess.remove(fid.toString());
//		
//		if (polyId == null) return;
//		
//		//update polyid
//		Filter filter = ff.id(fid);
//		try(SimpleFeatureWriter writer = geopkg.writer(getCatchments(), false, filter, null)){
//			writer.hasNext();
//			SimpleFeature sf = writer.next();
//			sf.setAttribute(POLYID_ATTRIBUTE, polyId);
//			writer.write();
//		}		
//	}
	
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
					try(SimpleFeatureWriter writer = geopkg.writer(getCatchments(), false, ff.id(fid), tx)){
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
			try(SimpleFeatureWriter writer = geopkg.writer(getFlowpaths(), false, Filter.INCLUDE, tx)){
				Name rankatt = ChyfDataSource.findAttribute(writer.getFeatureType(), Attribute.RANK);
				while(writer.hasNext()) {
					SimpleFeature sf = writer.next();
					FeatureId fid = sf.getIdentifier();
					RankType rank = ranks.get(fid);
					if (rank == null) {
						//bank
						rank = RankType.PRIMARY;
					}
					
					sf.setAttribute(rankatt, rank.getType());
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
				try(SimpleFeatureWriter writer = geopkg.writer(getFlowpaths(), false, Filter.INCLUDE, tx)){

					Name diratt = ChyfDataSource.findAttribute(writer.getFeatureType(), Attribute.DIRECTION);
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
							sf.setAttribute(diratt, DirectionType.KNOWN.type);
							writer.write();
						}else if (processed.contains(fid)) {
							sf.setAttribute(diratt, DirectionType.KNOWN.type);
							writer.write();
						}
					}
					
				}
				//do everything else
				try(SimpleFeatureWriter writer = geopkg.writer(getFlowpaths(), false, Filter.INCLUDE, tx)){

					Name diratt = ChyfDataSource.findAttribute(writer.getFeatureType(), Attribute.DIRECTION);
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
							sf.setAttribute(diratt, DirectionType.KNOWN.type);
							writer.write();
						}else if (processed.contains(fid)) {
							sf.setAttribute(diratt, DirectionType.KNOWN.type);
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
				try(SimpleFeatureWriter fw = geopkg.writer(getFlowpaths(), true, Filter.EXCLUDE, tx)){
					//all skeletons are always 2d
					fw.getFeatureType().getGeometryDescriptor().getUserData().put(Hints.COORDINATE_DIMENSION, 2);
					
					Name diratt = ChyfDataSource.findAttribute(fw.getFeatureType(), Attribute.DIRECTION);
					Name eftypeatt = ChyfDataSource.findAttribute(fw.getFeatureType(), Attribute.EFTYPE);
							
					for (LineString item : skeletonWriteCache) {
						SimpleFeature fs = fw.next();
						fs.setAttribute(eftypeatt, ((EfType)item.getUserData()).getType() );
						fs.setAttribute(diratt, DirectionType.UNKNOWN.type);
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
			sb.append(getFlowpaths().getTableName());
			sb.append(" WHERE ");
			sb.append(Attribute.EFTYPE.getFieldName());
			sb.append(" IN (" );
			sb.append(EfType.BANK.getType());
			if (!bankOnly) {
				sb.append(",");
				sb.append(EfType.SKELETON.getType());	
			}
			sb.append(")");
			
			c.createStatement().execute(sb.toString());
		}catch (Exception ex) {
			
		}
	}
	
	/**
	 * Gets aoi layer as a list of polygons
	 * @return
	 * @throws Exception
	 */
	public List<Polygon> getAoi() throws Exception{
		List<Polygon> aois = new ArrayList<>();
		try(SimpleFeatureReader reader = geopkg.reader(getEntry(Layer.AOI), Filter.INCLUDE, null)){
			while(reader.hasNext()) {
				SimpleFeature fs = reader.next();
				Polygon p = ChyfDataSource.getPolygon(fs);
				aois.add(p);
			}
		}
		return aois;
	}
	/**
	 * Creates a list of waterbodies to process.  Only includes
	 * waterbodies that are inside the aoi.
	 * 
	 * @throws SQLException
	 */
	public void addProcessedAttribute() throws Exception{
		wbToProcess = new ArrayList<>();
		
		List<Polygon> aois = getAoi();
		List<PreparedPolygon> aoispp = new ArrayList<>();
		for (Polygon p : aois) {
			aoispp.add(new PreparedPolygon(p));
		}
		try(SimpleFeatureReader reader = getWaterbodies()){
			while(reader.hasNext()) {
				SimpleFeature fs = reader.next();
				Polygon p = ChyfDataSource.getPolygon(fs);
				for (PreparedPolygon aoi:aoispp) {
					if (aoi.intersects(p) && p.relate(aoi.getGeometry(),"2********")) {
						wbToProcess.add(fs.getID());
						break;
					}
				}
			}
		}
	}

	/**
	 * Add a poly_id to the waterbodies table
	 * 
	 * @throws SQLException
	 */
	public void addPolygonIdAttribute() throws Exception{
		Connection c = geopkg.getDataSource().getConnection();
		String query = "ALTER TABLE " + getCatchments().getTableName() + " add column " + POLYID_ATTRIBUTE + " integer ";
		c.createStatement().execute(query);
		
		geopkg.close();
		read();
		
		//add poly id attribute to all waterbodies
		int polyId = 1;
		try(Transaction tx = new DefaultTransaction()) {	
			try(SimpleFeatureWriter writer = geopkg.writer(getCatchments(), false, Filter.INCLUDE, tx)){
				while(writer.hasNext()) {
					SimpleFeature sf = writer.next();
					sf.setAttribute(POLYID_ATTRIBUTE, polyId);
					polyId++;
					writer.write();
				}
			}
			tx.commit();
		}
	}
	
	public void addDirectionAttribute() throws Exception{	
		Connection c = geopkg.getDataSource().getConnection();
		String query = "ALTER TABLE " + getFlowpaths().getTableName() + " add column " + Attribute.DIRECTION.getFieldName()  + " integer ";
		c.createStatement().execute(query);
		
		query = "UPDATE TABLE " + getFlowpaths().getTableName() + " set " + Attribute.DIRECTION.getFieldName()  + " = " + DirectionType.UNKNOWN.type;
		c.createStatement().execute(query);
		
		geopkg.close();
		read();
	}
	
	public void addRankAttribute() throws Exception{	
		Connection c = geopkg.getDataSource().getConnection();
		String query = "ALTER TABLE " + getFlowpaths().getTableName() + " add column " + Attribute.RANK.getFieldName()  + " integer ";
		c.createStatement().execute(query);
		
		geopkg.close();
		read();
	}
	
	@Override
	public void close() throws Exception {
		geopkg.close();
	}
	
	
	public static final void deleteOutputFile(Path outputFile) throws Exception{
		if (Files.exists(outputFile)) {
			Files.delete(outputFile);
			//also delete -shm and -wal if files
			Path p = outputFile.getParent().resolve(outputFile.getFileName().toString() + "-shm");
			if (Files.exists(p)) Files.delete(p);
			
			p = outputFile.getParent().resolve(outputFile.getFileName().toString() + "-wal");
			if (Files.exists(p)) Files.delete(p);
		}
	}

}
