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
package net.refractions.chyf.elevation;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geometry.jts.WKBReader;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.io.WKBWriter;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Postgresql data source for elevation processing.
 * 
 * @author Emily
 *
 */
public class ElevationPostGisDataSource implements IElevationDataSource {

	static final Logger logger = LoggerFactory.getLogger(ElevationPostGisDataSource.class.getCanonicalName());

	public static final String WORKING_TABLE = "public.elevation_processing";
	
	public static final double BLOCK_SIZE = 0.4;

	protected Connection connection;
	private Integer eflowpathSrid = null;

	private String eflowpathTable = "";
	private String eflowpathSchema = "";
	private String geomField = "";
	private String cogPath = "";

	/**
	 * Connects to the database and configures the working table (if necessary)
	 * 
	 * @param connectionString
	 * @param eflowpathTable
	 * @param geometryCol
	 * @param doInit
	 * @throws Exception
	 */
	public ElevationPostGisDataSource(String connectionString, String eflowpathTable, 
			String geometryCol, int srid, String cogPath, boolean doInit) throws Exception {
		String[] bits = eflowpathTable.split("\\.");
		this.eflowpathSchema = bits[0];
		this.eflowpathTable = bits[1];
		this.geomField = geometryCol;
		this.eflowpathSrid = srid;
		this.cogPath = cogPath;
		
		connect(connectionString);
		if (doInit) {
			initWorkingTable();
		}
	}
	
	public String getCogPath() {
		return this.cogPath;
	}

	public synchronized void connect(String connectionString) throws SQLException, IOException {
		String[] bits = connectionString.split(";");
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

	    try (PreparedStatement ps = connection.prepareStatement(sb.toString())) {
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
		
		try(PreparedStatement ps = connection.prepareStatement(sb.toString())){
			ps.setString(1, "done");
			ps.setInt(2, block.getBlockId());
			ps.execute();
		}
	}
	
	private void initWorkingTable() throws Exception {

		StringBuilder sb = new StringBuilder();
		sb.append("DROP TABLE IF EXISTS ");
		sb.append(WORKING_TABLE);
		connection.createStatement().execute(sb.toString());

		sb = new StringBuilder();
		sb.append("CREATE TABLE ");
		sb.append(WORKING_TABLE);
		sb.append("(block_id serial primary key,");
		sb.append("status varchar, ");
		sb.append("x_min double precision, x_max double precision, ");
		sb.append("y_min double precision, y_max double precision, srid integer)");
		connection.createStatement().execute(sb.toString());

		sb = new StringBuilder();
		sb.append("SELECT ST_XMin(ext),ST_YMin(ext), ");
		sb.append("ST_XMax(ext), ST_YMax(ext) ");
		sb.append("FROM (");
		sb.append(" SELECT ST_Extent(" + geomField + ") AS ext ");
		sb.append(" FROM ");
		sb.append(eflowpathSchema + "." + eflowpathTable);
		//sb.append(" WHERE id = '867f8d54-b337-4650-9c67-e72241fe9537'");
		sb.append(") AS sub");

		ReferencedEnvelope env = null;
		try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sb.toString())) {

			if (rs.next()) {
				double minX = rs.getDouble(1);
				double minY = rs.getDouble(2);
				double maxX = rs.getDouble(3);
				double maxY = rs.getDouble(4);

				CoordinateReferenceSystem crs = CRS.decode("EPSG:" + eflowpathSrid, true);
				env = new ReferencedEnvelope(minX, maxX, minY, maxY, crs);
			} else {
				throw new IOException("No extent found for " + eflowpathSchema + "." + eflowpathTable);
			}

		} catch (SQLException | FactoryException e) {
			throw new IOException("Error retrieving extent for " + eflowpathSchema + "." + eflowpathTable, e);
		}

		sb = new StringBuilder();
		sb.append("INSERT INTO ");
		sb.append(WORKING_TABLE);
		sb.append("(status, x_min, x_max, y_min, y_max, srid) values (?,?,?,?,?,?)");
		try {
			connection.setAutoCommit(false);
			try (PreparedStatement ps = connection.prepareStatement(sb.toString())) {
				// break env into .1 degree increments
				
				for (double x = env.getMinX(); x <= env.getMaxX(); x += BLOCK_SIZE) {
					for (double y = env.getMinY(); y <= env.getMaxY(); y += BLOCK_SIZE) {
						ps.setString(1, "ready");
						ps.setDouble(2, x);
						ps.setDouble(3, x + BLOCK_SIZE);
						ps.setDouble(4, y);
						ps.setDouble(5, y + BLOCK_SIZE);
						ps.setInt(6, eflowpathSrid);
						ps.addBatch();
					}
					ps.executeBatch();
				}
				ps.executeBatch();
			}
			connection.commit();
		} catch (Exception ex) {
			logger.error("Error configuring blocks", ex);
			connection.rollback();
			throw ex;
		} finally {

			connection.setAutoCommit(true);
		}
	}


	public void close() throws SQLException {
		connection.close();
	}

	@Override
	public void updateFlowathGeometries(Collection<EFlowpath> features) throws Exception {

		//TODO: need to upgrade to write 4d geometries
		WKBWriter writer = new WKBWriter(3);
	    
		StringBuilder sb = new StringBuilder();
		sb.append("UPDATE ");
		sb.append(eflowpathSchema + "." + eflowpathTable);
		sb.append(" SET ");
		sb.append(geomField);
		sb.append(" = st_setsrid(ST_GeomFromEWKB(?), ?) WHERE id = ?");

		connection.setAutoCommit(false);
		try {
			PreparedStatement ps = connection.prepareStatement(sb.toString());
			int cnt = 0;

			for (EFlowpath edge : features) {
				if (cnt > 1000) {
					ps.executeBatch();
					cnt = 0;
				}
				cnt++;
				ps.setObject(1, writer.write(edge.getLineString()));
				ps.setObject(2, eflowpathSrid);
				ps.setObject(3, edge.getId());
				
				ps.addBatch();
			}
			ps.executeBatch();
			connection.commit();
		} catch (Exception ex) {
			logger.error("Error updating flowpath geometries. ", ex);
			connection.rollback();
			connection.setAutoCommit(true);
		}

	}

	@Override
	public List<EFlowpath> getFlowPaths(ReferencedEnvelope bounds) throws Exception {

		StringBuilder sb = new StringBuilder();
		sb.append("SELECT id, ST_AsEWKB(ST_Force3D(" + geomField + ")) FROM ");
		sb.append(eflowpathSchema + "." + eflowpathTable);
		sb.append(" WHERE st_intersects(" + geomField + ", ST_MakeEnvelope(?, ?, ?, ?, ?)) ");
		//sb.append(" and id = '867f8d54-b337-4650-9c67-e72241fe9537'");

		PackedCoordinateSequenceFactory sequenceFactory = 
                new PackedCoordinateSequenceFactory(PackedCoordinateSequenceFactory.DOUBLE);
        GeometryFactory geometryFactory = new GeometryFactory(sequenceFactory);

        WKBReader wkbReader = new WKBReader(geometryFactory);

		List<EFlowpath> edges = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(sb.toString())) {

			ps.setDouble(1, bounds.getMinX());
			ps.setDouble(2, bounds.getMinY());
			ps.setDouble(3, bounds.getMaxX());
			ps.setDouble(4, bounds.getMaxY());
			ps.setInt(5, eflowpathSrid);

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					EFlowpath i = new EFlowpath((UUID) rs.getObject(1), (LineString) wkbReader.read(rs.getBytes(2)));
					edges.add(i);

				}
			}
		}
		return edges;

	}

}
