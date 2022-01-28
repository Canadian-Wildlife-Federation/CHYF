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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.neo4j.gds.api.ImmutableGraphLoaderContext;
import org.neo4j.gds.config.GraphCreateFromCypherConfig;
import org.neo4j.gds.config.GraphCreateFromStoreConfig;
import org.neo4j.gds.config.ImmutableGraphCreateFromCypherConfig;
import org.neo4j.gds.core.GraphLoader;
import org.neo4j.gds.core.ImmutableGraphLoader;
import org.neo4j.gds.core.loading.GraphStoreCatalog;
import org.neo4j.gds.core.utils.progress.EmptyTaskRegistryFactory;
import org.neo4j.gds.impl.walking.WalkPath;
import org.neo4j.gds.transaction.TransactionContext;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.impl.core.NodeEntity;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.NullLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.streamorder.IGraphDataSource.FlowpathProperty;
import net.refractions.chyf.streamorder.IGraphDataSource.NexusProperty;

public class StreamOrderMainstemEngine {

	private Logger logger = LoggerFactory.getLogger(StreamOrderMainstemEngine.class);

	private boolean useNamesForMainstems = false;
	
	/**
	 * 
	 * @param useNamesForMainstems if names should be used for mainstems
	 */
	public StreamOrderMainstemEngine(boolean useNamesForMainstems) {
		this.useNamesForMainstems = useNamesForMainstems;
	}
	
	public void computeOrderValues(IGraphDataSource source) throws Exception {

		logger.info("Computing aoi groups");
		List<AoiGroup> groups = source.computeAoiGraphs();

		logger.info("Processing aoi groups");
		for (int i = 0; i < groups.size(); i++) {
			logger.info("Processing aoi group " + i + "/" + groups.size());
			processAoiGroup(source, groups.get(i));
		}

	}

	private void processAoiGroup(IGraphDataSource source, AoiGroup group) throws Exception {

		Neo4JDatastore graph = new Neo4JDatastore();
		try {
			graph.init();

			source.loadGraph(graph, group);
			computeOrder(graph);
			source.saveData(graph);

		} finally {
			graph.shutdown();
		}

	}

	private void computeOrder(Neo4JDatastore graph) {

		logger.info("Computing order");
		GraphDatabaseAPI api = ((GraphDatabaseAPI) graph.getDatabase());

		GraphCreateFromCypherConfig conf = ImmutableGraphCreateFromCypherConfig.builder().graphName("primaryGraph")
				.nodeQuery("MATCH (n:Nexus) RETURN id(n) as id")
				.relationshipQuery(
						"MATCH (s:Nexus)-[flow:FLOWPATH]->(t:Nexus) RETURN id(s) AS source, id(t) AS target")
				.putParameter("nodeProperties", new ArrayList<>())
				.putParameter("relationshipProperties", new ArrayList<>()).build();

		logger.info("Computing order - creating component networks");

		try (Transaction tx = graph.getDatabase().beginTx()) {
			GraphLoader gl = ImmutableGraphLoader.builder().context(ImmutableGraphLoaderContext.builder()
					.transactionContext(TransactionContext.of(api, tx)).api(api).log(NullLog.getInstance())
//                    .allocationTracker(allocationTracker)
					.taskRegistryFactory(EmptyTaskRegistryFactory.INSTANCE)
//                    .terminationFlag(TerminationFlag.wrap(transaction))
					.build()).createConfig(conf).build();

			GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("", "primaryGraph"), gl.graphStore());

			StringBuilder sb = new StringBuilder();
			sb.append("CALL gds.wcc.write('primaryGraph', { writeProperty: '" + NexusProperty.COMPONENTID.key + "' }) ");
			sb.append(" YIELD nodePropertiesWritten, componentCount;");

			tx.execute(sb.toString());
			tx.commit();
		}
	
		try (Transaction tx = graph.getDatabase().beginTx()) {
			tx.schema().indexFor(graph.getNexusType()).on(NexusProperty.COMPONENTID.key).create();
			tx.commit();
		}

		logger.info("Computing order - creating component networks complete");

		logger.info("Computing order - processing graphs with < 2 nodes");
		
		// set to 1 all subgraphs with 2 or fewer nodes
		process2SizeNetworks(graph);

		logger.info("Computing order - determining remaining subgraphs");
		Set<ImmutablePair<Long, Long>> subgraphs = new HashSet<>();
		try (Transaction tx = graph.getDatabase().beginTx()) {

			StringBuilder sb = new StringBuilder();
			sb.append("MATCH(a:Nexus) ");
			sb.append(" WITH a." + NexusProperty.COMPONENTID.key );
			sb.append(" as pid, count(*) as cnt ");
			sb.append(" WHERE cnt >= 3 ");
			sb.append(" RETURN pid, cnt ");

			try (Result result = tx.execute(sb.toString())) {
				while (result.hasNext()) {
					Map<String, Object> row = result.next();
					Long order = (Long) row.get("pid");
					Long cnt = (Long) row.get("cnt");
					subgraphs.add(new ImmutablePair<>(order, cnt));
				}
			}
		}

