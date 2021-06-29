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

import javax.swing.plaf.basic.BasicTreeUI;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureReader;
import org.geotools.data.Query;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.util.NullProgressListener;
import org.geotools.geometry.jts.JTS;
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
import org.opengis.filter.expression.PropertyName;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.util.ReprojectionUtils;

/**
 * Reads a geopackage input dataset.  
 * 
 * @author Emily
 *
 */
public class ChyfPostGisDataSource implements ChyfDataSource{

	static final Logger logger = LoggerFactory.getLogger(ChyfPostGisDataSource.class.getCanonicalName());
	
	protected CoordinateReferenceSystem crs;
	
	protected DataStore workingDataStore;
	protected DataStore rawDataStore;

	protected String aoiId;
	protected UUID aoiUuid;
	
	protected String rawSchema = "raw";
	protected String workingSchema = "working";
	
	private Map<String, Object> connectionParameters;
		
	public enum State {
		FP_PROCESSING,
		READY,
		FP_DONE,
		ERROR
	};
	
	public ChyfPostGisDataSource(String connectionString, String inschema, 
			String outschema) throws IOException {
		this.rawSchema = inschema;
		this.workingSchema = outschema;
		
		try {
			this.crs = CRS.decode("EPSG:4326");
		} catch (Exception e) {
			throw new IOException (e);
		}
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
	 * @return null if no more items to process
	 */
	public String getNextAoiToProcess() throws IOException{
		String laoiId = null;
		Transaction tx = new DefaultTransaction();
		try {
			Connection c = ((JDBCDataStore)workingDataStore).getConnection(tx);
				
			String aoitable = rawSchema + "." + getTypeName(Layer.AOI);
			try(Statement stmt = c.createStatement()){
				stmt.executeUpdate("LOCK TABLE " + aoitable);
				
				//find the next aoi to process
				StringBuilder sb = new StringBuilder();
				sb.append("SELECT name FROM ");
				sb.append(aoitable);
				sb.append(" WHERE status = '");
				sb.append(State.READY.name());
				sb.append("'");
					
				try(ResultSet rs = stmt.executeQuery(sb.toString())){
					if (rs.next()) {
						laoiId  = rs.getString(1);
					}
				}
			}
			tx.commit();
		}catch (SQLException ex) {
			tx.rollback();
			throw new IOException(ex);
		}
		
		return laoiId;
	}
	/**
	 * Updates the state of the aoi processing
	 * @param state
	 * @throws IOException 
	 */
	public void setState(State state) throws IOException {
		Transaction tx = new DefaultTransaction();
		try {
			Connection c = getConnection(tx);
				
			String aoitable = workingSchema + "." + getTypeName(Layer.AOI);
			try(Statement stmt = c.createStatement()){
				stmt.executeUpdate("LOCK TABLE " + aoitable);
				
				StringBuilder sb = new StringBuilder();
				sb.append("UPDATE ");
				sb.append(rawSchema + "." + getTypeName(Layer.AOI));
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
			tx.commit();
		}catch (SQLException ex) {
			tx.rollback();
			throw new IOException(ex);
		}
	}
	
	protected String getTableName(Layer layer) {
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
	    
	    clearAoiOutputTables();
	}
	
	/**
	 * clears existing aoi data from working tables and 
	 * copies raw data into the working tables
	 * @throws IOException
	 */
	protected void clearAoiOutputTables() throws IOException {

		connectionParameters.put("schema", workingSchema);
	    workingDataStore = DataStoreFinder.getDataStore(connectionParameters);
	    Connection c = ((JDBCDataStore)workingDataStore).getConnection(Transaction.AUTO_COMMIT);
	    
	    
	    //check if working tables exist;
	    List<Layer> toProcess = new ArrayList<>();
	    for (Layer l : Layer.values()) {
	    	if (l == Layer.AOI) continue;
	    	toProcess.add(l);
	    }
	    toProcess.add(Layer.AOI);
	    
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
	
	protected Filter getAoiFilter(Layer layer) {
		return ff.equals(ff.property(getAoiFieldName(layer)), ff.literal(aoiUuid));
	}
	
	protected String getAoiFieldName(Layer layer) {
		if (layer != null && layer == Layer.AOI) return "id";
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
	    	SimpleFeatureType stype = rawDataStore.getSchema(getTypeName(l));
	    	if (stype == null) throw new IOException("No raw data table for layer :" + l.getLayerName());
	    }
	    
	    connectionParameters.put("schema", workingSchema);
	    workingDataStore = DataStoreFinder.getDataStore(connectionParameters);
	    Connection c = getConnection();
	    
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
		    	StringBuilder sb = new StringBuilder();
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
}
