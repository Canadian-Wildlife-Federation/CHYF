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
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureStore;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.util.NullProgressListener;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.JDBCDataStoreFactory;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.expression.PropertyName;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfLogger;
import net.refractions.chyf.util.ReprojectionUtils;

/**
 * 
 * NOT USED: this was slow in cases when there wasn't a fast db connection;
 * 
 * Reads a geopackage input dataset.  
 * 
 * @author Emily
 *
 */
public class ChyfPostGisDataSource implements ChyfDataSource{

	static final Logger logger = LoggerFactory.getLogger(ChyfPostGisDataSource.class.getCanonicalName());
	
	protected CoordinateReferenceSystem crs;
	protected int srid;
	
	protected DataStore workingDataStore;
	protected DataStore rawDataStore;

	protected String aoiId;
	protected UUID aoiUuid;
	
	protected String rawSchema = "raw";
	protected String workingSchema = "working";
	
	protected Map<String, Object> connectionParameters;
	
	protected ChyfPostGisDataSource() throws IOException {
		
	}
	
	public ChyfPostGisDataSource(String connectionString, String inschema, 
			String outschema) throws IOException {
		ChyfLogger.INSTANCE.setDataSource(this);
		
		this.rawSchema = inschema;
		this.workingSchema = outschema;
		
		int port = 5432;
		String host = null;
		String dbname = null;
		String user = null;
		String password = null;
		
		String[] bits = connectionString.split(";");
		for (String bit : bits) {
			if (bit.toLowerCase().contains("host=")) {
				host = bit.substring("host=".length());
			}else if (bit.toLowerCase().contains("db=")) {
				dbname = bit.substring("db=".length());
			}else if (bit.toLowerCase().contains("user=")) {
				user = bit.substring("user=".length());
			}else if (bit.toLowerCase().contains("password=")) {
				password = bit.substring("password=".length());
			}else if (bit.toLowerCase().contains("port=")) {
				String sport = bit.substring("port=".length());
				port = Integer.valueOf(sport);
			}
		}
		connectionParameters = new HashMap<>();
		connectionParameters.put("dbtype", "postgis");
		connectionParameters.put("host", host);
		connectionParameters.put("port", port);
		connectionParameters.put("schema", rawSchema);
		connectionParameters.put("database", dbname);
		connectionParameters.put("user", user);
		connectionParameters.put("passwd", password);

		connectionParameters.put(JDBCDataStoreFactory.EXPOSE_PK.key, Boolean.TRUE);
		
		rawDataStore = DataStoreFinder.getDataStore(connectionParameters);
		try(Transaction tx = new DefaultTransaction()){
			try {
				Connection c = ((JDBCDataStore)rawDataStore).getConnection(tx);
				c.createStatement().execute("CREATE SCHEMA IF NOT EXISTS " + workingSchema);
				tx.commit();
			}catch (SQLException ex) {
				tx.rollback();
				throw new IOException(ex);
			}
		}
		
	    connectionParameters.put("schema", workingSchema);
	    workingDataStore = DataStoreFinder.getDataStore(connectionParameters);
	    
		createWorkingTables();
	}
	
	/**
	 * @return the coordinate reference system associated with the geopackage layers
	 */
	public CoordinateReferenceSystem getCoordinateReferenceSystem() {
		return crs;
	}
	