		Set<Long> tocompute = new HashSet<>();
		long nodecnt = 0;

		int i = 0;
		for (ImmutablePair<Long, Long> l : subgraphs) {

			tocompute.add(l.getLeft());
			nodecnt = nodecnt + l.getRight();
			i++;

			if (nodecnt > 1_000_000 || tocompute.size() > 500) {
				logger.info("Computing order - processing subgraph " + i + "/" + subgraphs.size());
				processSubGraphOrder(graph, tocompute);
				tocompute.clear();
				nodecnt = 0;
			}
		}
		if (!tocompute.isEmpty()) {
			logger.info("Computing order - processing subgraph " + i + "/" + subgraphs.size());
			processSubGraphOrder(graph, tocompute);
		}
	}

	private void process2SizeNetworks(Neo4JDatastore graph) {
		
		Set<Long> componentIds = new HashSet<>();
		
		try (Transaction tx = graph.getDatabase().beginTx()) {
			StringBuilder sb = new StringBuilder();
			sb.append(" MATCH (a:Nexus) ");
			sb.append(" WITH a." + NexusProperty.COMPONENTID.key );
			sb.append(" as pid, count(*) as cnt ");
			sb.append(" WHERE cnt = 2 ");
			sb.append(" RETURN pid");
			
			try (Result result = tx.execute(sb.toString())) {
				while (result.hasNext()) {
					Map<String, Object> row = result.next();
					Long pid = (Long) row.get("pid");
					componentIds.add(pid);
				}
			}
		}
		
		int cc = 0;

		Transaction tx2 = graph.getDatabase().beginTx();
		try {
			for (Long componentId : componentIds ) {
				
				if (cc == 500) {
					tx2.commit();
					tx2 = graph.getDatabase().beginTx();
					cc = 0;
				}
				
				StringBuilder sb = new StringBuilder();
				sb.append(" MATCH (a:Nexus) ");
				sb.append(" WHERE a." + NexusProperty.COMPONENTID.key );
				sb.append(" = " + componentId );
				sb.append(" RETURN a ");
				
				String mainstem = UUID.randomUUID().toString();
				try (Result result = tx2.execute(sb.toString())) {
					while (result.hasNext()) {
						Map<String, Object> row = result.next();
						NodeEntity node = (NodeEntity) row.get("a");
						
						node.setProperty(NexusProperty.SORDER.key, 1);
						node.setProperty(NexusProperty.SHORDER.key, 1);
						node.setProperty(NexusProperty.HTORDER.key, 1);
						node.setProperty(NexusProperty.HKORDER.key, 1);
						node.setProperty(NexusProperty.MAINSTEMID.key, mainstem);
						node.setProperty(NexusProperty.MAINSTEMID_SEQ.key, 1);
						node.setProperty(NexusProperty.UPSTREAMLENGTH.key, 0.0);
					}
				}
			}
		}finally {
			tx2.commit();
		}
	}

	
	private void processSubGraphOrder(Neo4JDatastore graph, Set<Long> componentIds) {

		GraphDatabaseAPI api = ((GraphDatabaseAPI) graph.getDatabase());

		GraphCreateFromCypherConfig conf;

		StringBuilder in = new StringBuilder();
		for (Long pi : componentIds) {
			in.append(String.valueOf(pi));
			in.append(",");
		}
		in.deleteCharAt(in.length() - 1);

		StringBuilder nodeQuery = new StringBuilder();
		nodeQuery.append("MATCH (n:Nexus) ");
		nodeQuery.append("WHERE n." + NexusProperty.COMPONENTID.key);
		nodeQuery.append(" IN [" + in.toString() + "] ");
		nodeQuery.append("RETURN id(n) as id ");

		StringBuilder edgeQuery = new StringBuilder();
		edgeQuery.append("MATCH (s:Nexus)-[flow:FLOWPATH]->(t:Nexus) ");
		edgeQuery.append("WHERE s." + NexusProperty.COMPONENTID.key);
		edgeQuery.append(" IN [" + in.toString() + "] ");
		edgeQuery.append(" AND t." + NexusProperty.COMPONENTID.key);
		edgeQuery.append(" IN [" + in.toString() + "] ");
		edgeQuery.append(" AND flow." + FlowpathProperty.RANK.key);
		edgeQuery.append(" = " + RankType.PRIMARY.getChyfValue() );
		edgeQuery.append(" AND flow." + FlowpathProperty.EF_TYPE.key);
		edgeQuery.append(" <> " + EfType.BANK.getChyfValue() );
		edgeQuery.append(" RETURN id(t) AS source, id(s) as target ");
		
		conf = ImmutableGraphCreateFromCypherConfig.builder().graphName("tempGraph")
				.nodeQuery(nodeQuery.toString())
				.relationshipQuery(edgeQuery.toString())
				.build();

		//store the nodes to visit in array list
		List<Long> toProcess = new ArrayList<>();
		
		try (Transaction tx = graph.getDatabase().beginTx()) {

			GraphLoader gl = ImmutableGraphLoader.builder().context(ImmutableGraphLoaderContext.builder()
					.transactionContext(TransactionContext.of(api, tx)).api(api).log(NullLog.getInstance())
//	                    .allocationTracker(allocationTracker)
					.taskRegistryFactory(EmptyTaskRegistryFactory.INSTANCE)
//	                    .terminationFlag(TerminationFlag.wrap(transaction))
					.build()).createConfig(conf).build();
//				
			GraphStoreCatalog.set(GraphCreateFromStoreConfig.emptyWithName("", "tempGraph"), gl.graphStore());

			StringBuilder sb = new StringBuilder();
			sb.append("MATCH(a:Nexus) WHERE ");
			sb.append("a." + NexusProperty.COMPONENTID.key);
			sb.append(" IN [" + in.toString() + "] ");
			sb.append(" and isEmpty([(a)-[fp:FLOWPATH]->() WHERE ");
			sb.append("fp." + FlowpathProperty.RANK.key );
			sb.append(" = " + RankType.PRIMARY.getChyfValue() + " and ");
			sb.append("fp." + FlowpathProperty.EF_TYPE.key);
			sb.append(" <> " + EfType.BANK.getChyfValue() + " | a]) ");
			sb.append("WITH id(a) AS startNode ");
			sb.append("CALL gds.alpha.bfs.stream('tempGraph', {startNode: startNode}) ");
			sb.append("YIELD path ");
			sb.append("RETURN path ");
			
			try (Result result = tx.execute(sb.toString())) {
				while (result.hasNext()) {
					Map<String, Object> row = result.next();
					WalkPath path = (WalkPath) row.get("path");

					path.reverseNodes().forEach(n->toProcess.add(n.getId()));
				}
			}
		}

		int commitcnt = 0;
		Transaction tx = graph.getDatabase().beginTx();
		try {
			//visit nodes in order committing every x number of visits
			for (Long nid : toProcess) {
				if (commitcnt == 500) {
					tx.commit();
					tx = graph.getDatabase().beginTx();
					commitcnt = 0;
				}
				commitcnt++;
	
				int cnt = 0;
				int order = -1;
				Node n = tx.getNodeById(nid);

				String upNameId = null;
				
				for (Relationship r : n.getRelationships(Direction.OUTGOING)) {
					if ((Integer) r.getProperty(FlowpathProperty.RANK.key) == RankType.PRIMARY.getChyfValue()) {
						if (r.hasProperty(FlowpathProperty.NAMEID.key))
							upNameId = r.getProperty(FlowpathProperty.NAMEID.key).toString();
						break;
					}
				}
	
				Node longestupstreamNode = null;
				double longestupstream = -1;
				
				Node sameNameUpstreamNode = null;
	
				Node longestNamedUpstreamNode = null;
				double longestNamedUpstream = -1;

				int shorder = 0;
				
				for (Relationship r : n.getRelationships(Direction.INCOMING)) {
					if (((Integer) r.getProperty(FlowpathProperty.EF_TYPE.key)) == EfType.BANK.getChyfValue() ||
							((Integer) r.getProperty(FlowpathProperty.RANK.key)) != RankType.PRIMARY.getChyfValue())
						continue;
	
					
					Node fromnode = r.getStartNode();
	
					double length = (double) r.getProperty(FlowpathProperty.LENGTH.key);
	
					double uplength = 0;
	
					String nameid = null;
					if (r.hasProperty(FlowpathProperty.NAMEID.key)) {
						nameid = (String)r.getProperty(FlowpathProperty.NAMEID.key);
					}
					
					if (fromnode.hasProperty(NexusProperty.UPSTREAMLENGTH.key))
						uplength = (double) fromnode.getProperty(NexusProperty.UPSTREAMLENGTH.key);
	
					if ((uplength + length) > longestupstream) {
						longestupstream = uplength + length;
						longestupstreamNode = fromnode;
					}
	
					if (nameid != null && ((uplength + length) > longestNamedUpstream)) {
						longestNamedUpstream = uplength + length;
						longestNamedUpstreamNode = fromnode;
					}
					
					if (upNameId != null && nameid != null && nameid.equals(upNameId)) {
						sameNameUpstreamNode = fromnode;
					}

					Integer nshorder = (Integer) fromnode.getProperty(NexusProperty.SHORDER.key);
					shorder += nshorder;
					
					Integer norder = (Integer) fromnode.getProperty(NexusProperty.SORDER.key);
					if (norder > order) {
						order = norder;
						cnt = 1;
					} else if (norder == order) {
						cnt++;
					}
				}
	
				if (cnt > 1) {
					n.setProperty(NexusProperty.SORDER.key, order + 1);
				} else {
					if (order == -1) order = 1;
					n.setProperty(NexusProperty.SORDER.key, order);
				}
				
				if (shorder == 0) shorder = 1;
				n.setProperty(NexusProperty.SHORDER.key, shorder);
				
				if (useNamesForMainstems) {
					//use names for mainstems
					if (sameNameUpstreamNode != null) {
						n.setProperty(NexusProperty.MAINSTEMID.key, sameNameUpstreamNode.getProperty(NexusProperty.MAINSTEMID.key));
					} else if (longestupstreamNode == null) {
						n.setProperty(NexusProperty.MAINSTEMID.key, UUID.randomUUID().toString());
					}else if (longestNamedUpstreamNode != null) {
						n.setProperty(NexusProperty.MAINSTEMID.key, longestNamedUpstreamNode.getProperty(NexusProperty.MAINSTEMID.key));
					} else {
						n.setProperty(NexusProperty.MAINSTEMID.key, longestupstreamNode.getProperty(NexusProperty.MAINSTEMID.key));
					}	
				}else {
					//only use upstream lenght for mainstems
					if (longestupstreamNode == null) {
						n.setProperty(NexusProperty.MAINSTEMID.key, UUID.randomUUID().toString());
					}else {
						n.setProperty(NexusProperty.MAINSTEMID.key, longestupstreamNode.getProperty(NexusProperty.MAINSTEMID.key));
					}
				}
				
				if (longestupstreamNode == null) {
					n.setProperty(NexusProperty.UPSTREAMLENGTH.key, 0.0);
				} else {
					n.setProperty(NexusProperty.UPSTREAMLENGTH.key, longestupstream);
				}
		
			}
			tx.commit();
		}finally {
			tx.close();
		}
		
		
		
		//walk up computing mainsteam sequestion, horton and hack order
		 commitcnt = 0;
		tx = graph.getDatabase().beginTx();
		try {
			//visit nodes in order committing every x number of visits
			for (int i = toProcess.size() - 1; i >= 0; i--) {
				Long nid = toProcess.get(i);
			
				if (commitcnt == 500) {
					tx.commit();
					tx = graph.getDatabase().beginTx();
					commitcnt = 0;
				}
				
				commitcnt++;
		
				Node n = tx.getNodeById(nid);
				if (!n.hasProperty(NexusProperty.MAINSTEMID_SEQ.key)) {
					n.setProperty(NexusProperty.MAINSTEMID_SEQ.key, 0);
				}
				if (!n.hasProperty(NexusProperty.HKORDER.key)) {
					n.setProperty(NexusProperty.HKORDER.key, 1);
				}
				if (!n.hasProperty(NexusProperty.HTORDER.key)) {
					n.setProperty(NexusProperty.HTORDER.key, n.getProperty(NexusProperty.SORDER.key));
				}
				String mainstemId = n.getProperty(NexusProperty.MAINSTEMID.key).toString();
				
				for (Relationship r : n.getRelationships(Direction.INCOMING)) {
					if (((Integer) r.getProperty(FlowpathProperty.EF_TYPE.key)) == EfType.BANK.getChyfValue() ||
							((Integer) r.getProperty(FlowpathProperty.RANK.key)) != RankType.PRIMARY.getChyfValue())
						continue;
					
					Node upNode = r.getStartNode();
					String upmid = upNode.getProperty(NexusProperty.MAINSTEMID.key).toString();
					if (upmid.equals(mainstemId)) {
						upNode.setProperty(NexusProperty.HTORDER.key, n.getProperty(NexusProperty.HTORDER.key));
						upNode.setProperty(NexusProperty.MAINSTEMID_SEQ.key, 
								((Integer)n.getProperty(NexusProperty.MAINSTEMID_SEQ.key)) + 1);
						upNode.setProperty(NexusProperty.HKORDER.key, n.getProperty(NexusProperty.HKORDER.key));
					}else {
						upNode.setProperty(NexusProperty.HTORDER.key, upNode.getProperty(NexusProperty.SORDER.key));
						upNode.setProperty(NexusProperty.MAINSTEMID_SEQ.key, 1);
						upNode.setProperty(NexusProperty.HKORDER.key, 
								((Integer)n.getProperty(NexusProperty.HKORDER.key)) + 1);
					}
				}
				
				}
				tx.commit();
			}finally {
				tx.close();
			}
			
		GraphStoreCatalog.removeAllLoadedGraphs();

	}
}
