/*
 * Copyright 2026 Canadian Wildlife Federation
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
package net.refractions.chyf.elevation.raw;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geometry.jts.WKBReader;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.io.WKBWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.elevation.AppProperties;

/**
 * Postgresql data source for elevation processing.
 * 
 * @author Emily
 *
 */
public class ElevationPostGisDataSource implements IElevationDataSource {

	static final Logger logger = LoggerFactory.getLogger(ElevationPostGisDataSource.class.getCanonicalName());

	public static final String WORKING_TABLE = "public.elevation_processing";
	
	protected String connectionString;
	protected Connection connection = null;
	
	protected AppProperties properties;

	/**
	 * Connects to the database and configures the working table (if necessary)
	 * 
	 * @param connectionString
	 * @param eflowpathTable
	 * @param geometryCol
	 * @throws Exception
	 */
	public ElevationPostGisDataSource(String connectionString, AppProperties properties) {
		this.properties = properties;		
		this.connectionString = connectionString;
	}
	
	public String getCogPath() {
		return this.properties.getCogPath();
	}

	public synchronized Connection getConnection() throws SQLException {
		if (connection != null) return connection;
		
		String[] bits = this.connectionString.split(";");
		String host = null;
		Integer port = null;
		String db = null;
		String user = null;
		String pass = null;
		for (String bit : bits) {
			if (bit.startsWith("host=")) {
				host = bit.substring("host=".length());
			} else if (bit.startsWith("port=")) {
				port = Integer.valueOf(bit.substring("port=".length()));
			} else if (bit.startsWith("db=")) {
				db = bit.substring("db=".length());
			} else if (bit.startsWith("user=")) {
				user = bit.substring("user=".length());
			} else if (bit.startsWith("password=")) {
				pass = bit.substring("password=".length());
			}
		}
		if (host == null || db == null || user == null || pass == null) {
			throw new SQLException("Host, database, user or password not supplied.");
		}

		connection = DriverManager.getConnection(
				"jdbc:postgresql://" + host + ":" + port + "/" + db + "?user=" + user + "&password=" + pass);
		return connection;
	}

	
	
	public Block checkOutNextBlock() throws Exception{
		StringBuilder sb = new StringBuilder();
	    sb.append("UPDATE ");
	    sb.append(WORKING_TABLE);
	    sb.append(" SET status = ? ");
	    sb.append("WHERE block_id = ( ");
	    sb.append(" SELECT block_id FROM ");
	    sb.append(WORKING_TABLE);
	    sb.append(" WHERE status = ? ");
	    sb.append(" LIMIT 1 ");
	    sb.append(" FOR UPDATE SKIP LOCKED ");
	    sb.append(") ");
	    sb.append("RETURNING block_id, x_min, x_max, y_min, y_max, srid");

	    try (PreparedStatement ps = getConnection().prepareStatement(sb.toString())) {
	        ps.setString(1, "processing");
	        ps.setString(2, "ready");

	        try (ResultSet rs = ps.executeQuery()) {
	            if (rs.next()) {
					CoordinateReferenceSystem crs = CRS.decode("EPSG:" + rs.getInt(6), true);
	            	ReferencedEnvelope re = new ReferencedEnvelope(rs.getDouble(2), rs.getDouble(3), rs.getDouble(4), rs.getDouble(5), crs);
	                return new Block(rs.getInt(1),re);
	            } else {
	                return null; // nothing left to claim
	            }
	        }
	    } catch (SQLException ex) {
	    	logger.error("Error checking out block", ex);
	        return null;
	    }
	}
	
	public void finishBlock(Block block) throws SQLException {
		StringBuilder sb = new StringBuilder();
		sb.append("UPDATE ");
		sb.append(WORKING_TABLE);
		sb.append(" set status = ? where block_id = ?");
		
		try(PreparedStatement ps = getConnection().prepareStatement(sb.toString())){
			ps.setString(1, "done");
			ps.setInt(2, block.getBlockId());
			ps.execute();
		}
	}
	
