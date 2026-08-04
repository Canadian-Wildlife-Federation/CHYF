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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Result;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.impl.core.NodeEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.streamorder.IGraphDataSource.FlowpathProperty;
import net.refractions.chyf.streamorder.IGraphDataSource.NexusProperty;

public class StreamOrderMainstemEngine {

	private Logger logger = LoggerFactory.getLogger(StreamOrderMainstemEngine.class);

	private StreamOrderArgs.MAINSTEM_NAME_OP nameOption;
	private String pageCacheSize;

	/**
	 *
	 * @param useNamesForMainstems if names should be used for mainstems
	 */
	public StreamOrderMainstemEngine(StreamOrderArgs.MAINSTEM_NAME_OP nameOption) {
		this(nameOption, Neo4JDatastore.DEFAULT_PAGE_CACHE_SIZE);
	}

	/**
	 *
	 * @param nameOption if names should be used for mainstems
	 * @param pageCacheSize Neo4j page cache size, e.g. "1g"
	 */
	public StreamOrderMainstemEngine(StreamOrderArgs.MAINSTEM_NAME_OP nameOption, String pageCacheSize) {
		this.nameOption = nameOption;
		this.pageCacheSize = pageCacheSize;
	}
	
	public void computeOrderValues(IGraphDataSource source) throws Exception {

		logger.info("Computing aoi groups");
		List<AoiGroup> groups = source.computeAoiGraphs();

		logger.info("Processing aoi groups");
		for (int i = 0; i < groups.size(); i++) {
			logger.info("Processing aoi group " + i + "/" + groups.size());
			processAoiGroup(source, groups.get(i));

			System.gc();
			logMemoryUsage();
		}

	}

	private void logMemoryUsage() {
		Runtime rt = Runtime.getRuntime();
		long heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);

