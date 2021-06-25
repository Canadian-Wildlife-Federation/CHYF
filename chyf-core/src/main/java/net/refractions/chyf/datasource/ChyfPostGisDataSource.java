/*******************************************************************************
* Copyright 2020 Government of Canada
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
 *******************************************************************************/
package net.refractions.chyf.datasource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureReader;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.util.NullProgressListener;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.JDBCDataStoreFactory;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a geopackage input dataset.  
 * 
 * @author Emily
 *
 */
public class ChyfPostGisDataSource implements ChyfDataSource{

	static final Logger logger = LoggerFactory.getLogger(ChyfPostGisDataSource.class.getCanonicalName());
	
//	protected Path geopackageFile;
//	protected GeoPackage geopkg;
	protected CoordinateReferenceSystem crs;
	
	
	protected DataStore workingDataStore;

	protected String aoiId;
	protected UUID aoiUuid;
	
	protected String rawSchema = "raw";
	protected String workingSchema = "working";
	
	private Map<String, Object> connectionParameters;
	
	public ChyfPostGisDataSource(String aoi) throws IOException {
		this.aoiId = aoi;
		
		try {
			this.crs = CRS.decode("EPSG:4326");
		} catch (Exception e) {
			throw new IOException (e);
		}
		read();
		prepareOutput();
	}
	
	/**
	 * @return the coordinate reference system associated with the geopackage layers
	 */
	public CoordinateReferenceSystem getCoordinateReferenceSystem() {
		return crs;
	}
	
	protected String getTableName(Layer layer) {
		return workingSchema + "." + getTypeName(layer);
	}
	
	protected Connection getConnection() throws IOException {
		return ((JDBCDataStore)workingDataStore).getConnection(Transaction.AUTO_COMMIT);
	}
	