	public void initWorkingTable() throws Exception {

		StringBuilder sb = new StringBuilder();
		sb.append("DROP TABLE IF EXISTS ");
		sb.append(WORKING_TABLE);
		getConnection().createStatement().execute(sb.toString());

		sb = new StringBuilder();
		sb.append("CREATE TABLE ");
		sb.append(WORKING_TABLE);
		sb.append("(block_id serial primary key,");
		sb.append("status varchar, ");
		sb.append("x_min double precision, x_max double precision, ");
		sb.append("y_min double precision, y_max double precision, srid integer)");
		getConnection().createStatement().execute(sb.toString());

		sb = new StringBuilder();
		sb.append("SELECT ST_XMin(ext),ST_YMin(ext), ");
		sb.append("ST_XMax(ext), ST_YMax(ext) ");
		sb.append("FROM (");
		sb.append(" SELECT ST_Extent(a." + this.properties.getGeometryColumn() + ") AS ext ");
		sb.append(" FROM ");
		sb.append(this.properties.getEflowpathTable() + " a ");
		if (properties.hasAoiFilter()) {
			sb.append(" JOIN " );
			sb.append(properties.getAoiTable());
			sb.append(" b on a.aoi_id = b.id ");
			sb.append(" WHERE  b.short_name in (" + "?,".repeat(properties.getAoi().size()-1) + "?)");
		}
		sb.append(") AS sub");

		ReferencedEnvelope env = null;
		try (PreparedStatement st = getConnection().prepareStatement(sb.toString())) {
			if (properties.hasAoiFilter()) {
				for (int i = 0; i < properties.getAoi().size(); i ++) {
					st.setString(i+1, properties.getAoi().get(i));
				}
			}

			try (ResultSet rs = st.executeQuery()) {

				if (rs.next()) {
					double minX = rs.getDouble(1);
					double minY = rs.getDouble(2);
					double maxX = rs.getDouble(3);
					double maxY = rs.getDouble(4);
					if (minX == maxX || minY == maxY) {
						//nothing to do
						logger.info("Nothing to do - no edges found for given aoi filter");
						return;
					}
					CoordinateReferenceSystem crs = CRS.decode("EPSG:" + this.properties.getSrid(), true);
					env = new ReferencedEnvelope(minX, maxX, minY, maxY, crs);
				} else {
					throw new IOException("No extent found for " + this.properties.getEflowpathTable());
				}
			}

		} catch (SQLException | FactoryException e) {
			throw new IOException("Error retrieving extent for " + this.properties.getEflowpathTable(), e);
		}

		sb = new StringBuilder();
		sb.append("INSERT INTO ");
		sb.append(WORKING_TABLE);
		sb.append("(status, x_min, x_max, y_min, y_max, srid) values (?,?,?,?,?,?)");
		try {
			getConnection().setAutoCommit(false);
			try (PreparedStatement ps = getConnection().prepareStatement(sb.toString())) {
				double blockSize = properties.getBlockSize();
				for (double x = env.getMinX(); x <= env.getMaxX(); x += blockSize) {
					for (double y = env.getMinY(); y <= env.getMaxY(); y += blockSize) {
						ps.setString(1, "ready");
						ps.setDouble(2, x);
						ps.setDouble(3, x + blockSize);
						ps.setDouble(4, y);
						ps.setDouble(5, y + blockSize);
						ps.setInt(6, properties.getSrid());
						ps.addBatch();
					}
					ps.executeBatch();
				}
				ps.executeBatch();
			}
			getConnection().commit();
		} catch (Exception ex) {
			logger.error("Error configuring blocks", ex);
			getConnection().rollback();
			throw ex;
		} finally {

			getConnection().setAutoCommit(true);
		}
	}


	public void close() throws SQLException {
		if (this.connection != null) {
			connection.close();
		}
	}

	@Override
	public void updateFlowpathGeometries(Collection<EFlowpath> features) throws Exception {

		WKBWriter writer = new WKBWriter(3);
	    
		StringBuilder sb = new StringBuilder();
		sb.append("UPDATE ");
		sb.append(properties.getEflowpathTable());
		sb.append(" SET ");
		sb.append(properties.getGeometryColumn());
		sb.append(" = st_setsrid(ST_GeomFromEWKB(?), ?) WHERE id = ?");

		getConnection().setAutoCommit(false);
		try (PreparedStatement ps = getConnection().prepareStatement(sb.toString())){
			int cnt = 0;

			for (EFlowpath edge : features) {
				if (cnt > 1000) {
					ps.executeBatch();
					cnt = 0;
				}
				cnt++;
				ps.setObject(1, writer.write(edge.getLineString()));
				ps.setObject(2, properties.getSrid());
				ps.setObject(3, edge.getId());
				ps.addBatch();
			}
			ps.executeBatch();
			getConnection().commit();
		} catch (Exception ex) {
			logger.error("Error updating flowpath geometries. ", ex);
			getConnection().rollback();	
			throw ex;
		}finally {
			getConnection().setAutoCommit(true);
		}

	}

	@Override
	public List<EFlowpath> getFlowPaths(ReferencedEnvelope bounds) throws Exception {

		StringBuilder sb = new StringBuilder();
		sb.append("SELECT a.id, ST_AsEWKB(ST_Force3D(a." + properties.getGeometryColumn() + ")) FROM ");
		sb.append(properties.getEflowpathTable() + " a ");
		if (properties.hasAoiFilter()) {
			sb.append(" JOIN " );
			sb.append(properties.getAoiTable());
			sb.append(" b on a.aoi_id = b.id ");
			sb.append(" WHERE  b.short_name in (" + "?,".repeat(properties.getAoi().size()-1) + "?)");
			sb.append(" AND ");
		}else {
			sb.append(" WHERE ");
		}
		sb.append(" st_intersects(a." + properties.getGeometryColumn() + ", ST_MakeEnvelope(?, ?, ?, ?, ?)) ");

		PackedCoordinateSequenceFactory sequenceFactory = 
                new PackedCoordinateSequenceFactory(PackedCoordinateSequenceFactory.DOUBLE);
        GeometryFactory geometryFactory = new GeometryFactory(sequenceFactory);

        WKBReader wkbReader = new WKBReader(geometryFactory);

		List<EFlowpath> edges = new ArrayList<>();
		try (PreparedStatement ps = getConnection().prepareStatement(sb.toString())) {
			int i = 1;
			if (properties.hasAoiFilter()) {
				for(String aoi : properties.getAoi()) {
					ps.setString(i++, aoi);
				}
			}
			ps.setDouble(i++, bounds.getMinX());
			ps.setDouble(i++, bounds.getMinY());
			ps.setDouble(i++, bounds.getMaxX());
			ps.setDouble(i++, bounds.getMaxY());
			ps.setInt(i++, properties.getSrid());

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					EFlowpath edge = new EFlowpath((UUID) rs.getObject(1), (LineString) wkbReader.read(rs.getBytes(2)));
					edges.add(edge);
				}
			}
		}
		return edges;

	}

}
