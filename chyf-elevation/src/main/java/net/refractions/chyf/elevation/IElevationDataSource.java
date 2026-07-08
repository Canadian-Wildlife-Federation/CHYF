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
package net.refractions.chyf.elevation;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.filter.FilterFactory2;

/**
 * Data source input for flowpath elevation tools
 * 
 * @author Emily
 *
 */
public interface IElevationDataSource extends AutoCloseable {
	
	public static final FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

	/**
	 * Writes the (elevation-enriched) geometries of the given flowpaths
	 * back to the data source.
	 *
	 * @param features
	 * @throws Exception
	 */
	public void updateFlowathGeometries(Collection<EFlowpath> features) throws Exception;

	/**
	 * Retrieves the flowpaths intersecting the given bounds.
	 *
	 * @param bounds
	 * @return
	 * @throws Exception
	 */
	public List<EFlowpath> getFlowPaths(ReferencedEnvelope bounds) throws Exception;

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
	 * @throws Exception
	 */
	public void close() throws Exception;

	/**
	 * @return the path/URI of the cloud-optimized GeoTIFF (COG) DEM to
	 * read elevation values from
	 */
	public String getCogPath();

}
