/*
 * Copyright 2022 Canadian Wildlife Federation
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
package net.refractions.chyf.streamorder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.impl.core.NodeEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.RankType;

public class PostgresqlGraphDataSource implements IGraphDataSource{

	private Connection connection = null;

	private static final int COMMIT_CNT = 5000;

	private Logger logger = LoggerFactory.getLogger(PostgresqlGraphDataSource.class);

	//chyf2 input tables
	private static final String SCHEMA = "chyf2";
	private static final String EFLOWPATH_TABLE = SCHEMA + ".eflowpath";
	private static final String NEXUS_TABLE = SCHEMA + ".nexus";
	private static final String AOI_TABLE = SCHEMA + ".aoi";
	
	//db connection
	private String host = "";
	private int port = 5432;
	private String db ="";
	private String user ="";
	private String pass = "";
	
	//output table
	private String outputTable = "";
	
	/**
	 * 
	 * @param dbconnection host=<HOST>;port=<PORT>;db=<NAME>;user=<USERNAME>;password=<PASSWORD>
	 * @param outputTable
	 */
	public PostgresqlGraphDataSource(String dbconnection, String outputTable) {
		
		String[] bits = dbconnection.split(";");
		for (String bit : bits) {
			if (bit.startsWith("host=")) {
				this.host = bit.substring("host=".length());
			}else if (bit.startsWith("port=")) {
				this.port = Integer.valueOf (bit.substring("port=".length()));
			}else if (bit.startsWith("db=")) {
				this.db = bit.substring("db=".length());
			}else if (bit.startsWith("user=")) {
				this.user = bit.substring("user=".length());
			}else if (bit.startsWith("password=")) {
				this.pass = bit.substring("password=".length());
			}
		}
		
		this.outputTable = outputTable;
	}
	
	public synchronized void connect() throws SQLException {

		if (host == null || db == null || user == null || pass == null) {
			throw new SQLException("Host, database, user or password not supplied.");
		}
		
		connection = DriverManager.getConnection("jdbc:postgresql://" + host + ":" + port + "/" + db + "?user=" +user + "&password=" + pass);

		//prepare output tables
		try(Statement s = connection.createStatement()){
			StringBuilder sb = new StringBuilder();
			sb.append("DROP TABLE IF EXISTS " + outputTable);
			s.execute(sb.toString());
			
			sb = new StringBuilder();
			sb.append("CREATE TABLE IF NOT EXISTS " + outputTable + " (");
			sb.append("id uuid, strahler_order integer, graphid integer, ");
			sb.append("mainstemid uuid, max_uplength double precision, ");
			sb.append("hack_order integer, horton_order integer, mainstem_seq integer, ");
			sb.append("shreve_order integer, ");
			sb.append("primary key (id))");
			s.execute(sb.toString());
		}
	}

	public void close() throws SQLException {
		connection.close();
	}
	
	public List<AoiGroup> computeAoiGraphs() throws SQLException {

		// find links between aois
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT DISTINCT ");
		sb.append(" a.aoi_id, b.aoi_id ");
		sb.append(" FROM ");
		sb.append(EFLOWPATH_TABLE + " a ");
		sb.append(" JOIN ");
		sb.append(EFLOWPATH_TABLE + " b ");
		sb.append(" ON a.from_nexus_id = b.to_nexus_id ");
		sb.append(" WHERE ");
		sb.append(" a.aoi_id != b.aoi_id AND a.rank = 1 AND b.rank = 1 ");
		
		HashMap<String, Set<String>> aoiLinks = new HashMap<>();

		try (Statement s = connection.createStatement(); ResultSet rs = s.executeQuery(sb.toString())) {

			while (rs.next()) {

				String id1 = rs.getString(1);
				String id2 = rs.getString(2);

				Set<String> set = aoiLinks.get(id1);
				if (set == null) {
					set = new HashSet<>();
					aoiLinks.put(id1, set);
				}
				set.add(id2);

				set = aoiLinks.get(id2);
				if (set == null) {
					set = new HashSet<>();
					aoiLinks.put(id2, set);
				}
				set.add(id1);
			}
		}

		Set<String> visited = new HashSet<>();

		List<AoiGroup> groups = new ArrayList<>();
		for (String s : aoiLinks.keySet()) {

			if (visited.contains(s))
				continue;

			ArrayDeque<String> tovisit = new ArrayDeque<>();

			AoiGroup aoiGroup = new AoiGroup(UUID.fromString(s));
			groups.add(aoiGroup);
			tovisit.addAll(aoiLinks.get(s));
			visited.add(s);

			while (!tovisit.isEmpty()) {
				String n = tovisit.remove();
				if (visited.contains(n))
					continue;
				aoiGroup.addAoi(UUID.fromString(n));
				visited.add(n);
				tovisit.addAll(aoiLinks.get(n));
			}
		}

		// add any aoi's that are not in a group

		try (Statement s = connection.createStatement();
				ResultSet rs = s.executeQuery("SELECT cast(id as varchar) FROM " + AOI_TABLE)) {

			while (rs.next()) {
				String id = rs.getString(1);
				if (!visited.contains(id)) {
					AoiGroup aoiGroup = new AoiGroup(UUID.fromString(id));
					groups.add(aoiGroup);
					visited.add(id);
				}
			}
		}
		return groups;
	}

	public void loadGraph(Neo4JDatastore datasource, AoiGroup group) throws SQLException {

		logger.info("Creating nodes");
		Transaction ntx = datasource.getDatabase().beginTx();
		try {

			StringBuilder sb = new StringBuilder();

			sb.append(" WITH edges AS (");
			sb.append(" SELECT from_nexus_id, to_nexus_id FROM ");
			sb.append( EFLOWPATH_TABLE );
			sb.append(" WHERE aoi_id IN (");
			for (int i = 0; i < group.getAoiIds().size(); i++) {
				sb.append("?,");
			}
			sb.deleteCharAt(sb.length() - 1);
			sb.append(")");
			sb.append(") ");
			sb.append(" SELECT id, nexus_type FROM ");
			sb.append( NEXUS_TABLE );
			sb.append(" WHERE id IN (");
			sb.append(" SELECT from_nexus_id FROM edges UNION SELECT to_nexus_id FROM edges");
			sb.append(")");

			int cnt = 0;
			try (PreparedStatement ps = connection.prepareStatement(sb.toString())) {
				int i = 1;
				for (UUID uuid : group.getAoiIds()) {
					ps.setObject(i++, uuid);
				}
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						String id = rs.getString(1);
						int type = rs.getInt(2);

						datasource.createNexus(ntx, id, type);
						
						cnt++;
						if (cnt > COMMIT_CNT) {
							cnt = 0;
							ntx.commit();
							ntx = datasource.getDatabase().beginTx();
						}
					}
				}
			}
			ntx.commit();
		} finally {
			ntx.close();
		}
		logger.info("Nodes created");

		logger.info("Creating node indexes");
		try (Transaction tx = datasource.getDatabase().beginTx()) {
			tx.schema().indexFor(datasource.getNexusType()).on(NexusProperty.ID.key).create();
			tx.commit();
		}
		logger.info("Node indexes created");

		logger.info("Creating relationships");
		Transaction rtx = datasource.getDatabase().beginTx();
		try {

			StringBuilder sb = new StringBuilder();
			sb.append("SELECT from_nexus_id, to_nexus_id, id, ef_type, ef_subtype, length, rank, name_id ");
			sb.append(" FROM ");
			sb.append(EFLOWPATH_TABLE);
			sb.append(" WHERE aoi_id IN (");
			for (int i = 0; i < group.getAoiIds().size(); i++) {
				sb.append("?,");
			}
			sb.deleteCharAt(sb.length() - 1);
			sb.append(")");

			int cnt = 0;
//			System.out.println(sb.toString());
			try (PreparedStatement ps = connection.prepareStatement(sb.toString())) {
				int i = 1;
				for (UUID uuid : group.getAoiIds()) {
					ps.setObject(i++, uuid);
				}
				try (ResultSet rs = ps.executeQuery()) {
					while (rs.next()) {
						String from = rs.getString(1);
						String to = rs.getString(2);
						String id = rs.getString(3);
						int type = rs.getInt(4);
						int subtype = rs.getInt(5);
						double length = rs.getDouble(6);
						int rank = rs.getInt(7);
						String nameid = rs.getString(8);

						datasource.createRelationship(rtx,  from, to, id, type, subtype, length, rank, nameid);
						
						cnt++;
						if (cnt > COMMIT_CNT) {
							cnt = 0;
							rtx.commit();
							rtx = datasource.getDatabase().beginTx();
						}
					}
				}

			}
			rtx.commit();

		} finally {
			rtx.close();
		}

		logger.info("Relationships created");

		logger.info("Creating relationship indexes");
		try (Transaction tx = datasource.getDatabase().beginTx()) {
			tx.schema().indexFor(datasource.getFlowpathType()).on(FlowpathProperty.EF_TYPE.key).create();
			tx.schema().indexFor(datasource.getFlowpathType()).on(FlowpathProperty.RANK.key).create();
			tx.commit();
		}
		logger.info("Relationship indexes created");
	}
	
	public void saveData(Neo4JDatastore graph) throws SQLException{

		
		connection.setAutoCommit(false);

		StringBuilder sb = new StringBuilder();
		sb.append("SELECT max(graphid) FROM ");
		sb.append(outputTable);
		
		long startgraphid = 0;
		try(Statement ss = connection.createStatement();
				ResultSet rs = ss.executeQuery(sb.toString())){
			rs.next();
			startgraphid = rs.getLong(1);
		}
		
		sb = new StringBuilder();
		sb.append("INSERT INTO " + outputTable );
		sb.append("(id, strahler_order, graphid, mainstemid, max_uplength, hack_order, ");
		sb.append(" horton_order, mainstem_seq, shreve_order )");
		sb.append(" VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
		
		try(PreparedStatement ps = connection.prepareStatement(sb.toString());
				Transaction tx = graph.getDatabase().beginTx(); 
				Result result = tx.execute("MATCH(a:Nexus) RETURN a ")){
			
			int cnt = 0;
			
			while (result.hasNext()) {
				Map<String, Object> row = result.next();
				
				NodeEntity ne = ((NodeEntity) row.get("a"));
				
				Long componentId = (Long) ne.getProperty(NexusProperty.COMPONENTID.key) + startgraphid;
		
				// primary output gets orders
				for (Relationship edge : ne.getRelationships(Direction.OUTGOING, graph.getFlowpathType())) {
					
					String eid = (String) edge.getProperty(FlowpathProperty.ID.key);
					ps.setObject(1, UUID.fromString(eid));
					ps.setInt(3, componentId.intValue());
					
					if ((Integer) edge.getProperty(FlowpathProperty.RANK.key) == RankType.PRIMARY.getChyfValue() &&
							(Integer) edge.getProperty(FlowpathProperty.EF_TYPE.key) != EfType.BANK.getChyfValue()) {

						//only primary non-bank
						
						Integer sorder = -9999;
						if (ne.hasProperty(NexusProperty.SORDER.key)) sorder =  (Integer) ne.getProperty(NexusProperty.SORDER.key);
						ps.setInt(2, sorder);
						
						String mid = null;
						if (ne.hasProperty(NexusProperty.MAINSTEMID.key)) {
							mid = ne.getProperty(NexusProperty.MAINSTEMID.key).toString();
						}
						if (mid == null) {
							ps.setNull(4, Types.OTHER);
						}else {
							ps.setObject(4, UUID.fromString(mid));
						}
						
						Double uplength = -1.0;
						if (ne.hasProperty(NexusProperty.UPSTREAMLENGTH.key)) uplength = (Double) ne.getProperty(NexusProperty.UPSTREAMLENGTH.key);
						ps.setDouble(5, uplength);
						
						Integer hack = -9999;
						if (ne.hasProperty(NexusProperty.HKORDER.key)) hack = (Integer) ne.getProperty(NexusProperty.HKORDER.key);
						ps.setInt(6, hack);

						Integer horton = -9999;
						if (ne.hasProperty(NexusProperty.HTORDER.key)) horton = (Integer) ne.getProperty(NexusProperty.HTORDER.key);
						ps.setInt(7, horton);
						
						Integer seq = -9999;
						if (ne.hasProperty(NexusProperty.MAINSTEMID_SEQ.key)) seq= (Integer) ne.getProperty(NexusProperty.MAINSTEMID_SEQ.key);
						ps.setInt(8, seq);
						
						Integer shorder = -9999;
						if (ne.hasProperty(NexusProperty.SHORDER.key)) shorder =  (Integer) ne.getProperty(NexusProperty.SHORDER.key);
						ps.setInt(9, shorder);
						
						
					} else {
						ps.setNull(2, Types.INTEGER);
						ps.setNull(4, Types.OTHER);
						ps.setNull(5, Types.DOUBLE);
						ps.setNull(6, Types.INTEGER);
						ps.setNull(7, Types.INTEGER);
						ps.setNull(8, Types.INTEGER);
						ps.setNull(9, Types.INTEGER);
					}
					
					ps.addBatch();
					cnt++;

					if (cnt > 1000) {
						ps.executeBatch();
						cnt = 0;
						connection.commit();
					}
				}

			}
			ps.executeBatch();
			connection.commit();
		}
		
	}
}