	protected void resetWorkingDataStore() throws IOException {
		workingDataStore.dispose();
		connectionParameters.put("schema", workingSchema);
		workingDataStore = DataStoreFinder.getDataStore(connectionParameters);
	}
	
	
	protected void read() throws IOException {
//		geopkg = new GeoPackage(geopackageFile.toFile());
//		geopkg.init();
//		
//		for (Layer l : Layer.values()) {
//			SimpleFeatureType ft = getFeatureType(l);
//			if (ft == null) continue;
//			if (crs == null) {
//				crs = ft.getCoordinateReferenceSystem();
//			}else if (!CRS.equalsIgnoreMetadata(crs,  ft.getCoordinateReferenceSystem())) {
//				throw new RuntimeException("All layers must have the same projection.");
//			}
//		}
		connectionParameters = new HashMap<>();
		connectionParameters.put("dbtype", "postgis");
		connectionParameters.put("host", "localhost");
		connectionParameters.put("port", 8447);
		connectionParameters.put("schema", rawSchema);
		connectionParameters.put("database", "chyf");
		connectionParameters.put("user", "smart");
		connectionParameters.put("passwd", "smart");

		connectionParameters.put(JDBCDataStoreFactory.EXPOSE_PK.key, Boolean.TRUE);
	    DataStore rawDataStore = DataStoreFinder.getDataStore(connectionParameters);
	    
	    
	    //ensure raw tables exist
	    for (Layer l : Layer.values()) {
	    	
	    	SimpleFeatureType stype = rawDataStore.getSchema(getTypeName(l));
	    	if (stype == null) throw new IOException("No raw data table for layer :" + l.getLayerName());
	    }
	    
	    //query aoi table for aoi id
	    Connection c = ((JDBCDataStore)rawDataStore).getConnection(Transaction.AUTO_COMMIT);
	    StringBuilder sb = new StringBuilder();
	    sb.append("SELECT id FROM ");
	    sb.append(rawSchema + "." + getTypeName(Layer.AOI));
	    sb.append(" WHERE name = ? ");
	    
	    try(PreparedStatement ps = c.prepareStatement(sb.toString())){
	    	ps.setString(1, aoiId);
	    	try(ResultSet rs = ps.executeQuery()){
	    		if (rs.next()) {
	    			aoiUuid = (UUID) rs.getObject(1);
	    		}
	    	}
	    }catch (SQLException ex) {
	    	throw new IOException(ex);
	    }
	    if (aoiUuid == null) {
	    	throw new IOException("Not aoi found with id " + aoiId);
	    }
	    rawDataStore.dispose();
	    
	    
	    connectionParameters.put("schema", workingSchema);
	    workingDataStore = DataStoreFinder.getDataStore(connectionParameters);
	    c = ((JDBCDataStore)workingDataStore).getConnection(Transaction.AUTO_COMMIT);
	    
	    //check if working tables exist;
	    List<Layer> toProcess = new ArrayList<>();
	    toProcess.add(Layer.AOI);
	    for (Layer l : Layer.values()) {
	    	if (l == Layer.AOI) continue;
	    	toProcess.add(l);
	    }
	    
	    for (Layer l : toProcess) {
	    	SimpleFeatureType stype = null;
	    
	    	try {
	    		stype = workingDataStore.getSchema(getTypeName(l));
	    	}catch (IOException ex) {
	    		//not found
	    	}
	    	if (stype == null) {
		    	sb = new StringBuilder();
		    	//create new table
		    	sb.append("CREATE TABLE ");
		    	sb.append(getTableName(l));
		    	sb.append(" AS SELECT ");
		    	if (l != Layer.AOI) {
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
		    		
		    	try {
			    	if (l == Layer.AOI) {
			    		sb = new StringBuilder();
			    		sb.append("ALTER TABLE ");
			    		sb.append(getTableName(l));
			    		sb.append(" ADD PRIMARY KEY (id) ");
			    		c.createStatement().executeUpdate(sb.toString());
			    	}else {
			    		sb = new StringBuilder();
			    		sb.append("ALTER TABLE ");
			    		sb.append(getTableName(l));
			    		sb.append(" ADD PRIMARY KEY ( ");
			    		sb.append(ChyfAttribute.INTERNAL_ID.getFieldName());
			    		sb.append(")");
			    		c.createStatement().executeUpdate(sb.toString());
			    		
			    		sb = new StringBuilder();
			    		sb.append("CREATE INDEX ON ");
			    		sb.append(getTableName(l));
			    		sb.append(" ( ");
			    		sb.append(getAoiFieldName(l));
			    		sb.append(")");
			    		c.createStatement().executeUpdate(sb.toString());
			    	}
			    	
			    		
		    	}catch(SQLException ex) {
		    		throw new IOException(ex);
		    	}	   
	    	}
	    }
	}
	
	protected void prepareOutput() throws IOException {
	    connectionParameters.put("schema", workingSchema);
	    workingDataStore = DataStoreFinder.getDataStore(connectionParameters);
	    Connection c = ((JDBCDataStore)workingDataStore).getConnection(Transaction.AUTO_COMMIT);
	    
	    //check if working tables exist;
	    List<Layer> toProcess = new ArrayList<>();
	    toProcess.add(Layer.AOI);
	    for (Layer l : Layer.values()) {
	    	if (l == Layer.AOI) continue;
	    	toProcess.add(l);
	    }
	    
	    for (Layer l : toProcess) {

	    	StringBuilder sb = new StringBuilder();
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
	    		
    		sb = new StringBuilder();
    		sb.append("INSERT INTO ");
    		sb.append(workingSchema + "." + getTypeName(l));
    		sb.append(" SELECT ");
    		if (l != Layer.AOI) {
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
//	
//	/**
//	 * 
//	 * @param layer
//	 * @return the feature type associated with a given layer
//	 * @throws IOException
//	 */
//	public synchronized SimpleFeatureType getFeatureType(Layer layer) throws IOException{
//		return getFeatureType(getEntry(layer));
//	}
//
//	
//    /**
//     * 
//     * @param layer
//     * @return the feature type associated with a given feature entry
//     * @throws IOException
//     */
//	protected synchronized SimpleFeatureType getFeatureType(FeatureEntry layer) throws IOException{
//		if (layer == null) return null;
//		dataStore.getFeatureSource(typeName)
//		try(SimpleFeatureReader reader = geopkg.reader(layer, Filter.EXCLUDE, null)){
//			return reader.getFeatureType();
//		}
//	}
//	
	/**
	 * @return a feature reader for the given layer
	 */
    protected FeatureReader<SimpleFeatureType, SimpleFeature> getFeatureReader(Layer layer, Filter filter, Transaction tx) throws IOException {

		String typeName = getTypeName(layer);
		
        List<Filter> filters = new ArrayList<>();
        filters.add(getAoiFilter(layer));
        if (filter != null) {
        	filters.add(filter);
        }
        
        Query query = new Query( typeName, ff.and(filters));
		return workingDataStore.getFeatureReader(query, tx );
    }
   
	
    /**
     * 
     * @return all catchments of water type
     * 
     * @throws IOException
     */
    public FeatureReader<SimpleFeatureType, SimpleFeature> getWaterbodies() throws IOException{
		return getFeatureReader(Layer.ECATCHMENTS, getECatchmentTypeFilter(EcType.WATER), null);
	}
//    
//    /**
//     * The feature entry associated with the given layer
//     * @param layer
//     * @return
//     * @throws IOException
//     */
//	protected synchronized FeatureEntry getEntry(Layer layer) throws IOException {
//		return geopkg.feature(layer.getLayerName());
//	}
//
//	public synchronized int getSrid(Layer layer) throws IOException {
//		return geopkg.feature(layer.getLayerName()).getSrid();
//	}
//
//	/**
//	 * Ensures that the eflowpath, ecatchments, and ecoastline layers
//	 * all have a internal_uuid attribute and that is is unique and populated
//	 * for each feature.  If it doesn't exist a new field is added.  Any empty values
//	 * are populated.  If not unique then an exception is thrown
//	 * @throws SQLException 
//	 * @throws IOException 
//	 * @throws Exception 
//	 */
//	public synchronized void addInternalIdAttribute() throws IOException {
//		
//		//layers which need internal_ids
//		Layer[] layers = new Layer[] {Layer.SHORELINES, Layer.EFLOWPATHS, Layer.ECATCHMENTS};
//		
//		List<FeatureEntry> featureEntries = new ArrayList<>();
//		for (Layer l : layers) {
//			FeatureEntry fe = getEntry(l);
//			if (fe != null) featureEntries.add(fe);
//		}
//		
//		//determine which layers need internal ids added
//		List<FeatureEntry> addId = new ArrayList<>();
//		for (FeatureEntry fe : featureEntries) {
//			Name name = ChyfDataSource.findAttribute(getFeatureType(fe), ChyfAttribute.INTERNAL_ID);
//			if (name == null) addId.add(fe);
//		}
//		
//		try {
//			//add the id
//			Connection c = geopkg.getDataSource().getConnection();
//			for (FeatureEntry layer : addId) {
//				String query = "ALTER TABLE " + layer.getTableName() + " add column " + ChyfAttribute.INTERNAL_ID.getFieldName()  + " text ";
//				c.createStatement().execute(query);
//			}
//			
//			//ensure unique
//			for (FeatureEntry layer : featureEntries) {
//				
//				StringBuilder sb = new StringBuilder();
//				sb.append("SELECT count(*), ");
//				sb.append(ChyfAttribute.INTERNAL_ID.getFieldName());
//				sb.append(" FROM ");
//				sb.append(layer.getTableName());
//				sb.append(" WHERE ");
//				sb.append(ChyfAttribute.INTERNAL_ID.getFieldName());
//				sb.append(" is not null ");
//				sb.append(" GROUP BY ");
//				sb.append(ChyfAttribute.INTERNAL_ID.getFieldName());
//				sb.append(" HAVING count(*) > 1");
//				
//				try(ResultSet rs = c.createStatement().executeQuery(sb.toString())){
//					if (rs.next()) throw new RuntimeException("Provided internal ids are not unique.");
//				}
//			}
//		} catch (SQLException sqle) {
//			throw new IOException(sqle);
//		}
//		
//		geopkg.close();
//		read();
//		
//		//populate internal_id with uuids
//		for (FeatureEntry layer : featureEntries) {
//			try(DefaultTransaction tx = new DefaultTransaction()) {
//				try {
//					Filter blankFilter = ff.isNull(ff.property(ChyfAttribute.INTERNAL_ID.getFieldName()));
//		
//					try(SimpleFeatureWriter writer = geopkg.writer(layer, false, blankFilter, tx)){
//						Name name = ChyfDataSource.findAttribute(writer.getFeatureType(), ChyfAttribute.INTERNAL_ID);
//						
//						while(writer.hasNext()) {
//							SimpleFeature sf = writer.next();
//							sf.setAttribute(name, UUID.randomUUID().toString());
//							writer.write();
//						}
//					}
//					tx.commit();
//				} catch (IOException ioe) {
//					tx.rollback();
//					throw ioe;
//				}
//			}
//		}
//		
//	}

	@Override
	public FeatureReader<SimpleFeatureType, SimpleFeature> query(Layer layer) throws IOException {
		return query(layer, null, null);
	}

	@Override
	public FeatureReader<SimpleFeatureType, SimpleFeature> query(Layer layer, ReferencedEnvelope bounds) throws IOException {
		return query(layer, bounds, null);
	}

	@Override
	public FeatureReader<SimpleFeatureType, SimpleFeature> query(Layer layer, Filter filter) throws IOException {
		return query(layer, null, filter);
	}

	@Override
	public FeatureReader<SimpleFeatureType, SimpleFeature> query(Layer layer, ReferencedEnvelope bounds, Filter filter) throws IOException {
		if (layer == null) throw new IOException("Required dataset not found.");

		String typeName = getTypeName(layer);
		SimpleFeatureType schema = workingDataStore.getSchema(typeName);
		
		// usually "THE_GEOM" for shapefiles
        
        List<Filter> filters = new ArrayList<>();
        filters.add(getAoiFilter(layer));
        
        if (bounds != null) {
        	String geometryPropertyName = schema.getGeometryDescriptor().getLocalName();
        	Filter bboxfilter = ff.bbox(ff.property(geometryPropertyName), bounds);
        	filters.add(bboxfilter);
        }
        
        if (filter != null) {
        	filters.add(filter);
        }
        
        Query query = new Query( typeName, ff.and(filters));
        
		return workingDataStore.getFeatureReader(query, Transaction.AUTO_COMMIT );
	}
	
	protected Filter getAoiFilter(Layer layer) {
		return ff.equals(ff.property(getAoiFieldName(layer)), ff.literal(aoiUuid));
	}
	
	protected String getAoiFieldName(Layer layer) {
		if (layer == Layer.AOI) return "id";
		return "aoi_id";
	}
	
	protected String getTypeName(Layer layer) {
		switch(layer) {
		case AOI: return "aoi";
		case ECATCHMENTS: return "ecatchment";
		case EFLOWPATHS: return "eflowpath";
		case SHORELINES: return "shoreline";
		case TERMINALNODES: return "terminal_node";		
		}
		return null;
	}
	
//	// Internal query interface allows subclasses to specify the featureEntry instead of using the fixed Layer Enum
//	protected SimpleFeatureReader query(FeatureEntry fe, ReferencedEnvelope bounds, Filter filter) throws IOException {
//		if(fe == null) return null;
//		
//		Filter netFilter = null;
//		if(bounds != null) {
//			netFilter = filterFromEnvelope(bounds, fe);
//		}
//		if(filter != null) {
//			netFilter = ff.and(netFilter, filter);
//		}
//    	return geopkg.reader(fe, netFilter, null);
//	}
////	
//	protected Filter filterFromEnvelope(ReferencedEnvelope env, FeatureEntry source) {
//		BoundingBox bounds = ReprojectionUtils.reproject(env, ReprojectionUtils.srsCodeToCRS(source.getSrid()));
//		String geom = source.getGeometryColumn();
//		Filter filter1 = ff.bbox(ff.property(geom), bounds);
//		Filter filter2 = ff.intersects(ff.property(geom), ff.literal(JTS.toGeometry(bounds)));
//		Filter filter = ff.and(filter1, filter2);
//		return filter;
//	}

	/**
	 * Gets aoi layer as a list of polygons
	 * @return
	 * @throws IOException 
	 * @throws NoSuchElementException 
	 * @throws IllegalArgumentException 
	 * @throws Exception
	 */
	public synchronized List<Polygon> getAoi() throws IOException  {
		
		List<Polygon> aois = new ArrayList<>();
		
		SimpleFeatureSource fsource = workingDataStore.getFeatureSource(getTypeName(Layer.AOI));
		fsource.getFeatures().accepts(new FeatureVisitor() {
			@Override
			public void visit(Feature feature) {
				Polygon p = ChyfDataSource.getPolygon((SimpleFeature)feature);
				aois.add(p);
			}
		}, new NullProgressListener());
		
		return aois;
	}
	
	@Override
	public synchronized void close() {
		if (workingDataStore != null) workingDataStore.dispose();
	}

	@Override
	public SimpleFeatureType getFeatureType(Layer layer) throws IOException {
		return workingDataStore.getSchema(getTypeName(layer));
	}

}
