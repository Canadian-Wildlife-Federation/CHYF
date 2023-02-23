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
package net.refractions.chyf.cyclechecker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PostgresqlCycleDataSource implements AutoCloseable {

	private Connection connection = null;


	private Logger logger = LoggerFactory.getLogger(PostgresqlCycleDataSource.class);

	//chyf2 input tables
	private String eflowpathTable = "";
	
	//db connection
	private String host = "";
	private int port = 5432;
	private String db ="";
	private String user ="";
	private String pass = "";
	
	/**
	 * 
	 * @param dbconnection host=<HOST>;port=<PORT>;db=<NAME>;user=<USERNAME>;password=<PASSWORD>
	 * @param outputTable
	 */
	public PostgresqlCycleDataSource(String dbconnection, String inputSchema) {
		
		this.eflowpathTable = inputSchema + ".eflowpath";
		
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
	}
	
	public Collection<Node> buildGraph() throws SQLException{
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT distinct from_nexus_id, to_nexus_id ");
		sb.append(" FROM ");
		sb.append(this.eflowpathTable);
			
		Map<String, Node> nodes = new HashMap<>();
		
		try(Statement s = this.connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)){
			try(ResultSet rs = s.executeQuery(sb.toString())){
				long counter = 0;
				while(rs.next()) {
					counter ++;
					if (counter % 1000 == 0) {
						logger.info("loaded: " + counter);
					}
					String fromId = rs.getString(1);
					String toId = rs.getString(2);
					
					Node fromNode = nodes.get(fromId);
					if (fromNode == null) {
						fromNode = new Node(fromId);
						nodes.put(fromId,  fromNode);
					}
					Node toNode = nodes.get(toId);
					if (toNode == null) {
						toNode = new Node(toId);
						nodes.put(toId,  toNode);
					}
					fromNode.addOutNode(toNode);
					toNode.addInNode(fromNode);
				}
			}
		}
		
		return nodes.values();
	}
	
	
	public synchronized void connect() throws SQLException {
		if (host == null || db == null || user == null || pass == null) {
			throw new SQLException("Host, database, user or password not supplied.");
		}
		connection = DriverManager.getConnection("jdbc:postgresql://" + host + ":" + port + "/" + db + "?user=" +user + "&password=" + pass);
	}

	public void close() throws SQLException {
		connection.close();
	}
	
}
