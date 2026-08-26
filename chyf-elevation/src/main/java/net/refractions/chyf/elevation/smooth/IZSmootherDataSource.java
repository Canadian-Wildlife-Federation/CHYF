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

import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Data source input for the elevation (z) smoothing tools.
 *
 * @author Emily
 *
 */
public interface IZSmootherDataSource extends AutoCloseable{

	/**
	 * Claims the next available block of work for processing, marking
	 * it so other workers do not pick it up.
	 *
	 * @return the next block to process, or null if none remain
	 * @throws Exception
	 */
	public Block checkOutNextBlock() throws Exception;

	/**
	 * Marks the given block as complete.
	 *
	 * @param block
	 * @throws SQLException
	 */
	public void finishBlock(Block block) throws SQLException;

	/**
	 * Releases any resources (e.g. database connections) held by this
	 * data source.
	 *
	 * @throws SQLException
	 */
	public void close() throws SQLException;

	/**
	 * Builds the node graph (from/to node connectivity and raw elevation
	 * values) for the flowpaths within the given block.
	 *
	 * @param block
	 * @return a map of node id to node, for all nodes in the block
	 * @throws SQLException
	 */
	public HashMap<UUID, Node> getNodeGraph(Block block) throws SQLException ;

	/**
	 * Streams all flowpaths within the given block, handing each one to
	 * the given processor and writing the (possibly modified) geometry
	 * back to the data source. Writes are batched and committed
	 * periodically so the entire block is never held in memory.
	 *
	 * @param block
	 * @param processor invoked once per flowpath; any changes it makes to
	 * the flowpath geometry are written back to the data source
	 * @throws Exception
	 */
	public void processFlowPaths(Block block, Consumer<EFlowpath> processor) throws Exception ;

}
