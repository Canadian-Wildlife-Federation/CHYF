/*******************************************************************************
 * Copyright 2020 Government of Canada
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
 *******************************************************************************/

package net.refractions.chyf.watershed.builder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.util.ProcessStatistics;
import net.refractions.chyf.watershed.WatershedBoundaryBuilder;
import net.refractions.chyf.watershed.inputprep.DuplicateHydroEdgeRemover;
import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.WatershedBoundaryEdge;
import net.refractions.chyf.watershed.qa.DuplicateConstraintSegmentFinder;
import net.refractions.chyf.watershed.qa.WatershedQA;

/**
 * BlockProcessor extracts the DEM and Hydro Edge, data for a block and
 * runs the {@link WatershedBoundaryBuilder} over them. It then saves the generated watershed boundaries to
 * the output geopackage.
 */
public class BlockProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BlockProcessor.class);

    private DataManager dm;
    
    public BlockProcessor(DataManager dm) {
    	this.dm = dm;
    }
    
    /**
     * Processes the given block.
     * @throws TransformException 
     * @throws IOException 
     */
    public void run(DataBlock block) {
        try {
        	ProcessStatistics stats = new ProcessStatistics();
    		
        	// we assume that there should not be any watershedBoundaries previously created for this block
	        //dm.deleteWatershedBoundaries(block);
	
	        block.setState(BlockState.EXTRACT);
	        stats.reportStatus(logger, "Processing Block " + block);
	
	        List<HydroEdge> allHydroEdges = dm.getHydroEdges(block);
	        if(allHydroEdges.isEmpty()) {
	        	block.setState(BlockState.DISABLED, "No hydro edges in block.");
	        	stats.reportStatus(logger, "No hydro edges in block; skipping");
	        	return;
	        }
	
	        List<Coordinate> demCoords = dm.getDEM(block);
	        if(demCoords.isEmpty()) {
	        	block.setState(BlockState.DISABLED, "No DEM in block");
	        	stats.reportStatus(logger, "No DEM in block; skipping");
	        	return;
	        }
	        List<Coordinate> respectedDemCoords = new ArrayList<Coordinate>();
	
        	block.setState(BlockState.BUILD);

            DuplicateHydroEdgeRemover dupHERemover = new DuplicateHydroEdgeRemover(allHydroEdges);
            allHydroEdges.clear();
            allHydroEdges.addAll(dupHERemover.getRemainingHydroEdges());

            DuplicateConstraintSegmentFinder finder = new DuplicateConstraintSegmentFinder(allHydroEdges);
            if (finder.hasDuplicates()) {
            	block.setState(BlockState.FAILEDQA,
                    "Duplicate constraint segments found at "
                            + finder.getDuplicates().iterator().next().toString());
                logger.error("Duplicate constraint segments found");
                return;
            }
            
            WatershedBoundaryBuilder builder = new WatershedBoundaryBuilder(demCoords, respectedDemCoords, allHydroEdges, dm.getGeometryFactory(), stats);
            builder.build();

            Collection<WatershedBoundaryEdge> watershedBoundaryColl = builder.getBoundaryEdges();

            stats.reportStatus(logger, "Removing watershed boundaries outside of block boundaries");
            Geometry blockGeom = JTS.toGeometry(block.getBounds());
            
            for (Iterator<WatershedBoundaryEdge> iter = watershedBoundaryColl.iterator(); iter.hasNext();) {
                WatershedBoundaryEdge wsEdge = (WatershedBoundaryEdge) iter.next();
                if(blockGeom.distance(wsEdge.getGeometry()) > 0) {
                    iter.remove();
                }
            }

            stats.reportStatus(logger, "Saving Results");
            dm.writeWatershedBoundaries(watershedBoundaryColl);

            stats.reportStatus(logger, "Stage3: QA");
            block.setState(BlockState.QA);

            WatershedQA watershedQA = new WatershedQA(builder);

            if (!watershedQA.isValid()) {
                logger.error(watershedQA.getErrorMessage());
                block.setState(BlockState.FAILEDQA, watershedQA.getErrorMessage());
                return;
            } else {
            	block.setState(BlockState.COMPLETE);
            }

        } catch (Exception e) {
            logger.error("Encountered errors while processing block: {}", block);
            logger.debug(e.getClass().getName() + ": " + e.getMessage(), e);
            block.setState(BlockState.ERROR, e);
            return;
        }
    }

}
