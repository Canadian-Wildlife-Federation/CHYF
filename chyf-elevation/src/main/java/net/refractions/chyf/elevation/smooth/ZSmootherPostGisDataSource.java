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
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

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
	/**
	 * A second connection dedicated to streaming flowpaths through a
	 * server side cursor. It is kept separate from the main connection so
	 * that geometry updates can be committed without closing the cursor.
	 */
	protected Connection readConnection;
	protected AppProperties properties;
	
	public static double NO_DATA = -9999;

	/**
	 * Rows fetched at a time when streaming the node graph. The rows are
	 * small (two uuids and two doubles) so this can be much larger than the
	 * flowpath batch size.
	 */
	private static final int NODE_FETCH_SIZE = 100_000;
	
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
		if (connection == null) connection = createConnection();
		return connection;
	}

	/**
	 * @return the connection used to stream flowpaths; it never has
	 * autocommit enabled as that is required for server side cursors
	 */
	private synchronized Connection getReadConnection() throws SQLException {
		if (readConnection == null) {
			readConnection = createConnection();
			readConnection.setAutoCommit(false);
		}
		return readConnection;
	}

	private Connection createConnection() throws SQLException {
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

		return DriverManager.getConnection(
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
		if (readConnection != null) readConnection.close();
		if (connection != null) connection.close();
	}

	
	public HashMap<UUID, Node> getNodeGraph(Block block) throws SQLException {
		logger.info("Getting node graph: " + block.getBlockId());

		StringBuilder sb = new StringBuilder();
		sb.append("SELECT a.from_nexus_id, a.to_nexus_id, ");
		sb.append("ST_Z(ST_StartPoint(a." + properties.getGeometryColumn() + ")) AS from_z, " );
		sb.append("ST_Z(ST_EndPoint(a." + properties.getGeometryColumn() + ")) AS to_z " );
		sb.append(" FROM ");
		sb.append(properties.getEflowpathTable() + " a ");
		sb.append(" join " + properties.getEflowpathPropertiesTable() + " b on a.id = b.id ");
		sb.append(" join " + WORKING_LINK_TABLE + " c on b.graph_id = c.graph_id ");
		sb.append(" WHERE c.block_id = ? ");
		
		HashMap<UUID, Node> nodes = new HashMap<>();

		//the read connection is used (never autocommit) so that a fetch size
		//forces a server side cursor; without it the driver pulls every row of
		//the block into memory before the first node is built, which on the
		//largest block is enough to exhaust the heap on its own
		Connection readCon = getReadConnection();

		try (PreparedStatement ps = readCon.prepareStatement(sb.toString(), ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_READ_ONLY)) {

			ps.setFetchSize(NODE_FETCH_SIZE);
			ps.setInt(1, block.getBlockId());

			int cnt = 0;

			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					if (++cnt % 1_000_000 == 0) {
						logger.info(MessageFormat.format("Block {0}: {1} graph edges read ({2} nodes).",
								block.getBlockId(), cnt, nodes.size()));
					}
					UUID fromNodeId = (UUID)rs.getObject(1);
					UUID toNodeId = (UUID)rs.getObject(2);
					
					Double fromZ = NO_DATA;
					if (rs.getObject(3) == null) {
						logger.warn(MessageFormat.format("A geometry in block {0} has no Z value. You should add a z value to this before smoothing.  See node {1}.", block.blockId, fromNodeId), ps);						
					}else {
						fromZ = rs.getDouble(3);
					}
					
					Double toZ = NO_DATA;
					if (rs.getObject(4) == null) {
						logger.warn(MessageFormat.format("A geometry in block {0} has no Z value. You should add a z value to this before smoothing.  See node {1}.", block.blockId, toNodeId), ps);						
					}else {
						toZ = rs.getDouble(4);
					}
					
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
			logger.info(MessageFormat.format("Block {0}: node graph built from {1} edges ({2} nodes).",
					block.getBlockId(), cnt, nodes.size()));
		} finally {
			//release the read snapshot; the select is read only so there is
			//nothing to commit
			readCon.rollback();
		}
		return nodes;
	}
	
	
	public void processFlowPaths(Block block, Consumer<EFlowpath> processor) throws Exception {

		int batchSize = properties.getSmoothingFlowpathBatchSize();

		Connection readCon = getReadConnection();
		Connection writeCon = getConnection();


		//process edges in aoi groups
		List<UUID> aoiGroups = new ArrayList<>();

		StringBuilder sb = new StringBuilder();
		sb.append("SELECT distinct a.aoi_id ");
		sb.append(" FROM ");
		sb.append(properties.getEflowpathTable() + " a ");
		sb.append(" join " + properties.getEflowpathPropertiesTable() + " b on a.id = b.id ");
		sb.append(" join " + WORKING_LINK_TABLE + " c on b.graph_id = c.graph_id ");
		sb.append(" WHERE c.block_id = ? ");
		
		try (PreparedStatement ps = readCon.prepareStatement(sb.toString(), ResultSet.TYPE_FORWARD_ONLY,
				ResultSet.CONCUR_READ_ONLY)) {			
			ps.setInt(1, block.getBlockId());
			try(ResultSet rs = ps.executeQuery()){
				while(rs.next()) {
					aoiGroups.add((UUID)rs.getObject(1));
				}
			}
		}

		
		sb = new StringBuilder();
		sb.append("SELECT a.id, a.from_nexus_id, a.to_nexus_id, ");
		sb.append(" st_Asbinary(a." + properties.getGeometryColumn() + ") " );
		sb.append(" FROM ");
		sb.append(properties.getEflowpathTable() + " a ");
		sb.append(" join " + properties.getEflowpathPropertiesTable() + " b on a.id = b.id ");
		sb.append(" join " + WORKING_LINK_TABLE + " c on b.graph_id = c.graph_id ");
		sb.append(" WHERE c.block_id = ? and a.aoi_id = ? ");

		StringBuilder usb = new StringBuilder();
		usb.append("UPDATE ");
		usb.append(properties.getEflowpathTable());
		usb.append(" SET ");
		usb.append(properties.getGeometryColumn());
		usb.append(" = st_setsrid(st_geomfromewkb(?), " + properties.getSrid() + ") WHERE id = ?");

		WKBReader wkbReader = new WKBReader(new GeometryFactory());
		WKBWriter writer = new WKBWriter(4);

		int total = 0;

		try {
			for (UUID aoi : aoiGroups) {

				//autocommit must be disabled for every aoi group; the finally
				//block below restores it once all groups are processed
				writeCon.setAutoCommit(false);

				try (PreparedStatement ps = readCon.prepareStatement(sb.toString(), ResultSet.TYPE_FORWARD_ONLY,
						ResultSet.CONCUR_READ_ONLY);
						PreparedStatement update = writeCon.prepareStatement(usb.toString())) {

					//a fetch size forces the driver to use a server side cursor rather
					//than pulling the entire result set into memory
					ps.setFetchSize(batchSize);
					ps.setInt(1, block.getBlockId());
					ps.setObject(2,  aoi);

					int cnt = 0;

					try (ResultSet rs = ps.executeQuery()) {
						while (rs.next()) {
							UUID edgeId = (UUID) rs.getObject(1);
							UUID fromNexusId = (UUID) rs.getObject(2);
							UUID toNexusId = (UUID) rs.getObject(3);
							LineString ls = (LineString) wkbReader.read(rs.getBytes(4));

							EFlowpath edge = new EFlowpath(edgeId, ls, fromNexusId, toNexusId);
							processor.accept(edge);

							update.setObject(1, writer.write(edge.getLineString()));
							update.setObject(2, edge.getId());
							update.addBatch();

							cnt++;
							total++;
							if (cnt >= batchSize) {
								update.executeBatch();
								writeCon.commit();
								cnt = 0;
								logger.info(MessageFormat.format("Block {0}: {1} edges processed.", block.getBlockId(), total));
							}
						}
					}

					if (cnt > 0) update.executeBatch();
					writeCon.commit();
					logger.info(MessageFormat.format("Block {0}: {1} edges processed.", block.getBlockId(), total));

				} catch (Exception ex) {
					logger.error("Error updating flowpath geometries. ", ex);
					writeCon.rollback();
					throw ex;
				} finally {
					//release the read snapshot; the select is read only so there is
					//nothing to commit
					readCon.rollback();
				}
			}
		} finally {
			//the connection is shared with the block check out/finish statements
			//which rely on autocommit being enabled
			writeCon.setAutoCommit(true);
		}
	}

}
