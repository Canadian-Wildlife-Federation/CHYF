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
package net.refractions.chyf.elevation.smooth;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.elevation.AppProperties;


/**
 * Postgresql data source for elevation smoothing processing.
 * 
 * @author Emily
 *
 */
public class ZSmootherPostGisDataSource implements IZSmootherDataSource  {

	static final Logger logger = LoggerFactory.getLogger(ZSmootherPostGisDataSource.class.getCanonicalName());

	public static final String WORKING_TABLE = "public.elevation_smoothing";
	public static final String WORKING_LINK_TABLE = "public.elevation_smoothing_link";

	private String connectionString;
	protected Connection connection;
	protected AppProperties properties;
	
	
	/**
	 * Connects to the database and configures the working table (if necessary)
	 * 
	 * @param connectionString
	 * @param eflowpathTable
	 * @param geometryCol
	 * @param doInit
	 * @throws Exception
	 */
	public ZSmootherPostGisDataSource(String connectionString, AppProperties properties) {
		this.properties = properties;
		this.connectionString = connectionString;
	}
	
	private synchronized Connection getConnection() throws SQLException {
		if (connection != null) return this.connection;
		
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
	    sb.append("RETURNING block_id");

	    try (PreparedStatement ps = getConnection().prepareStatement(sb.toString())) {
	        ps.setString(1, "processing");
	        ps.setString(2, "ready");

	        try (ResultSet rs = ps.executeQuery()) {
	            if (rs.next()) {
	            	return new Block(rs.getInt(1));
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

		for (String table : new String[] {WORKING_LINK_TABLE, WORKING_TABLE}) {
			getConnection().createStatement().execute("DROP TABLE IF EXISTS " + table);
		}

		StringBuilder sb = new StringBuilder();
		sb.append("CREATE TABLE ");
		sb.append(WORKING_LINK_TABLE);
		sb.append("(block_id int, graph_id int)");
		getConnection().createStatement().execute(sb.toString());

		sb = new StringBuilder();
		sb.append("CREATE TABLE ");
		sb.append(WORKING_TABLE);
		sb.append("(block_id int primary key, status varchar)");
		getConnection().createStatement().execute(sb.toString());

		
		sb = new StringBuilder();
		sb.append("SELECT a.graph_id, count(a.*) FROM ");
		sb.append(properties.getEflowpathPropertiesTable() + " a ");
		if (properties.hasAoiFilter()) {
			sb.append(" JOIN ");
			sb.append( properties.getEflowpathTable() + " b on a.id = b.id " );
			sb.append(" JOIN ");
			sb.append( properties.getAoiTable() + " c on b.aoi_id = c.id ");
			sb.append(" WHERE c.short_name in (" + "?,".repeat(properties.getAoi().size()-1) + "?)");
		}
		sb.append(" GROUP BY graph_id ORDER BY count(*) DESC");

		
		String insert = "INSERT INTO " + WORKING_LINK_TABLE + " (block_id, graph_id) values (?,?)";
		String insertb = "INSERT INTO " + WORKING_TABLE + " (block_id, status) values (?,?)";
		
		try (PreparedStatement st = getConnection().prepareStatement(sb.toString()); 				
				PreparedStatement pslink = getConnection().prepareStatement(insert);
				PreparedStatement psblock = getConnection().prepareStatement(insertb)) {

			for (int i = 0; i < properties.getAoi().size(); i ++) {
				st.setString(i + 1,  properties.getAoi().get(i));
			}
			
			int currentBlockId = 0;
			int currentBlockCnt = properties.getSmoothingTargetGroupSize() + 1;;
			
			try(ResultSet rs = st.executeQuery()){
				while (rs.next()) {
					int graphid = rs.getInt(1);
					int cnt = rs.getInt(2);
					
					if (currentBlockCnt + cnt > properties.getSmoothingTargetGroupSize()) {
						currentBlockId ++;
						currentBlockCnt = cnt;
						
						psblock.setInt(1, currentBlockId);
						psblock.setString(2, "ready");
						psblock.execute();
						
						pslink.executeBatch();
					}else {
						currentBlockCnt += cnt;
					}				
					
					pslink.setInt(1, currentBlockId);
					pslink.setInt(2, graphid);
					pslink.addBatch();
				}
				pslink.executeBatch();
			}

		} catch (SQLException e) {
			throw new IOException("Error generating processing blocks.", e);
		}

		
		sb = new StringBuilder();
		sb.append("CREATE INDEX working_table_link_graph_idx on ");
		sb.append(WORKING_LINK_TABLE);
		sb.append("(graph_id)");
		getConnection().createStatement().execute(sb.toString());
		
		sb = new StringBuilder();
		sb.append("CREATE INDEX working_table_link_blk_idx on ");
		sb.append(WORKING_LINK_TABLE);
		sb.append("(block_id)");
		getConnection().createStatement().execute(sb.toString());
	}


	public void close() throws SQLException {
		if (connection != null) connection.close();
	}

	
	public void updateFlowpathGeometries(Collection<EFlowpath> features) throws Exception {

		WKBWriter writer = new WKBWriter(4);

		StringBuilder sb = new StringBuilder();
		sb.append("UPDATE ");
		sb.append(properties.getEflowpathTable());
		sb.append(" SET ");
		sb.append(properties.getGeometryColumn());
		sb.append(" = st_setsrid(st_geomfromewkb(?), " + properties.getSrid() + ") WHERE id = ?");

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
				ps.setObject(2, edge.getId());		
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

	
	public HashMap<UUID, Node> getNodeGraph(Block block) throws SQLException {
		logger.info("Getting node graph: " + block.getBlockId());

		StringBuilder sb = new StringBuilder();
		sb.append("SELECT a.id, a.from_nexus_id, a.to_nexus_id, ");
		sb.append("ST_Z(ST_StartPoint(a." + properties.getGeometryColumn() + ")) AS from_z, " );
		sb.append("ST_Z(ST_EndPoint(a." + properties.getGeometryColumn() + ")) AS to_z " );
		sb.append(" FROM ");
		sb.append(properties.getEflowpathTable() + " a ");
		sb.append(" join " + properties.getEflowpathPropertiesTable() + " b on a.id = b.id ");
		sb.append(" join " + WORKING_LINK_TABLE + " c on b.graph_id = c.graph_id ");
		sb.append(" WHERE c.block_id = ? ");
		
		HashMap<UUID, Node> nodes = new HashMap<>();
		
		try (PreparedStatement ps = getConnection().prepareStatement(sb.toString())) {

			ps.setInt(1, block.getBlockId());

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					UUID edgeid = (UUID) rs.getObject(1);

					UUID fromNodeId = (UUID)rs.getObject(2);
					UUID toNodeId = (UUID)rs.getObject(3);
					Double fromZ = rs.getDouble(4);
					Double toZ = rs.getDouble(5);
					
					Node n = nodes.get(fromNodeId);
					if (n == null) {
						n = new Node(fromNodeId, fromZ);
						nodes.put(fromNodeId, n);
					}
					n.addOutNode(toNodeId);

					
					n = nodes.get(toNodeId);
					if (n == null) {
						n = new Node(toNodeId, toZ);
						nodes.put(toNodeId, n);
					}
					n.addInNode(fromNodeId);
					
				}
			}
		}
		return nodes;
	}
	
	
	public List<EFlowpath> getFlowPaths(Block block, UUID lastId) throws Exception {

		
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT a.id, a.from_nexus_id, a.to_nexus_id, ");
		sb.append(" st_Asbinary(a." + properties.getGeometryColumn() + ") " );
		sb.append(" FROM ");
		sb.append(properties.getEflowpathTable() + " a ");
		sb.append(" join " + properties.getEflowpathPropertiesTable() + " b on a.id = b.id ");
		sb.append(" join " + WORKING_LINK_TABLE + " c on b.graph_id = c.graph_id ");
		sb.append(" WHERE c.block_id = ? AND a.id > ? ");
		sb.append(" ORDER BY a.id ");
		sb.append(" LIMIT " + properties.getSmoothingFlowpathBatchSize());

		GeometryFactory geometryFactory = new GeometryFactory();
		WKBReader wkbReader = new WKBReader(geometryFactory);

		List<EFlowpath> edges = new ArrayList<>();
		try (PreparedStatement ps = getConnection().prepareStatement(sb.toString())) {
			ps.setInt(1, block.getBlockId());
			ps.setObject(2, lastId);
			
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					UUID edgeId = (UUID) rs.getObject(1);
					UUID fromNexusId = (UUID)rs.getObject(2);
					UUID toNexusId = (UUID)rs.getObject(3);
					LineString ls = (LineString) wkbReader.read(rs.getBytes(4));
					
					edges.add(new EFlowpath(edgeId, ls, fromNexusId, toNexusId));

				}
			}
		}
		return edges;

	}

}
