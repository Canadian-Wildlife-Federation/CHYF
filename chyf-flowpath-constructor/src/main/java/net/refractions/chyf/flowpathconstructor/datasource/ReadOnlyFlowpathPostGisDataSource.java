/*
 * Copyright 2021 Canadian Wildlife Federation
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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import org.geotools.data.DataStoreFinder;
import org.geotools.data.FeatureReader;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.util.NullProgressListener;
import org.geotools.jdbc.JDBCDataStore;
import org.geotools.jdbc.JDBCDataStoreFactory;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureVisitor;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfLogger;
import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfPostGisDataSource;
import net.refractions.chyf.datasource.FlowDirection;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.ConstructionPoint;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkelLineString;

/**
 * 
 * Geopackage data source for flowpath tools that only provides read support. Used for
 * independent cycle validation tool
 * 
 * @author Emily
 *
 */
public class ReadOnlyFlowpathPostGisDataSource extends ChyfPostGisDataSource implements IFlowpathDataSource{

	static final Logger logger = LoggerFactory.getLogger(ReadOnlyFlowpathPostGisDataSource.class.getCanonicalName());

	public ReadOnlyFlowpathPostGisDataSource(String connectionString, String schema) throws IOException {
		ChyfLogger.INSTANCE.setDataSource(this);
		
		this.workingSchema = schema;
		
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
		connectionParameters.put("schema", workingSchema);
		connectionParameters.put("database", dbname);
		connectionParameters.put("user", user);
		connectionParameters.put("passwd", password);

		connectionParameters.put(JDBCDataStoreFactory.EXPOSE_PK.key, Boolean.TRUE);
	    connectionParameters.put("schema", workingSchema);
	    workingDataStore = DataStoreFinder.getDataStore(connectionParameters);
	    
	    
	    Connection c = ((JDBCDataStore)workingDataStore).getConnection(Transaction.AUTO_COMMIT);
	    
		// read the srid from the eflowpath table
		try {
			StringBuilder sb = new StringBuilder();
			sb.append("SELECT srid ");
			sb.append("FROM geometry_columns ");
			sb.append(" WHERE f_table_schema = ? AND f_table_name = ?");

			try (PreparedStatement ps = c.prepareStatement(sb.toString())) {
				ps.setString(1, workingSchema);
				ps.setString(2, getTypeName(Layer.EFLOWPATHS));
				try (ResultSet rs = ps.executeQuery()) {
					if (rs.next()) {
						int srid = rs.getInt(1);
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
	public List<TerminalNode> getTerminalNodes() throws Exception{
		List<TerminalNode> pnt = new ArrayList<>();
		try(FeatureReader<SimpleFeatureType, SimpleFeature> reader = getFeatureReader(Layer.TERMINALNODES,
				getAoiFilter(Layer.TERMINALNODES), Transaction.AUTO_COMMIT)){
			
			Name flowdirAttribute = ChyfDataSource.findAttribute(reader.getFeatureType(), ChyfAttribute.FLOWDIRECTION);
			Map<ChyfAttribute, Name> nameAttributes = findRiverNameAttributes(reader.getFeatureType());
			
			while(reader.hasNext()){
				SimpleFeature point = reader.next();
				Point pg = ChyfDataSource.getPoint(point);
				FlowDirection fd = FlowDirection.parseValue( ((Number)point.getAttribute(flowdirAttribute)).intValue() );
				
				TerminalNode node = new TerminalNode(pg, fd);
				node.setNames(getRiverNameIds(nameAttributes, point));
				pnt.add(node);
			}
		}
		return pnt;
	}

	@Override
	public void setAoi(String aoiId) throws IOException {
		//query aoi table for aoi id
	    Connection c = ((JDBCDataStore)workingDataStore).getConnection(Transaction.AUTO_COMMIT);
	    StringBuilder sb = new StringBuilder();
	    sb.append("SELECT id FROM ");
	    sb.append(workingSchema + "." + getTypeName(Layer.AOI));
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
	public void logError(String message, Geometry location, String process) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");
	}


	@Override
	public void logWarning(String message, Geometry location, String process) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");
	}


	@Override
	public void updateWaterbodyGeometries(Collection<Polygon> polygons) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");
	}


	@Override
	public void updateCoastline(FeatureId fid, LineString newls, Transaction tx) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");		
	}


	@Override
	public void createConstructionsPoints(List<ConstructionPoint> points) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");			
	}


	@Override
	public void writeSkeletons(Collection<SkelLineString> skeletons) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");	
	}


	@Override
	public void removeExistingSkeletons(boolean bankOnly) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");	
	}


	@Override
	public List<ConstructionPoint> getConstructionsPoints(Object catchmentId) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");		
	}


	@Override
	public void addRankAttribute() throws Exception {
		throw new UnsupportedOperationException("Not supported for read only dataset");		
	}


	@Override
	public void flipFlowEdges(Collection<FeatureId> pathstoflip, Collection<FeatureId> processed) throws Exception {
		throw new UnsupportedOperationException("Not supported for read only dataset");	
	}


	@Override
	public Set<Coordinate> getOutputConstructionPoints() throws Exception {
		throw new UnsupportedOperationException("Not supported for read only dataset");	
	}


	@Override
	public void writeRanks(Map<FeatureId, RankType> ranks) throws Exception {
		throw new UnsupportedOperationException("Not supported for read only dataset");			
	}

	@Override
	public void writeFlowpathNames(HashMap<FeatureId, String[]> nameids) throws IOException{
		throw new UnsupportedOperationException("Not supported for read only dataset");
	}
}
