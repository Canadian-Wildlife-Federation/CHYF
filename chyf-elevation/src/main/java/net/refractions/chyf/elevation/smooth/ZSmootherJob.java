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

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYZM;
import org.locationtech.jts.geom.GeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker that checks out blocks from the data source and smooths
 * flowpath elevations within each block. For every node graph, an
 * upstream pass computes the maximum elevation reachable looking
 * downstream and a downstream pass computes the minimum elevation
 * reachable looking upstream; each node's smoothed elevation is the
 * average of the two, and edge coordinates between nodes are
 * clamped/interpolated to stay monotonic between their endpoints.
 *
 * @author Emily
 *
 */
public class ZSmootherJob implements Runnable{

	static final Logger logger = LoggerFactory.getLogger(ZSmootherJob.class.getCanonicalName());

	private IZSmootherDataSource dataSource;
	private GeometryFactory gf = new GeometryFactory();
	
	public ZSmootherJob(IZSmootherDataSource dataSource) {
		this.dataSource = dataSource;
	}


	@Override
	public void run() {
		
		while(true) {
			try {
				Block b = dataSource.checkOutNextBlock();				
				if (b == null) return;
				logger.info("Processing Block: " + b.getBlockId());
				try {
					Map<UUID, Double> nodeElevations = processBlock(b);
					processBlockEdges(b, nodeElevations);
					dataSource.finishBlock(b);
				}catch (Exception ex) {
					logger.error("Unable to process block: " + b.getBlockId(), ex);
				}
			
			}catch (Exception ex) {
				logger.error("Unable to process blocks.", ex);
				return;
			}
		}		
	}
	
	private void processBlockEdges(Block b, Map<UUID, Double> nodeElevations) throws Exception {
		
		logger.info("Processing edges in: " + b.getBlockId());

		UUID lastEdge = new UUID(0L, 0L);
		while(true) {
			List<EFlowpath> edges = dataSource.getFlowPaths(b, lastEdge);
			if (edges.isEmpty()) return;
			for(EFlowpath edge : edges) {				
				lastEdge = edge.getId();
				processEdge(edge,  nodeElevations);
			}			
			//write edges
			dataSource.updateFlowpathGeometries(edges);
		}
			
	}
	
	private void processEdge(EFlowpath p,  Map<UUID, Double> nodeElevations){
		
		Coordinate[] c = p.getLineString().getCoordinates();
		
		Double upZ = nodeElevations.get(p.getFromNodeId());
		Double downZ = nodeElevations.get(p.getToNodeId());
		
		Double[] z = new Double[c.length];
		
		z[0] = upZ;
		z[z.length-1] = downZ;
		
		for (int i = 1; i < z.length - 1; i ++) {
			z[i] = c[i].getZ();
		}
		
		//Upstream pass (walking up the network):
		//for each vertex find the largest elevation value
		//on that vertex or downstream of that vertex (must be smaller then the
		//upstream node z value)
		Double[] zmax = new Double[c.length];
		zmax[c.length-1] = downZ;
		
		for (int i = c.length - 2; i >= 0; i --) {
			if (c[i].getZ() > upZ) {
				zmax[i] = upZ;
			}else {
				zmax[i] = Math.max(c[i].getZ(), zmax[i+1]);
			}
		}
		
		//Downstream pass 
		//for each vertex find the smallest elevation value
		//on that vertex or upstream of that vertex, the value
		//cannot be smaller than the downstream node z value
		Double[] zmin = new Double[c.length];
		zmin[0] = upZ;
		for (int i = 1; i < c.length; i ++) {
			if (c[i].getZ() < downZ) {
				zmin[i] = downZ;
			}else {
				zmin[i] = Math.min(c[i].getZ(), zmin[i-1]);
			}
		}
		
		//average
		CoordinateXYZM[] allc = new CoordinateXYZM[c.length];
		for (int i = 0; i < c.length; i ++) {
			double sz = (zmin[i] + zmax[i]) / 2.0;
			sz = Math.round(sz * 10000) / 10000.0;
			
			CoordinateXYZM mc = new CoordinateXYZM(c[i]);
			mc.setM(sz);
			allc[i] = mc;
			
		}
		
		p.setLineString(gf.createLineString(allc));
	}
	
	private Map<UUID, Double> processBlock(Block b) throws Exception {
		HashMap<UUID, Node> graph = dataSource.getNodeGraph(b);
		
		logger.info("Running upstream node pass on block: " + b.getBlockId());
		 //Upstream pass (walking up the network):
		//	 Starts at outlet points (nodes with no downstream connections)
		//	 Works upstream, visiting each node
		//	 Calculates a "maximum elevation" value for each node by looking at all downstream elevations
		Set<Node> sinkNodes = graph.values().stream()
			.filter(n->n.getOutNodes().size() == 0)
			.collect(Collectors.toSet());

		ArrayDeque<Node> toProcess = new ArrayDeque<>(sinkNodes);
		int stuckCnt = 0;
		while(!toProcess.isEmpty()) {
			Node n = toProcess.removeFirst();
			Double z = n.getRawZ();
			
			//already processed
			if (n.getMaxDownZ() != null) continue;
			
			boolean processed = true;
			for(UUID out : n.getOutNodes()) {
				Node outnode = graph.get(out);
				if (outnode.getMaxDownZ() == null) {
					processed = false;					
					break;
				}else {
					z = Double.max(z, outnode.getMaxDownZ());
				}
			}
			if (processed) {
				stuckCnt = 0;
				n.setMaxDownZ(z);
				//add all in nodes to the array
				for (UUID in : n.getInNodes()) {
					toProcess.add(graph.get(in));
				}
			}else {
				toProcess.add(n);
				stuckCnt ++;
				if (stuckCnt > toProcess.size()) {
					throw new Exception("Graph appears to have a cycle");
				}
			}
		}
		
		logger.info("Running downstream node pass on block: " + b.getBlockId());
		 //Downstream pass (walking down the network):
		//	 Starts at outlet points (nodes with no downstream connections)
		//	 Works upstream, visiting each node
		//	 Calculates a "maximum elevation" value for each node by looking at all downstream elevations
		Set<Node> sourceNodes = graph.values().stream()
			.filter(n->n.getInNodes().size() == 0)
			.collect(Collectors.toSet());

		toProcess = new ArrayDeque<>(sourceNodes);
		while(!toProcess.isEmpty()) {
			
			Node n = toProcess.removeFirst();
			Double z = n.getRawZ();
			//already processed
			if (n.getMinUpZ() != null) continue;
			
			boolean processed = true;
			for(UUID in : n.getInNodes()) {
				Node innode = graph.get(in);
				if (innode.getMinUpZ() == null) {
					processed = false;					
					break;
				}else {
					z = Double.min(z, innode.getMinUpZ());
				}
			}
			if (processed) {
				n.setMinUpZ(z);
				//add all in nodes to the array
				for (UUID out : n.getOutNodes()) {
					toProcess.add(graph.get(out));
				}
			}else {
				toProcess.add(n);
			}
		}
		
		logger.info("Computing smoothed value: " + b.getBlockId());
		return graph.values().stream().collect(Collectors.toMap(n->n.getId(), n->n.getSmoothedZ()));
	}
	
}
