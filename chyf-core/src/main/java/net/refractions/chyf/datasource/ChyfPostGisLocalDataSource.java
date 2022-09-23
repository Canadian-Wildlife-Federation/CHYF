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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureReader;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.data.store.EmptyFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.JDBCDataStoreFactory;
import org.geotools.referencing.CRS;
import org.geotools.util.factory.Hints;
import org.locationtech.jts.geom.Geometry;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.Property;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads from PostGIS using local cache.
 * 
 * Downloads data from a postgis database, copies the data into a local geopackage,
 * once complete writes the database back to postigs  
 * 
 * @author Emily
 *
 */
public abstract class ChyfPostGisLocalDataSource implements ChyfDataSource{

	static final Logger logger = LoggerFactory.getLogger(ChyfPostGisLocalDataSource.class.getCanonicalName());
	
	protected CoordinateReferenceSystem crs;
	protected int srid;
	
//	protected DataStore outputDataStore;
//	protected DataStore inputDataStore;

	protected String aoiId;
	protected UUID aoiUuid;
	
	protected String inputSchema = "raw";
	protected String outputSchema = "working";
	
	private Map<String, Object> connectionParameters;
		
	protected ChyfGeoPackageDataSource local;

	
	public ChyfPostGisLocalDataSource(String connectionString, String inschema, 
			String outschema) throws IOException {
		this.inputSchema = inschema;
		this.outputSchema = outschema;
		
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
		connectionParameters.put("schema", inputSchema);
		connectionParameters.put("database", dbname);
		connectionParameters.put("user", user);
		connectionParameters.put("passwd", password);

		connectionParameters.put(JDBCDataStoreFactory.EXPOSE_PK.key, Boolean.TRUE);
		
		DataStore inputDataStore = createInputDataStore();
		try {
			
			// read the srid from the eflowpath table
			try(Connection c = ((JDBCDataStore)inputDataStore).getConnection(Transaction.AUTO_COMMIT)) {
				StringBuilder sb = new StringBuilder();
				sb.append("SELECT srid ");
				sb.append("FROM geometry_columns ");
				sb.append(" WHERE f_table_schema = ? AND f_table_name = ?");
	
				try (PreparedStatement ps = c.prepareStatement(sb.toString())) {
					ps.setString(1, inputSchema);
					ps.setString(2, getTypeName(Layer.EFLOWPATHS));
					try (ResultSet rs = ps.executeQuery()) {
						if (rs.next()) {
							this.srid = rs.getInt(1);
							this.crs = CRS.decode("EPSG:" + srid);
						}
					}
				}
			} catch (Exception e) {
				throw new IOException(e);
			}
			if (this.crs == null) {
				throw new IOException(
						"Could not determine srid of eflowpath table - ensure a valid srid exists in the geometry column tables for the eflowpath table");
			}
		}finally {
			
			inputDataStore.dispose();
		}
	}
	
	protected abstract ChyfGeoPackageDataSource createLocalDataSourceInternal(Path file) throws Exception;
	
	/**
	 * creates the local chyf data source
	 * 
	 * @return
	 * @throws IOException 
	 */
	protected ChyfGeoPackageDataSource createLocalDataSource() throws Exception {
		
		DataStore inputDataStore = createInputDataStore();
		try {
		
			//initialize output schema
			initOutputSchema(inputDataStore);
			
			//create local filestore
			Path temp = Files.createTempFile("fp", ".geopkg");
			logger.info("Creating geopackage file:" + temp.toString());
			
			GeoPackage geopkg = new GeoPackage(temp.toFile());
			geopkg.init();
			geopkg.addCRS(getCoordinateReferenceSystem(), "EPSG", srid);
			cacheData(inputDataStore, geopkg);
			geopkg.close();
			
			//clean output data
			try(DefaultTransaction tx = new DefaultTransaction()){
				try {
					cleanOutputSchema(((JDBCDataStore)inputDataStore).getConnection(tx), tx, true);
					tx.commit();
				}catch (IOException ex) {
					tx.rollback();
					throw ex;
				}
			}
			
			return createLocalDataSourceInternal(temp);
		}finally {
			inputDataStore.dispose();
		}
	}

