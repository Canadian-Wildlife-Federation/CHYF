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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;

/**
 * In memory data source for testing the z smoothing engine.  Flowpaths are
 * added with {@link #addFlowpath(UUID, UUID, UUID, double[][])} and the
 * smoothed geometries written back by the engine are kept for inspection.
 *
 * All flowpaths belong to a single graph within a single block.
 */
public class MockZSmootherDataSource implements IZSmootherDataSource {

	public static final int BLOCK_ID = 1;
	public static final int GRAPH_ID = 1;

	private GeometryFactory gf = new GeometryFactory();

	private List<EFlowpath> edges = new ArrayList<>();
	private HashMap<UUID, LineString> results = new HashMap<>();
	private Set<Integer> finishedBlocks = new HashSet<>();

	private boolean blockCheckedOut = false;
	private boolean closed = false;

	/**
	 * Adds a flowpath to the data source.
	 *
	 * @param id flowpath id
	 * @param fromNodeId upstream node id
	 * @param toNodeId downstream node id
	 * @param xyz one {x,y,z} triple per vertex; the first and last vertex
	 * are the from and to nodes respectively
	 */
	public void addFlowpath(UUID id, UUID fromNodeId, UUID toNodeId, double[][] xyz) {
		Coordinate[] c = new Coordinate[xyz.length];
		for (int i = 0; i < xyz.length; i++) {
			c[i] = new Coordinate(xyz[i][0], xyz[i][1], xyz[i][2]);
		}
		edges.add(new EFlowpath(id, gf.createLineString(c), fromNodeId, toNodeId));
	}

	/**
	 * @return the geometry written back by the engine for the given flowpath
	 */
	public LineString getResult(UUID flowpathId) {
		return results.get(flowpathId);
	}

	public boolean isBlockFinished(int blockId) {
		return finishedBlocks.contains(blockId);
	}

	public boolean isClosed() {
		return closed;
	}

	@Override
	public Block checkOutNextBlock() throws Exception {
		if (blockCheckedOut) return null;
		blockCheckedOut = true;

		Block b = new Block(BLOCK_ID);
		b.addGraphId(GRAPH_ID);
		return b;
	}

	@Override
	public void finishBlock(Block block) {
		finishedBlocks.add(block.getBlockId());
	}

	@Override
	public void close() {
		closed = true;
	}

	/**
	 * Builds the node graph the same way the postgis data source does; node
	 * elevations are taken from the start/end point z values of the flowpath
	 * geometries.
	 */
	@Override
	public HashMap<UUID, Node> getNodeGraph(Block block) {
		HashMap<UUID, Node> nodes = new HashMap<>();

		for (EFlowpath edge : forBlock(block)) {
			Coordinate[] c = edge.getLineString().getCoordinates();

			Double fromZ = Double.valueOf(c[0].getZ()).isNaN() ?
					ZSmootherPostGisDataSource.NO_DATA : c[0].getZ();
			Double toZ = Double.valueOf(c[c.length - 1].getZ()).isNaN() ?
					ZSmootherPostGisDataSource.NO_DATA : c[c.length - 1].getZ();

			Node n = nodes.get(edge.getFromNodeId());
			if (n == null) {
				n = new Node(edge.getFromNodeId(), fromZ);
				nodes.put(edge.getFromNodeId(), n);
			}
			n.addOutNode(edge.getToNodeId());

			n = nodes.get(edge.getToNodeId());
			if (n == null) {
				n = new Node(edge.getToNodeId(), toZ);
				nodes.put(edge.getToNodeId(), n);
			}
			n.addInNode(edge.getFromNodeId());
		}
		return nodes;
	}

	@Override
	public void processFlowPaths(Block block, Consumer<EFlowpath> processor) {
		for (EFlowpath edge : forBlock(block)) {
			processor.accept(edge);
			//stand in for the geometry update
			results.put(edge.getId(), edge.getLineString());
		}
	}

	private List<EFlowpath> forBlock(Block block) {
		if (block.getBlockId() != BLOCK_ID) return new ArrayList<>();
		return edges;
	}

}