		String rss = null;
		try {
			for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
				if (line.startsWith("VmRSS:")) {
					rss = line.trim();
					break;
				}
			}
		} catch (IOException ex) {
			// /proc/self/status not available (e.g. not running on Linux) - skip
		}

		logger.info("Memory usage - heap used: " + heapUsedMb + "MB" + (rss == null ? "" : ", " + rss));
	}

	private void processAoiGroup(IGraphDataSource source, AoiGroup group) throws Exception {

		Neo4JDatastore graph = new Neo4JDatastore();
		try {
			graph.init(pageCacheSize);

			source.loadGraph(graph, group);
			computeOrder(graph);
			source.saveData(graph);
		}catch (Exception ex) {
			logger.error(ex.getMessage(), ex);
			throw ex;
		} finally {
			graph.shutdown();
		}

	}

	private void computeOrder(Neo4JDatastore graph) {

		logger.info("Computing order");

		logger.info("Computing order - creating component networks");

		computeComponentIds(graph);

		try (Transaction tx = graph.getDatabase().beginTx()) {
			tx.schema()
				.indexFor(graph.getNexusType())
				.on(NexusProperty.COMPONENTID.key).create();
			tx.commit();
		}
		try (Transaction tx = graph.getDatabase().beginTx()) {
			tx.schema().awaitIndexesOnline(8 * 60, TimeUnit.MINUTES);
			tx.commit();
		}

		logger.info("Computing order - creating component networks complete");

		logger.info("Computing order - processing graphs with < 2 nodes");
		
		// set to 1 all subgraphs with 2 or fewer nodes
		processSize2Networks(graph);

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

	/**
	 * Computes weakly connected components with an in-memory union-find over node ids,
	 * writing the result to componentId. Avoids projecting the whole AOI graph into GDS
	 * native memory (gds.graph.create + gds.wcc.write), which was OOM-killing the process
	 * on large datasets.
	 */
	private void computeComponentIds(Neo4JDatastore graph) {

		int maxId = -1;
		try (Transaction tx = graph.getDatabase().beginTx()) {
			try (Result result = tx.execute("MATCH (n:Nexus) RETURN max(id(n)) AS maxid")) {
				Object v = result.next().get("maxid");
				if (v != null) maxId = ((Long) v).intValue();
			}
		}
		if (maxId < 0) return;

		int[] parent = new int[maxId + 1];
		for (int i = 0; i <= maxId; i++) parent[i] = i;

		try (Transaction tx = graph.getDatabase().beginTx()) {
			try (Result result = tx.execute(
					"MATCH (a:Nexus)-[:FLOWPATH]->(b:Nexus) RETURN id(a) AS aid, id(b) AS bid")) {
				while (result.hasNext()) {
					Map<String, Object> row = result.next();
					union(parent, ((Long) row.get("aid")).intValue(), ((Long) row.get("bid")).intValue());
				}
			}
		}

		List<Map<String, Object>> batch = new ArrayList<>();
		Transaction tx = graph.getDatabase().beginTx();
		try {
			for (int i = 0; i <= maxId; i++) {
				Map<String, Object> row = new HashMap<>();
				row.put("id", (long) i);
				row.put("cid", (long) find(parent, i));
				batch.add(row);

				if (batch.size() == 5000) {
					writeComponentIdBatch(tx, batch);
					tx.commit();
					tx = graph.getDatabase().beginTx();
					batch.clear();
				}
			}
			if (!batch.isEmpty()) writeComponentIdBatch(tx, batch);
			tx.commit();
		} finally {
			tx.close();
		}
	}

	private void writeComponentIdBatch(Transaction tx, List<Map<String, Object>> batch) {
		StringBuilder sb = new StringBuilder();
		sb.append("UNWIND $rows AS row ");
		sb.append("MATCH (n:Nexus) WHERE id(n) = row.id ");
		sb.append("SET n." + NexusProperty.COMPONENTID.key + " = row.cid");
		tx.execute(sb.toString(), Map.of("rows", batch));
	}

	private static int find(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]]; // path halving
			i = parent[i];
		}
		return i;
	}

	private static void union(int[] parent, int a, int b) {
		int ra = find(parent, a);
		int rb = find(parent, b);
		if (ra != rb) parent[ra] = rb;
	}

	private void processSize2Networks(Neo4JDatastore graph) {
		
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

		List<Long> ids = new ArrayList<>(componentIds);

		//store the nodes to visit in array list
		List<Long> toProcess = new ArrayList<>();
		try (Transaction tx = graph.getDatabase().beginTx()) {

			StringBuilder sb = new StringBuilder();
			sb.append("MATCH(a:Nexus) WHERE ");
			sb.append("a." + NexusProperty.COMPONENTID.key);
			sb.append(" IN $componentIds ");
			sb.append(" and isEmpty([(a)-[fp:FLOWPATH]->() WHERE ");
			sb.append("fp." + FlowpathProperty.RANK.key );
			sb.append(" = " + RankType.PRIMARY.getChyfValue() + " and ");
			sb.append("fp." + FlowpathProperty.EF_TYPE.key);
			sb.append(" <> " + EfType.BANK.getChyfValue() + " | a]) ");
			sb.append("RETURN id(a) AS id ");

			List<Long> startNodes = new ArrayList<>();
			try (Result result = tx.execute(sb.toString(), Map.of("componentIds", ids))) {
				while (result.hasNext()) {
					startNodes.add((Long) result.next().get("id"));
				}
			}

			for (Long startNodeId : startNodes) {
				toProcess.addAll(bfsUpstreamOrder(tx, componentIds, startNodeId));
			}
		}

		final int COMMIT_BATCH_SIZE = 5000;

		int commitcnt = 0;
		Transaction tx = graph.getDatabase().beginTx();
		try {
			//visit nodes in order committing every x number of visits
			for (Long nid : toProcess) {
				if (commitcnt == COMMIT_BATCH_SIZE) {
					tx.commit();
					tx = graph.getDatabase().beginTx();
					commitcnt = 0;
				}
				commitcnt++;
	
				int cnt = 0;
				int order = -1;
				Node n = tx.getNodeById(nid);

				String upNameId = null;
				
				if (this.nameOption == StreamOrderArgs.MAINSTEM_NAME_OP.ALWAYS) {
					for (Relationship r : n.getRelationships(Direction.OUTGOING)) {
						if ((Integer) r.getProperty(FlowpathProperty.RANK.key) == RankType.PRIMARY.getChyfValue()) {
							upNameId = (String)r.getProperty(FlowpathProperty.NAMEID.key, null);;
							break;
						}
					}
				}else if (this.nameOption == StreamOrderArgs.MAINSTEM_NAME_OP.SINGLELINE) {
					for (Relationship r : n.getRelationships(Direction.OUTGOING)) {
						int eftype = (Integer)r.getProperty(FlowpathProperty.EF_TYPE.key); 
						if (eftype == EfType.INFRASTRUCTURE.getChyfValue() || eftype == EfType.REACH.getChyfValue()) {
							if ((Integer) r.getProperty(FlowpathProperty.RANK.key) == RankType.PRIMARY.getChyfValue()) {								
								upNameId = (String)r.getProperty(FlowpathProperty.NAMEID.key, null);;
								break;
							}
						}
					}
				}
	
				Node longestupstreamNode = null;
				double longestupstream = -1;
				
				Node sameNameUpstreamNode = null;

				int shorder = 0;
				
				for (Relationship r : n.getRelationships(Direction.INCOMING)) {
					if (((Integer) r.getProperty(FlowpathProperty.EF_TYPE.key)) == EfType.BANK.getChyfValue() ||
							((Integer) r.getProperty(FlowpathProperty.RANK.key)) != RankType.PRIMARY.getChyfValue())
						continue;
	
					
					Node fromnode = r.getStartNode();
	
					double length = (double) r.getProperty(FlowpathProperty.LENGTH.key);	

					String nameid = (String)r.getProperty(FlowpathProperty.NAMEID.key, null);					
					double uplength = (Double)fromnode.getProperty(NexusProperty.UPSTREAMLENGTH.key, 0.0);
	
					if (upNameId != null && nameid != null && nameid.equals(upNameId)) {
						sameNameUpstreamNode = fromnode;
					}
					
					if ((uplength + length) > longestupstream) {
						longestupstream = uplength + length;
						longestupstreamNode = fromnode;
					}

					if (!fromnode.hasProperty(NexusProperty.SHORDER.key)) {
						logger.error("ERROR: node missing shorder value"  + fromnode.getProperty(NexusProperty.ID.key));
						System.out.println("ERROR: " + fromnode.getProperty(NexusProperty.ID.key));
					}
					Integer nshorder = (Integer) fromnode.getProperty(NexusProperty.SHORDER.key, -9999);
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
				
				if (sameNameUpstreamNode != null) {
					n.setProperty(NexusProperty.MAINSTEMID.key, sameNameUpstreamNode.getProperty(NexusProperty.MAINSTEMID.key));
				}else if (longestupstreamNode == null) {
					n.setProperty(NexusProperty.MAINSTEMID.key, UUID.randomUUID().toString());
				} else {
					n.setProperty(NexusProperty.MAINSTEMID.key, longestupstreamNode.getProperty(NexusProperty.MAINSTEMID.key));
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

				if (commitcnt == COMMIT_BATCH_SIZE) {
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

	}

	/**
	 * Breadth-first walk upstream from startNode along primary, non-bank FLOWPATH edges.
	 * These edges form a tree rooted at the outlet (each node has at most one outgoing
	 * primary edge), so reversing the BFS visit order yields a valid upstream-to-downstream
	 * processing order — replaces the gds.alpha.bfs.stream call, which materializes a full
	 * WalkPath object and was the main cost for very large components.
	 */
	private List<Long> bfsUpstreamOrder(Transaction tx, Set<Long> componentIds, Long startNodeId) {
		List<Long> visitOrder = new ArrayList<>();
		Set<Long> visited = new HashSet<>();
		Deque<Long> queue = new ArrayDeque<>();

		queue.add(startNodeId);
		visited.add(startNodeId);

		while (!queue.isEmpty()) {
			Long id = queue.poll();
			visitOrder.add(id);

			Node n = tx.getNodeById(id);
			for (Relationship r : n.getRelationships(Direction.INCOMING)) {
				if (((Integer) r.getProperty(FlowpathProperty.EF_TYPE.key)) == EfType.BANK.getChyfValue() ||
						((Integer) r.getProperty(FlowpathProperty.RANK.key)) != RankType.PRIMARY.getChyfValue())
					continue;

				Node up = r.getStartNode();
				Long upId = up.getId();
				if (visited.contains(upId)) continue;

				Long upComponentId = (Long) up.getProperty(NexusProperty.COMPONENTID.key, null);
				if (upComponentId == null || !componentIds.contains(upComponentId)) continue;

				visited.add(upId);
				queue.add(upId);
			}
		}

		Collections.reverse(visitOrder); // downstream-first -> upstream-first
		return visitOrder;
	}
}