	/**
	 * cached the data into a local geopackage database
	 * 
	 * @param geopkg
	 * @throws IOException
	 */
	protected void cacheData(DataStore inputDataStore, GeoPackage geopkg) throws IOException {
		
		//download data from layers into local geopackage
		for (Layer l : Layer.values()) {
			
			if(l == Layer.ERRORS) continue;

			
			if (l == Layer.FEATURENAMES) {
				//may or may not exists
				boolean found = false;
				for (String s : inputDataStore.getTypeNames()) {
					if (s.equals(getTypeName(l))) {
						found = true;
						break;
					}
				}
				if (!found) continue;
			}
			FeatureEntry entry = new FeatureEntry();
			entry.setTableName(l.getLayerName());
			entry.setM(false);
			
			Filter filter = getAoiFilter(l);
			
			SimpleFeatureCollection input = inputDataStore.getFeatureSource(getTypeName(l)).getFeatures(filter);
			
			//convert uuid to string
			SimpleFeatureTypeBuilder ftBuilder = new SimpleFeatureTypeBuilder();
			ftBuilder.setName(l.getLayerName());
			for (AttributeDescriptor ad : input.getSchema().getAttributeDescriptors()) {
				if (ad.getType().getBinding().equals(UUID.class)) {
					ftBuilder.add(ad.getLocalName(), String.class);
				}else {
					ftBuilder.add(ad);
				}
			}
			
			if (l == Layer.FEATURENAMES) {
				//add a fake geometry column so we can process this like other layers
				ftBuilder.add("geometry", Geometry.class, srid);
			}
			
			SimpleFeatureType newtype = ftBuilder.buildFeatureType();
			List<SimpleFeature> features = new ArrayList<>();
			input.accepts(new FeatureVisitor() {
				@Override
				public void visit(Feature feature) {
					SimpleFeatureBuilder sb=  new SimpleFeatureBuilder(newtype);
					for (Property p : feature.getProperties()) {
						if (p.getValue() instanceof UUID) {
							sb.set(p.getName(), ((UUID)p.getValue()).toString());
						}else {
							sb.set(p.getName(), p.getValue());
						}
					}
					SimpleFeature sf = sb.buildFeature(feature.getIdentifier().getID());
					features.add(sf);
				}
				
			}, null);
			
			if(!features.isEmpty()) {
				geopkg.add(entry, DataUtilities.collection(features));
			}else {
				geopkg.add(entry,new EmptyFeatureCollection(newtype));
			}
			if (l != Layer.FEATURENAMES) geopkg.createSpatialIndex(entry);
		}
		
		
	}
	
	protected void cleanOutputSchema(Connection c, Transaction tx, boolean includeAoi) throws IOException{

	    //check if working tables exist;
	    List<Layer> toProcess = new ArrayList<>();
	    for (Layer l : Layer.values()) {
	    	if (l == Layer.ERRORS) continue;
	    	if (l == Layer.AOI) continue;
	    	toProcess.add(l);
	    }
	    if (includeAoi) {
	    	toProcess.add(Layer.AOI);
	    	toProcess.add(0, Layer.ERRORS);
	    }
	    
	    
	    for (Layer l : toProcess) {
	    	StringBuilder sb = new StringBuilder();
    		//delete everything from aoi
    		sb.append("DELETE FROM ");
    		sb.append( outputSchema + "." + getTypeName(l) );
    		sb.append(" WHERE ");
    		sb.append(getAoiFieldName(l));
    		sb.append(" = ? ");
	    		
    		try(PreparedStatement ps = c.prepareStatement(sb.toString())){
    			ps.setObject(1, aoiUuid);
    			ps.executeUpdate();
    		}catch(SQLException ex) {
    			throw new IOException(ex);
    		}    		   
	    }
	    
	    if (includeAoi) {
		    StringBuilder sb = new StringBuilder();
			sb.append("INSERT INTO ");
			sb.append(outputSchema + "." + getTypeName(Layer.AOI));
			sb.append(" SELECT * FROM ");
			sb.append(inputSchema + "." + getTypeName(Layer.AOI));
			sb.append(" WHERE ");
			sb.append(getAoiFieldName(Layer.AOI) + " = ? ");
			
			try(PreparedStatement ps = c.prepareStatement(sb.toString())){
				ps.setObject(1, aoiUuid);
				ps.executeUpdate();
			}catch(SQLException ex) {
				throw new IOException(ex);
			}
	    }
	}
	
