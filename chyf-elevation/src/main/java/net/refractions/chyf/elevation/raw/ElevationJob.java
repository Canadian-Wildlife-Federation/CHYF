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
package net.refractions.chyf.elevation.raw;

import java.util.List;
import java.util.function.Supplier;

import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Worker that checks out blocks from the data source and applies
 * DEM elevation values to the flowpath geometries within each block.
 *
 * @author Emily
 *
 */
public class ElevationJob implements Runnable {

	private static Logger logger = LoggerFactory.getLogger(ElevationJob.class);

	private Supplier<IElevationDataSource> dataSourceProvider;
	private IElevationDataSource dataSource;
	private DemElevationReader elevationSource;

	public ElevationJob(Supplier<IElevationDataSource> dataSourceProvider) {
		this.dataSourceProvider = dataSourceProvider;
	}

	@Override
	public void run() {
		logger.info("Starting Elevation Processor");
		try (IElevationDataSource dataSource = dataSourceProvider.get()) {
			this.dataSource = dataSource;
			try (DemElevationReader elevationSource = new DemElevationReader(this.dataSource.getCogPath())) {
				this.elevationSource = elevationSource;

				while (true) {
					try {
						Block block = dataSource.checkOutNextBlock();
						if (block == null)
							return;
						logger.info("Processing Block: " + block.getBlockId());
						applyElevation(block);
						dataSource.finishBlock(block);
						logger.info("Finished Block: " + block.getBlockId());
					} catch (Exception ex) {
						logger.error("Error processing block.", ex);
					}
				}
			} catch (Exception ex) {
				logger.error("Error assigning 3d elevation", ex);
			}
		} catch (Exception ex) {
			logger.error("Error with datasource", ex);
		}
	}

	private void applyElevation(Block block) throws Exception {
		//expand bounds to include ALL edges then apply elevation
		//too every coordinate; then it doesn't matter if we overwrite
		//for edges that cross block boundaries
		//they will get processed twice, but there won't be any mulit-
		//threading issues
		List<EFlowpath> edges = dataSource.getFlowPaths(block.getBounds());
		
		if(edges.isEmpty()) return;
		
		ReferencedEnvelope re =  new ReferencedEnvelope(block.getBounds());
		for (EFlowpath edge : edges) {
			re.expandToInclude(edge.getLineString().getEnvelopeInternal());
		}
		
		GridBlock cogImage = elevationSource.getElevations(block, re);
		
		for (EFlowpath edge : edges) {
			edge.setLineString(applyElevation(edge.getLineString(), cogImage));
		}
		dataSource.updateFlowpathGeometries(edges);
	}

	private LineString applyElevation(LineString ls, GridBlock cogImage) throws TransformException {
		Coordinate[] cs = ls.getCoordinates();
		for (int i = 0; i < cs.length; i++) {
			double elevationValue = cogImage.getValue(cs[i]);
			cs[i].setZ(elevationValue);
		}
		
		return ls.getFactory().createLineString(cs);
	}

}