	/**
	 * Finds the next aoi in the raw.aoi table with
	 * a state of ready.  Updates the state to processing.
	 * @param current the current state to search for
	 * @param processing the state to update to to flag aoi as processing
	 * @return null if no more items to process
	 */
	public String getNextAoiToProcess(ProcessingState current, ProcessingState processing) throws IOException{
		String laoiId = null;
		try(Transaction tx = new DefaultTransaction()){
			try {
				Connection c = ((JDBCDataStore)workingDataStore).getConnection(tx);
					
				String aoitable = rawSchema + "." + getTypeName(Layer.AOI);
				try(Statement stmt = c.createStatement()){
					stmt.executeUpdate("LOCK TABLE " + aoitable);
					
					//find the next aoi to process
					StringBuilder sb = new StringBuilder();
					sb.append("SELECT id, name FROM ");
					sb.append(aoitable);
					sb.append(" WHERE status = '");
					sb.append(current.name());
					sb.append("'");
						
					UUID aoiId = null;
					try(ResultSet rs = stmt.executeQuery(sb.toString())){
						if (rs.next()) {
							aoiId = (UUID) rs.getObject(1);
							laoiId  = rs.getString(2);
						}
					}
					
					if (aoiId != null) {
						sb = new StringBuilder();
						sb.append("UPDATE ");
						sb.append(aoitable);
						sb.append(" SET status = ? ");
						sb.append(" WHERE id = ?");
						
						try(PreparedStatement ps = c.prepareStatement(sb.toString())){
							ps.setString(1, processing.name());
							ps.setObject(2, aoiId);
							ps.executeUpdate();
						}
						
						sb = new StringBuilder();
						sb.append("UPDATE ");
						sb.append(workingSchema + "." + getTypeName(Layer.AOI));
						sb.append(" SET status = ? ");
						sb.append(" WHERE id = ?");
						
						try(PreparedStatement ps = c.prepareStatement(sb.toString())){
							ps.setString(1, processing.name());
							ps.setObject(2, aoiId);
							ps.executeUpdate();
						}
					}
				}
				tx.commit();
			}catch (SQLException ex) {
				tx.rollback();
				throw new IOException(ex);
			}
		}
		
		return laoiId;
	}
	/**
	 * Updates the state of the aoi processing (both the working 
	 * and finish schema).
	 * 
	 * @param state
	 * @throws IOException 
	 */
	public void setState(ProcessingState state) throws IOException {
		try(Transaction tx = new DefaultTransaction()){
			try {
				Connection c = getConnection(tx);
					
				for (String schema : new String[] {workingSchema, rawSchema}) {
					
					String aoitable = schema + "." + getTypeName(Layer.AOI);
					try(Statement stmt = c.createStatement()){
						stmt.executeUpdate("LOCK TABLE " + aoitable);
						
						StringBuilder sb = new StringBuilder();
						sb.append("UPDATE ");
						sb.append(schema + "." + getTypeName(Layer.AOI));
						sb.append(" SET status = ? ");
						sb.append(" WHERE ");
						sb.append(getAoiFieldName(Layer.AOI));
						sb.append(" = ? ");
						
						try(PreparedStatement ps = c.prepareStatement(sb.toString())){
							ps.setString(1, state.name());
							ps.setObject(2, aoiUuid);
						
							ps.executeUpdate();
						}catch (Exception ex) {
							throw new IOException(ex);
						}
					}
				}
				tx.commit();
			}catch (SQLException ex) {
				tx.rollback();
				throw new IOException(ex);
			}
		}
	}
	
	protected String getTableName(ILayer layer) {
		return workingSchema + "." + getTypeName(layer);
	}
	
	protected Connection getConnection() throws IOException {
		return getConnection(Transaction.AUTO_COMMIT);
	}
	
	protected Connection getConnection(Transaction tx) throws IOException {
		return ((JDBCDataStore)workingDataStore).getConnection(tx);
	}
	
	protected void resetWorkingDataStore() throws IOException {
		workingDataStore.dispose();
		connectionParameters.put("schema", workingSchema);
		workingDataStore = DataStoreFinder.getDataStore(connectionParameters);
	}
	
	public void setAoi(String aoiId) throws IOException {
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
	}
	
	

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