	/**
	 * Gets the local data source
	 * @return
	 * @throws IOException
	 */
	protected synchronized ChyfGeoPackageDataSource getLocalDataSource() throws IOException{
		if (this.local == null) {
			try {
				this.local = createLocalDataSource();
			}catch(Exception ex) {
				throw new IOException(ex);
			}
		}
		return local;
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
		
		DataStore inputDataStore = createInputDataStore();
		try {
			try(Transaction tx = new DefaultTransaction()){
				try {
					Connection c = ((JDBCDataStore)inputDataStore).getConnection(tx);
						
					String aoitable = inputSchema + "." + Layer.AOI.getLayerName().toLowerCase();
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
						}
					}
					tx.commit();
				}catch (SQLException ex) {
					tx.rollback();
					throw new IOException(ex);
				}
			}
			
			return laoiId;
		} finally {
			inputDataStore.dispose();
		}
	}
	/**
	 * Updates the state of the aoi processing (both the working 
	 * and finish schema).
	 * 
	 * @param state
	 * @throws IOException 
	 */
	public void setState(ProcessingState state) throws IOException {
		
		DataStore inputDataStore = createInputDataStore();
		try {
			try(Transaction tx = new DefaultTransaction()){
				try {
					Connection c = ((JDBCDataStore)inputDataStore).getConnection(tx);
	
					for (String schema : new String[] {inputSchema, outputSchema}) {
						
						String aoitable = schema + "." + Layer.AOI.getLayerName().toLowerCase();
						try(Statement stmt = c.createStatement()){
							stmt.executeUpdate("LOCK TABLE " + aoitable);
							
							StringBuilder sb = new StringBuilder();
							sb.append("UPDATE ");
							sb.append(schema + "." + Layer.AOI.getLayerName().toLowerCase());
							sb.append(" SET status = ? ");
							sb.append(" WHERE id = ? ");
							
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
		}finally {
			inputDataStore.dispose();
		}
	}
	
	protected DataStore createInputDataStore() throws IOException {
		connectionParameters.put("schema", inputSchema);
		DataStore inputDataStore = DataStoreFinder.getDataStore(connectionParameters);
		return inputDataStore;
	}
	
	protected DataStore createOutputDataStore() throws IOException {
		connectionParameters.put("schema", outputSchema);
		DataStore inputDataStore = DataStoreFinder.getDataStore(connectionParameters);
		return inputDataStore;
	}
	
	public void setAoi(String aoiId) throws IOException {
		if (this.local != null) {
			this.local.close();
		}
		this.local = null;
		
		DataStore inputDataStore = createInputDataStore();
		try (Connection c = ((JDBCDataStore)inputDataStore).getConnection(Transaction.AUTO_COMMIT)){
			//query aoi table for aoi id
		    
		    StringBuilder sb = new StringBuilder();
		    sb.append("SELECT id FROM ");
		    sb.append(inputSchema + "." + Layer.AOI.getLayerName().toLowerCase());
		    sb.append(" WHERE upper(name) = ? ");
		    
		    try(PreparedStatement ps = c.prepareStatement(sb.toString())){
		    	ps.setString(1, aoiId.toUpperCase());
		    	try(ResultSet rs = ps.executeQuery()){
		    		if (rs.next()) {
		    			aoiUuid = (UUID) rs.getObject(1);
		    		}
		    	}
		    }catch (SQLException ex) {
		    	throw new IOException(ex);
		    }
		    if (aoiUuid == null) {
		    	throw new IOException("No aoi found with id " + aoiId);
		    }
		 }catch (SQLException ex) {
		   	throw new IOException(ex);
		 }finally {
			inputDataStore.dispose();
		 }
	}
	
	

	/**
	 * @return a feature reader for the given layer
	 */
    protected FeatureReader<SimpleFeatureType, SimpleFeature> getFeatureReader(Layer layer, Filter filter, Transaction tx) throws IOException {
    	return getLocalDataSource().getFeatureReader(layer, filter, tx);
    }
   
    /**
     * 
     * @return all catchments of water type
     * 
     * @throws IOException
     */
    @Override
    public FeatureReader<SimpleFeatureType, SimpleFeature> getWaterbodies() throws IOException{
		return getLocalDataSource().getWaterbodies();
	}

	@Override
	public FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer) throws IOException {
		return getLocalDataSource().query(layer);
	}

	@Override
	public FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer, ReferencedEnvelope bounds) throws IOException {
		return getLocalDataSource().query(layer, bounds);
	}

	@Override
	public FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer, Filter filter) throws IOException {
		return getLocalDataSource().query(layer, filter);
	}

	@Override
	public FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer, ReferencedEnvelope bounds, Filter filter) throws IOException {
		return getLocalDataSource().query(layer, bounds, filter);
	}
	
		
	@Override
	public synchronized void close() {
		//if (outputDataStore != null) outputDataStore.dispose();
	}

	@Override
	public SimpleFeatureType getFeatureType(ILayer layer) throws IOException {
		return getLocalDataSource().getFeatureType(layer);
	}

	@Override
	public void logError(String message, Geometry location, String process) throws IOException {
		local.logError(message, location, process);
	}


	@Override
	public void logWarning(String message, Geometry location, String process) throws IOException {
		local.logWarning(message, location, process);
	}


	@Override
	public CoordinateReferenceSystem getCoordinateReferenceSystem() {
		return crs;
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
	
	protected String getAoiFieldName(ILayer layer) {
		if (layer != null && layer == Layer.AOI) return "id";
		return "aoi_id";
	}
	
	protected Filter getAoiFilter(ILayer layer) {
		return ff.equals(ff.property(getAoiFieldName(layer)), ff.literal(aoiUuid));
	}
	
	protected void uploadResultsInternal(DataStore outputDataStore, Transaction tx)  throws IOException {
		for (Layer l : Layer.values()) {
			logger.info("copying " + l.getLayerName() + " results to database");
			if (l == Layer.AOI) continue;
			//convert uuid to string
			try(SimpleFeatureReader reader = getLocalDataSource().getFeatureReader(l, Filter.INCLUDE, null);
					FeatureWriter<SimpleFeatureType, SimpleFeature> writer = outputDataStore.getFeatureWriterAppend(getTypeName(l), tx)){
				copyFeatures(reader, writer, getAoiFieldName(l));
			}
		}
	}
	
	
		
	/**
	 * copies features from reader to writer
	 * @param reader
	 * @param writer
	 * @param aoiFieldName
	 * @throws IOException
	 */
	protected void copyFeatures(SimpleFeatureReader reader, FeatureWriter<SimpleFeatureType, SimpleFeature> writer, String aoiFieldName) throws IOException{
		
		List<AttributeDescriptor> descriptors = writer.getFeatureType().getAttributeDescriptors();
		while(reader.hasNext()) {
			SimpleFeature next = reader.next();			
			SimpleFeature nextf = writer.next();
			
			UUID internalid = null;
			for (AttributeDescriptor ai : descriptors) {
				if (ai.getLocalName().equals(aoiFieldName)) {
					nextf.setAttribute(ai.getLocalName(), aoiUuid);		
				}else {
					Object x = next.getAttribute(ai.getLocalName());
					if (x == null) {
						nextf.setAttribute(ai.getLocalName(), null);
					}else if (ai.getType().getBinding().equals(UUID.class)) {
						nextf.setAttribute(ai.getLocalName(), UUID.fromString(x.toString()));
					}else {
						nextf.setAttribute(ai.getLocalName(), x);
					}
					
					if (ai.getLocalName().equals(ChyfAttribute.INTERNAL_ID.getFieldName())) {
						if (x != null) internalid = UUID.fromString(x.toString());
					}
				}
			}
			
			if (internalid != null) {
				nextf.getUserData().put( Hints.USE_PROVIDED_FID, true );
				((FeatureIdImpl)nextf.getIdentifier()).setID(internalid.toString());
				//this doesn't work
				//nextf.getUserData().put( Hints.PROVIDED_FID, internalid.toString() );
			}
			writer.write();		
		}
	}
	
	/**
	 * Write results back to database and clears local cache
	 * 
	 * @throws IOException 
	 */
	@Override
	public void finish() throws IOException {
		DataStore outputDataStore = createOutputDataStore();
		try {
			try(DefaultTransaction tx = new DefaultTransaction()){
				try {
					uploadResultsInternal(outputDataStore, tx);
					tx.commit();
				}catch (IOException ex) {
					tx.rollback();
					throw ex;
				}
			}
		}finally {
			outputDataStore.dispose();
		}
		local.close();
		try {
			Files.delete(local.getFileName());
		}catch (Exception ex) {
			logger.warn("Could not delete local data cache file: " + local.getFileName());
		}
		local = null;
	}
	    
	
	/**
	 * Creates output tables in output schema as required.  
	 * Subclasses can extend for custom outputs.
	 */
	protected void initOutputSchemaInternal(DataStore inputDataStore, DataStore outDataStore, Transaction tx) throws IOException{
		
		Connection c = ((JDBCDataStore)outDataStore).getConnection(tx);
		try {
			List<Layer> items = new ArrayList<>();
			for (Layer l : Layer.values()) items.add(l);
			items.remove(Layer.AOI);
			items.add(0, Layer.AOI);
			
			for (Layer l : items) {
				SimpleFeatureType out = null;
				try {
					out = outDataStore.getSchema(getTypeName(l));
				}catch(Exception ex) {}
				if (out != null) continue;
				
				if (l == Layer.ERRORS) {
					createErrorWarningsTable(c);
				}else if (l == Layer.AOI) {
					StringBuilder sb = new StringBuilder();
					sb.append("CREATE TABLE IF NOT EXISTS ");
					sb.append(outputSchema + "." + getTypeName(Layer.AOI));
					sb.append(" AS SELECT * FROM " + inputSchema + "." + getTypeName(Layer.AOI));
					sb.append(" LIMIT 0 ");
					System.out.println(sb.toString());
					c.createStatement().execute(sb.toString());
					
		    		sb = new StringBuilder();
		    		sb.append("ALTER TABLE ");
					sb.append(outputSchema + "." + getTypeName(Layer.AOI));
		    		sb.append(" ADD PRIMARY KEY (id) ");
		    		c.createStatement().executeUpdate(sb.toString());
		    		
		    		sb = new StringBuilder();
		    		sb.append("ALTER TABLE ");
					sb.append(outputSchema + "." + getTypeName(Layer.AOI));
		    		sb.append(" ADD CONSTRAINT " + outputSchema + "_aoi_name_unq UNIQUE (name) ");
		    		c.createStatement().executeUpdate(sb.toString());
				}else if (l == Layer.FEATURENAMES) {
					createNameIdTable(c);
				}else {
		    		SimpleFeatureType rtype = inputDataStore.getSchema(getTypeName(l));
		    		Name interalidatt = ChyfDataSource.findAttribute(rtype, ChyfAttribute.INTERNAL_ID);
		    		
		    		String tableName = outputSchema + "." + getTypeName(l);
			    	StringBuilder sb = new StringBuilder();
			    	//create new table
			    	sb.append("CREATE TABLE ");
			    	sb.append(tableName);
			    	sb.append(" AS SELECT ");
			    	if (interalidatt == null) {
			    		sb.append("uuid_generate_v4() as " + ChyfAttribute.INTERNAL_ID.getFieldName());
			    		sb.append(",");
			    	}
			    	sb.append(" a.* ");
			    	sb.append(" FROM " + inputSchema + "." + getTypeName(l) + " a LIMIT 0 ");
					c.createStatement().executeUpdate(sb.toString());
	
			    	sb = new StringBuilder();
			    	sb.append("ALTER TABLE ");
			    	sb.append(tableName);
			    	sb.append(" ADD PRIMARY KEY ( ");
			    	sb.append(ChyfAttribute.INTERNAL_ID.getFieldName());
			    	sb.append(")");
			    	c.createStatement().executeUpdate(sb.toString());
			    		
			    	sb = new StringBuilder();
			    	sb.append("CREATE INDEX ON ");
			    	sb.append(tableName);
			    	sb.append(" ( ");
			    	sb.append(getAoiFieldName(l));
			    	sb.append(")");
			    	c.createStatement().executeUpdate(sb.toString());
			    
			    	//TODO: this assumes geometry field
			    	sb = new StringBuilder();
			    	sb.append("CREATE INDEX ON ");
			    	sb.append(tableName);
			    	sb.append(" using gist (geometry) ");
			    	
			    	c.createStatement().executeUpdate(sb.toString());
			    }	
				
	    	}	   
		}catch (SQLException ex) {
			throw new IOException(ex);
		}
	}
	
	protected void createErrorWarningsTable(Connection c) throws IOException{
		try {
			
			StringBuilder sb = new StringBuilder();
			sb.append("CREATE TABLE ");
			sb.append(outputSchema + "." + getTypeName(Layer.ERRORS));
	    	sb.append("(");
	    	sb.append("id uuid not null default uuid_generate_v4() primary key, ");
	    	sb.append(getAoiFieldName(Layer.ERRORS) + " uuid not null references ");
	    	sb.append(outputSchema + "." + getTypeName(Layer.AOI) + " (" + getAoiFieldName(Layer.AOI) + "),"); 
	    	sb.append("type varchar(32), ");
	    	sb.append("message varchar, ");
	    	sb.append("process varchar, ");
	    	sb.append("geometry GEOMETRY )");
			c.createStatement().executeUpdate(sb.toString());
		}catch (SQLException ex) {
			throw new IOException(ex);
		}
	}
	
	protected void createNameIdTable(Connection c) throws IOException {
		try{
			StringBuilder sb = new StringBuilder();
			sb.append("CREATE TABLE IF NOT EXISTS ");
			sb.append(outputSchema + "." + getTypeName(Layer.FEATURENAMES));
			sb.append("(");
	    	sb.append(getAoiFieldName(Layer.FEATURENAMES) + " uuid not null references " + outputSchema + "." + getTypeName(Layer.AOI) + " (" + getAoiFieldName(Layer.AOI) + "), "); 
			sb.append(" fid varchar, name_id varchar, geodbname varchar, name varchar, primary key (fid)) ");
			c.createStatement().execute(sb.toString());
   
    		sb = new StringBuilder();
    		sb.append("ALTER TABLE ");
			sb.append(outputSchema + "." + getTypeName(Layer.FEATURENAMES));
    		sb.append(" ADD CONSTRAINT feature_names_aoi_id_unq UNIQUE (aoi_id, name_id) ");
    		c.createStatement().execute(sb.toString());
    		
		}catch (SQLException ex) {
			throw new IOException(ex);
		}
	}

	
	/**
	 * Creates output tables in output schema as required.  
	 * 
	 */
	protected void initOutputSchema(DataStore inputDataStore) throws IOException{
		DataStore outputDataStore = createOutputDataStore();
		try {
			try(Transaction tx = new DefaultTransaction()){
				try {
					Connection c = ((JDBCDataStore)inputDataStore).getConnection(tx);
					c.createStatement().execute("CREATE SCHEMA IF NOT EXISTS " + outputSchema);
					tx.commit();
					
					initOutputSchemaInternal(inputDataStore, outputDataStore, tx);
					tx.commit();
				}catch (SQLException ex) {
					tx.rollback();
					throw new IOException(ex);
				}
			}
		}finally {
			outputDataStore.dispose();
		}
	}	
}
