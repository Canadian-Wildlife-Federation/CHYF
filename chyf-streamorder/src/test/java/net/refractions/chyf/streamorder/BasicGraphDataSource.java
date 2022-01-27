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

import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.impl.core.NodeEntity;

public class BasicGraphDataSource implements IGraphDataSource {

	private AoiGroup group = new AoiGroup();
	
	private Map<String, Map<String,Object>> edgeProperties;
	private Map<String, Map<String,Object>> nodeProperties;
	
	@Override
	public void connect() throws SQLException {
	}

	@Override
	public void close() throws SQLException {
	}

	@Override
	public List<AoiGroup> computeAoiGraphs() throws SQLException {
		return Collections.singletonList(group);
	}

	@Override
	public void loadGraph(Neo4JDatastore datasource, AoiGroup group) throws SQLException {
		
		try(Transaction tx = datasource.getDatabase().beginTx()){
			for (int i = 1; i < 19; i ++) {
				datasource.createNexus(tx, "N-" + i, 4);
			}
			
			datasource.createRelationship(tx, "N-1", "N-10", "E-4", 1, 1, 5, 1, null);
			datasource.createRelationship(tx, "N-2", "N-10", "E-5", 1, 1, 6, 1, null);
			datasource.createRelationship(tx, "N-10", "N-12", "E-6", 1, 1, 8, 1, null);
			
			datasource.createRelationship(tx, "N-3", "N-11", "E-1", 1, 1, 2, 1, null);
			datasource.createRelationship(tx, "N-4", "N-11", "E-2", 1, 1, 3, 1, "RED");
			datasource.createRelationship(tx, "N-11", "N-12", "E-3", 1, 1, 9, 1, "RED");
			
			datasource.createRelationship(tx, "N-12", "N-16", "E-7", 1, 1, 1, 1, "RED");
			datasource.createRelationship(tx, "N-5", "N-16", "E-8", 1, 1, 6, 1, null);
			datasource.createRelationship(tx, "N-16", "N-17", "E-9", 1, 1, 2, 1, null);
			
			datasource.createRelationship(tx, "N-6", "N-13", "E-10", 1, 1, 8, 1, null);
			datasource.createRelationship(tx, "N-7", "N-13", "E-11", 1, 1, 7, 1, "PURPLE");
			datasource.createRelationship(tx, "N-13", "N-15", "E-12", 1, 1, 5, 1, "PURPLE");
			
			datasource.createRelationship(tx, "N-8", "N-14", "E-13", 1, 1, 1, 1, null);
			datasource.createRelationship(tx, "N-9", "N-14", "E-14", 1, 1, 2, 1, null);
			datasource.createRelationship(tx, "N-14", "N-15", "E-15", 1, 1, 2, 1, null);
			
			datasource.createRelationship(tx, "N-15", "N-17", "E-16", 1, 1, 3, 1, "PURPLE");
			datasource.createRelationship(tx, "N-17", "N-18", "E-17", 1, 1, 3, 1, "PURPLE");
			
			tx.commit();
		}
	
		
	}

	@Override
	public void saveData(Neo4JDatastore graph) throws SQLException {
		nodeProperties = new HashMap<String, Map<String,Object>>();
		edgeProperties = new HashMap<String, Map<String,Object>>();
		
		
		try(Transaction tx = graph.getDatabase().beginTx(); 
				Result result = tx.execute("MATCH(a:Nexus) RETURN a ")){
			
			
			while (result.hasNext()) {
				Map<String, Object> row = result.next();
				
				NodeEntity ne = ((NodeEntity) row.get("a"));
				
				Long componentId = (Long) ne.getProperty(NexusProperty.COMPONENTID.key);
				
				HashMap<String, Object> nodeProp = new HashMap<>();
				nodeProp.put(NexusProperty.COMPONENTID.key, componentId);
				nodeProperties.put((String)ne.getProperty(NexusProperty.ID.key), nodeProp);
				
		
				// primary output gets orders
				for (Relationship edge : ne.getRelationships(Direction.OUTGOING, graph.getFlowpathType())) {
					
					HashMap<String, Object> flowpathProp = new HashMap<>();
					flowpathProp.put(NexusProperty.COMPONENTID.key, componentId);
					
					edgeProperties.put((String)edge.getProperty(FlowpathProperty.ID.key), flowpathProp);
					
					
					if ((Integer) edge.getProperty(FlowpathProperty.RANK.key) == 1 &&
							(Integer) edge.getProperty(FlowpathProperty.EF_TYPE.key) != 2) {

						//only primary non-bank
						
						Integer sorder = -9999;
						if (ne.hasProperty(NexusProperty.SORDER.key)) sorder =  (Integer) ne.getProperty(NexusProperty.SORDER.key);
						flowpathProp.put(NexusProperty.SORDER.key, sorder);
						
						String mid = null;
						if (ne.hasProperty(NexusProperty.MAINSTEMID.key)) {
							mid = ne.getProperty(NexusProperty.MAINSTEMID.key).toString();
						}
						flowpathProp.put(NexusProperty.MAINSTEMID.key, mid);
						
						Double uplength = -1.0;
						if (ne.hasProperty(NexusProperty.UPSTREAMLENGTH.key)) uplength = (Double) ne.getProperty(NexusProperty.UPSTREAMLENGTH.key);
						flowpathProp.put(NexusProperty.UPSTREAMLENGTH.key, uplength);
						
						Integer hack = -9999;
						if (ne.hasProperty(NexusProperty.HKORDER.key)) hack = (Integer) ne.getProperty(NexusProperty.HKORDER.key);
						flowpathProp.put(NexusProperty.HKORDER.key, hack);

						Integer horton = -9999;
						if (ne.hasProperty(NexusProperty.HTORDER.key)) horton = (Integer) ne.getProperty(NexusProperty.HTORDER.key);
						flowpathProp.put(NexusProperty.HTORDER.key, horton);
						
						Integer seq = -9999;
						if (ne.hasProperty(NexusProperty.MAINSTEMID_SEQ.key)) seq = (Integer) ne.getProperty(NexusProperty.MAINSTEMID_SEQ.key);
						flowpathProp.put(NexusProperty.MAINSTEMID_SEQ.key, seq);
						
						Integer shorder = -9999;
						if (ne.hasProperty(NexusProperty.SHORDER.key)) shorder = (Integer) ne.getProperty(NexusProperty.SHORDER.key);
						flowpathProp.put(NexusProperty.SHORDER.key, shorder);
						
						
					} 
				}
			}
		}
		
	}
	
	public Map<String, Map<String, Object>> getNodeProperties(){
		return this.nodeProperties;
	}
	public Map<String, Map<String, Object>> getEdgeProperties(){
		return this.edgeProperties;
	}

}