	@Override
	public FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer) throws IOException {
		return query(layer, null, null);
	}

	@Override
	public FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer, ReferencedEnvelope bounds) throws IOException {
		return query(layer, bounds, null);
	}

	@Override
	public FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer, Filter filter) throws IOException {
		return query(layer, null, filter);
	}

	@Override
	public FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer, ReferencedEnvelope bounds, Filter filter) throws IOException {
		if (layer == null) throw new IOException("Required dataset not found.");

		String typeName = getTypeName(layer);
		SimpleFeatureType schema = workingDataStore.getSchema(typeName);
		
        List<Filter> filters = new ArrayList<>();
        filters.add(getAoiFilter(layer));
        
        if (bounds != null) {
        	String geometryPropertyName = schema.getGeometryDescriptor().getLocalName();
        	filters.add(filterFromEnvelope(bounds, ff.property(geometryPropertyName)));
        }
        
        if (filter != null) {
        	filters.add(filter);
        }
        
        Query query = new Query( typeName, ff.and(filters));
        
		return workingDataStore.getFeatureReader(query, Transaction.AUTO_COMMIT );
	}
	

	
	protected Filter filterFromEnvelope(ReferencedEnvelope env, PropertyName geomProp) {
		BoundingBox bounds = ReprojectionUtils.reproject(env, getCoordinateReferenceSystem());
		Filter filter1 = ff.bbox(geomProp, bounds);
		Filter filter2 = ff.intersects(geomProp, ff.literal(JTS.toGeometry(bounds)));
		Filter filter = ff.and(filter1, filter2);
		return filter;
	}
	
	protected Filter getAoiFilter(ILayer layer) {
		return ff.equals(ff.property(getAoiFieldName(layer)), ff.literal(aoiUuid));
	}
	
	protected String getAoiFieldName(ILayer layer) {
		if (layer != null && layer == Layer.AOI) return "id";
		return "aoi_id";
	}
	
	protected String getTypeName(ILayer layer) {
		if (layer == Layer.AOI) return "aoi";
		if (layer == Layer.ECATCHMENTS) return "ecatchment";
		if (layer == Layer.EFLOWPATHS) return "eflowpath";
		if (layer == Layer.SHORELINES) return "shoreline";
		if (layer == Layer.TERMINALNODES) return "terminal_node";
		if (layer == Layer.ERRORS) return "errors";
		if (layer == Layer.FEATURENAMES) return "feature_names";
		return null;
	}

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
	public SimpleFeatureType getFeatureType(ILayer layer) throws IOException {
		return workingDataStore.getSchema(getTypeName(layer));
	}

	
	/**
	 * Creates the database working tables if
	 * they don't already exist.  Does not add/remove
	 * any data from these tables.
	 * 
	 * @throws IOException
	 */
	protected void createWorkingTables() throws IOException {

		//ensure raw tables exist
	    for (Layer l : Layer.values()) {
	    	if (l == Layer.ERRORS) continue;
	    	SimpleFeatureType stype = rawDataStore.getSchema(getTypeName(l));
	    	if (stype == null) throw new IOException("No raw data table for layer :" + l.getLayerName());
	    }
	    
	    Connection c = getConnection();
	    
	    //read the srid from the eflowpath table
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("SELECT srid ");
			sb.append("FROM geometry_columns ");
			sb.append(" WHERE f_table_schema = ? AND f_table_name = ?");

			try(PreparedStatement ps = c.prepareStatement(sb.toString())){
				ps.setString(1, rawSchema);
				ps.setString(2, getTypeName(Layer.EFLOWPATHS));
				try(ResultSet rs = ps.executeQuery()){
					if (rs.next()) {
						srid = rs.getInt(1);
						this.crs = CRS.decode("EPSG:" + srid);
					}
				}
			}
		} catch (Exception e) {
			throw new IOException (e);
		}
		if (this.crs == null) {
			throw new IOException("Could not determine srid of eflowpath table - ensure a valid srid exists in the geometry column tables for the eflowpath table");
		}
	    
	    
	    //check if working tables exist;
	    List<Layer> toProcess = new ArrayList<>();
	    toProcess.add(Layer.AOI);
	    for (Layer l : Layer.values()) {
	    	if (l == Layer.AOI ) continue;
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
	    		if (l == Layer.ERRORS) {
	    			createErrorWarningsTable(c);
	    		}else {
		    		SimpleFeatureType rtype = rawDataStore.getSchema(getTypeName(l));
		    		Name interalidatt = ChyfDataSource.findAttribute(rtype, ChyfAttribute.INTERNAL_ID);
		    		
			    	StringBuilder sb = new StringBuilder();
			    	//create new table
			    	sb.append("CREATE TABLE ");
			    	sb.append(getTableName(l));
			    	sb.append(" AS SELECT ");
			    	if (l != Layer.AOI && interalidatt == null) {
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
				    	
				    		//TODO: this assumes geometry field
				    		sb = new StringBuilder();
				    		sb.append("CREATE INDEX ON ");
				    		sb.append(getTableName(l));
				    		sb.append(" using gist (geometry) ");
				    		
				    		c.createStatement().executeUpdate(sb.toString());
				    	}	
			    	}catch(SQLException ex) {
			    		throw new IOException(ex);
			    	}	   
			    	
	    		}
	    	}
	    }
	    createNameIdTable();
	}
	
	
	protected void createErrorWarningsTable(Connection c) throws IOException{
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("CREATE TABLE ");
	    	sb.append(getTableName(Layer.ERRORS));
	    	sb.append("(");
	    	sb.append("id uuid not null default uuid_generate_v4() primary key, ");
	    	sb.append(getAoiFieldName(Layer.ERRORS) + " uuid not null references " + getTableName(Layer.AOI) + " (" + getAoiFieldName(Layer.AOI) + "),"); 
	    	sb.append("type varchar(32), ");
	    	sb.append("message varchar, ");
	    	sb.append("process varchar, ");
	    	sb.append("geometry GEOMETRY (geometry, " + srid + ")");
	    	
			c.createStatement().executeUpdate(sb.toString());
		}catch (SQLException ex) {
			throw new IOException(ex);
		}
	}

	public void logError(String message, Geometry location, String process) throws IOException {
		logInternal("ERROR", location, message, process);
	}
	public void logWarning(String message, Geometry location, String process) throws IOException {
		logInternal("WARNING", location, message, process);
	}
	private void logInternal(String type, Geometry location, String message, String process) throws IOException{
		
		SimpleFeatureType pntType = getFeatureType(Layer.ERRORS);

		List<SimpleFeature> features = new ArrayList<>();
		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(pntType);
		
		String uuid = UUID.randomUUID().toString();
		featureBuilder.set("geometry", location);
		featureBuilder.set(getAoiFieldName(Layer.ERRORS), aoiUuid);
		featureBuilder.set("type", type);
		featureBuilder.set("message", message);
		featureBuilder.set("process", process);
		SimpleFeature sf = featureBuilder.buildFeature(uuid);
		features.add(sf);
		
		try(Transaction t1 = new DefaultTransaction("transaction 1")){
			@SuppressWarnings("unchecked")
			FeatureStore<SimpleFeatureType, SimpleFeature> store = 
					(FeatureStore<SimpleFeatureType, SimpleFeature>) workingDataStore.getFeatureSource(getTypeName(Layer.ERRORS));
			store.setTransaction(t1);
			store.addFeatures(DataUtilities.collection(features));
			t1.commit();
		}
	}
	
	
	protected void createNameIdTable() throws IOException {
		try{
			StringBuilder sb = new StringBuilder();
			sb.append("CREATE TABLE IF NOT EXISTS ");
			sb.append(workingSchema + "." + getTypeName(Layer.FEATURENAMES));
			sb.append("(");
	    	sb.append(getAoiFieldName(Layer.FEATURENAMES) + " uuid not null references " + workingSchema + "." + getTableName(Layer.AOI) + " (" + getAoiFieldName(Layer.AOI) + "), "); 
			sb.append(" fid varchar, name_id varchar, geodbname varchar, name varchar, primary key (fid)) ");
			getConnection().createStatement().execute(sb.toString());
			
    		sb = new StringBuilder();
    		sb.append("ALTER TABLE ");
			sb.append(workingSchema + "." + getTypeName(Layer.FEATURENAMES));
    		sb.append(" ADD CONSTRAINT feature_names_aoi_id_unq UNIQUE (aoi_id, name_id) ");
    		getConnection().createStatement().execute(sb.toString());
    		

		}catch (SQLException ex) {
			throw new IOException(ex);
		}
	}

}
