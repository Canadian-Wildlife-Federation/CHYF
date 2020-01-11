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
import java.util.List;
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
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.skeletonizer.points.BoundaryEdge;
import net.refractions.chyf.skeletonizer.points.PolygonInfo;
import net.refractions.chyf.skeletonizer.points.SkeletonPoint;

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
	
	public enum FlowDirection{
		IN(1),
		OUT(2),
		UNKNOWN(3);
		
		private int modelValue;
		
		private FlowDirection(int modelValue) {
			this.modelValue = modelValue;
		}
		
		public int modelValue() {
			return this.modelValue;
		}
		
		public static FlowDirection getDirection(int value) throws IOException{
			for (FlowDirection d : FlowDirection.values()) {
				if (d.modelValue == value) return d;
			}
			throw new IOException("Flow direction of " + value + " is invalid");
		}
	}
	
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
		
	    logger.info("Reading waterbodies");
	    catchments = geopkg.feature(Layer.CATCHMENT.getLayerName());
	    catchments.setM(false); //4d not supported
        if (getCatchments().getGeometryType()== Geometries.MULTIPOLYGON) cIsMulti = true;
        
		logger.info("Reading flowpaths");
		flowpaths = geopkg.feature(Layer.FLOWPATH.getLayerName());
	}
	
	private FeatureEntry getCatchments() throws IOException {
//		return geopkg.feature(Layer.CATCHMENT.getLayerName());
		return catchments;
	}
	private FeatureEntry getFlowpaths() throws IOException {
//		return geopkg.feature(Layer.FLOWPATH.getLayerName());
		return flowpaths;
	}
	
	public List<BoundaryEdge> getBoundary() throws Exception{
		//TODO: coastline
		//intersect aoi with waterbodies
		List<BoundaryEdge> edges = new ArrayList<>();
		
		List<LineString> common = new ArrayList<>();
		
		List<LineString> tocheck = new ArrayList<>();
		
		try(SimpleFeatureReader reader = query(null, geopkg.feature(Layer.AOI.getLayerName()), null)){
			while(reader.hasNext()){
				SimpleFeature aoi = reader.next();
				//TODO: aoi must be a simple polygon with no holes
				Polygon pg = ChyfDataSource.getPolygon(aoi);
				LineString ring = pg.getExteriorRing();
				tocheck.add(ring);
			}
		}
		if (geopkg.feature(Layer.COASTLINE.getLayerName()) != null){
			try(SimpleFeatureReader reader = query(null, geopkg.feature(Layer.COASTLINE.getLayerName()), null)){
				while(reader.hasNext()){
					SimpleFeature aoi = reader.next();
					LineString ring = ChyfDataSource.getLineString(aoi);
					tocheck.add(ring);
				}
			}
		}

		//intersect with waterbodies
		try(SimpleFeatureReader wbreader = getWaterbodies()){
			while(wbreader.hasNext()) {
				SimpleFeature ww = wbreader.next();
				Polygon wb = ChyfDataSource.getPolygon(ww);
		
				for (LineString ring : tocheck) {
					if (!ring.getEnvelopeInternal().intersects(wb.getEnvelopeInternal())) continue;
					Geometry t = ring.intersection(wb);
					if (t.isEmpty()) continue;
					if (t instanceof LineString) {
						common.add((LineString)t);
					}else if (t instanceof MultiLineString) {
						for (int i = 0; i < ((MultiLineString)t).getNumGeometries(); i ++) {
							common.add((LineString)t.getGeometryN(i));
						}
					}else if (t instanceof Point) {
						//ignore
					}else {
						throw new RuntimeException("The intersection of aoi and waterbodies returns invalid geometry type ("+ t.getGeometryType() + ")");
					}		
				}
			}
		}
		
		
		List<Point> inoutpoints = new ArrayList<>();
		if (geopkg.feature(Layer.BOUNDARY_POINTS.getLayerName()) != null){
			try(SimpleFeatureReader reader = query(null, geopkg.feature(Layer.BOUNDARY_POINTS.getLayerName()), null)){
				while(reader.hasNext()){
					SimpleFeature point = reader.next();
					Point pg = ChyfDataSource.getPoint(point);
					pg.setUserData(FlowDirection.getDirection((Integer)point.getAttribute(Attribute.IOTYPE.getFieldName())));
					inoutpoints.add(pg);
				}
			}
		}
		
		LineMerger m = new LineMerger();
		m.add(common);
		Collection<?> items = m.getMergedLineStrings();
		
		for (Object x : items) {
			if (x instanceof LineString) {
				//find the nearest point within 0.004 units
				LineString ls = (LineString)x;
				
				Point found = null;
				for (Coordinate c : ls.getCoordinates()) {
					for (Point p : inoutpoints) {
						if (c.equals2D(p.getCoordinate())) {
							found = p;
							break;
						}
					}
					if (found != null) break;
				}
					
				if (found != null) {
					BoundaryEdge be = new BoundaryEdge((FlowDirection)found.getUserData(),  (LineString)x, found);
					edges.add(be);
				}else {
					BoundaryEdge be = new BoundaryEdge(FlowDirection.UNKNOWN,  (LineString)x, null);
					edges.add(be);
					logger.warn("WARNING: The boundary edge has no in/out points: " + be.getLineString().toText());
				}
			}else {
				throw new RuntimeException("The intersection of aoi and waterbodies linestring merger does not return linestring geometry");

			}
		}
		return edges;
	}
	
	/**
	 * Create a new layer for skeleton construction points
	 * @param points
	 * @throws IOException
	 */
	public void addPointLayer(Set<SkeletonPoint> points) throws IOException {
		
		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
		builder.add("the_geom", Point.class, getCatchments().getBounds().getCoordinateReferenceSystem());
		builder.add(POINTID_ATTRIBUTE, Integer.class);
		builder.add(POLYID_ATTRIBUTE, Integer.class);
		builder.add("node_type", Integer.class);
		builder.add("io_type", Integer.class);
		builder.setName("ConstructionPoints");
		SimpleFeatureType pntType = builder.buildFeatureType();
		
		FeatureEntry entry = new FeatureEntry();
		entry.setTableName(CONSTRUCTION_PNTS_LAYER);
		entry.setM(false);
		List<SimpleFeature> features = new ArrayList<>();
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(pntType);
		GeometryFactory gf = new GeometryFactory();
		int pointid = 1;
		for (SkeletonPoint pnt : points) {
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
	public synchronized List<SkeletonPoint> getSkeletonPoints(int polyId) throws IOException{
		Filter filter = ff.equals(ff.property(POLYID_ATTRIBUTE), ff.literal(polyId));
		List<SkeletonPoint> points = new ArrayList<>();
		try(SimpleFeatureReader reader = geopkg.reader(geopkg.feature(CONSTRUCTION_PNTS_LAYER), filter, null)){
        	while(reader.hasNext()) {
        		SimpleFeature sf = reader.next();
        		
        		Coordinate c = ((Point)sf.getDefaultGeometry()).getCoordinate();
        		SkeletonPoint.Type type = SkeletonPoint.Type.getType((Integer)sf.getAttribute(NODETYPE_ATTRIBUTE));
        		FlowDirection fd = FlowDirection.getDirection((Integer)sf.getAttribute(Attribute.IOTYPE.getFieldName()));
        		SkeletonPoint p = new SkeletonPoint(c, type, fd, null);
        		points.add(p);
        	}
        }
        return points;
	}
	
	
	public Filter getWbTypeFilter() {
		return ff.equals(ff.property(Attribute.ECTYPE.getFieldName()), ff.literal(EcType.WATER.getType()));
	}
	
	public SimpleFeatureReader getWaterbodies(ReferencedEnvelope bounds) throws IOException{
		return query(bounds, getCatchments(), getWbTypeFilter());
	}
	
	public SimpleFeatureReader getFlowpaths(ReferencedEnvelope bounds) throws IOException{
		return query(bounds, getFlowpaths(), null);
	}
	
	private SimpleFeatureReader query(ReferencedEnvelope bounds, FeatureEntry source, Filter filter) throws IOException {
		if (source == null) throw new IOException("Required dataset not found.");
		if (bounds == null) {
			return geopkg.reader(source, null, null);
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
			rr.getFeatureType().getGeometryDescriptor().getUserData().put(Hints.COORDINATE_DIMENSION, 3);
			if (rr.hasNext()) next = rr.next();
		}
		if (next == null) throw new IllegalStateException("Waterbody with feature id " + fid + " not found.");
		setProcessed(next.getIdentifier(), polyId);
		return next;
	}

	/**
	 * Updates the waterbody layers, setting the given feature id
	 * processed flag to true. If the polyId provided is not null then
	 * the polygon id is also updated
	 * 
	 * @param fid feature id to update
	 * @param polyId the polygon id; if null not updated
	 * @throws IOException
	 */
	private void setProcessed(FeatureId fid, Integer polyId) throws IOException{
		wbToProcess.remove(fid.toString());
		
		if (polyId == null) return;
		
		//update polyid
		Filter filter = ff.id(fid);
		try(SimpleFeatureWriter writer = geopkg.writer(getCatchments(), false, filter, null)){
			writer.hasNext();
			SimpleFeature sf = writer.next();
			sf.setAttribute(POLYID_ATTRIBUTE, polyId);
			writer.write();
		}		
	}
	
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
	 * Pass an empty collection to write any remaining features in the write cache
	 * 
	 * @param skeletons
	 * @throws IOException
	 */
	public synchronized void writeSkeletons(Collection<LineString> skeletons) throws IOException {
		if (skeletonWriteCache == null) skeletonWriteCache = new ArrayList<>();
		skeletonWriteCache.addAll(skeletons);
		
		if (skeletonWriteCache.size() > 1000 || skeletons.isEmpty()) {
			try(Transaction tx = new DefaultTransaction()) {	
				try(SimpleFeatureWriter fw = geopkg.writer(getFlowpaths(), true, Filter.EXCLUDE, tx)){
					for (LineString item : skeletonWriteCache) {
						SimpleFeature fs = fw.next();
						fs.setAttribute(Attribute.EFTYPE.getFieldName(), ((EfType)item.getUserData()).getType() );
						fs.setDefaultGeometry(item);
						fw.write();
					}
					tx.commit();
				} catch(IOException ioe) {
					tx.rollback();
					throw ioe;
				}
			}
			skeletonWriteCache.clear();
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
	 * Add a processed column to the waterbodies table
	 * 
	 * @throws SQLException
	 */
	public void addProcessedAttribute() throws Exception{
		wbToProcess = new ArrayList<>();
		try(SimpleFeatureReader reader = getWaterbodies()){
			while(reader.hasNext()) {
				wbToProcess.add(reader.next().getID());
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
