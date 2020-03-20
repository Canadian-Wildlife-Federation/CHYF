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
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.EcType;

/**
 * The CatchmentDelineator is a Java application that can be used
 * to build catchment/watershed polygons from a DEM and hydrography linear constraints.
 */
public class CatchmentDelineator {
    private static Logger logger = LoggerFactory.getLogger(CatchmentDelineator.class);

    //private static String inputTiff = "L:\\Refractions\\NRCan\\CHyF Catchment Delineation 2019-20\\data\\bc";
    //private static String inputGeopackage = "L:\\Refractions\\NRCan\\CHyF Skeletonizer 2019-20\\data_processed\\KOTL.gpkg";
    //private static String inputGeopackage = "L:\\Refractions\\NRCan\\CHyF Catchment Delineation 2019-20\\data\\bc\\KOTL.gpkg";
    //private static String outputGeopackage = "L:\\Refractions\\NRCan\\CHyF Catchment Delineation 2019-20\\data\\bc\\output.gpkg";
    //private static String inputTiff = "L:\\Refractions\\NRCan\\CHyF Catchment Delineation 2019-20\\data\\quebec";
    //private static String inputGeopackage = "L:\\Refractions\\NRCan\\CHyF Catchment Delineation 2019-20\\data\\quebec\\Richelieu.gpkg";
    //private static String outputGeopackage = "C:\\Users\\cmhod\\Documents\\chyf_output.gpkg";
    
    private int numThreads = 1;
    private int maxBlocks = Integer.MAX_VALUE;
    private DataManager dm;
    private boolean recover = false;
    private List<DataBlock> blocks;
    
    public static void main(String[] args) throws IOException {	
    	Args a = Args.parseArguments(args, "CatchmentDelineator");
		if (a == null) return;
    	
    	CatchmentDelineator wb = new CatchmentDelineator(a);
    	wb.build();
    }
    
    public CatchmentDelineator(Args args) throws IOException {
		Path inputPath = args.getInput();
		Path inputTiffDir = args.getTiffDir();
		Path outputPath = args.getOutput();
    	numThreads = args.getCores();
    	recover = args.getRecover();
		
    	dm = new DataManager(inputTiffDir, inputPath, outputPath, recover);
    }
    
    public void build() {
    	if(recover) {
    		blocks = dm.getBlocks();
    	} else {
    		HydroEdgeLoader loader = new HydroEdgeLoader(dm);
    		loader.load();
    		blocks = dm.generateBlocks(loader.getEdges());
    	}
    	
    	processBlocks();
    	buildBoundaries();
    	// QA?
    }
    
    public void processBlocks() {
    	// TODO delete only watershed boundaries that we need to, for each block being processed
    	//dm.deleteWatershedBoundaries(null);
        BlockProcessor processor = new BlockProcessor(dm);
        Stream<DataBlock> blocksToRun = blocks.stream().filter(new Predicate<DataBlock>() {

			@Override
			public boolean test(DataBlock block) {
				if(block.getState() == BlockState.COMPLETE) {
					return false;
				}
				return true;
			}
        	
        }).limit(maxBlocks);
        
        ForkJoinPool pool = new ForkJoinPool(numThreads);
        ForkJoinTask<?> task = pool.submit(
        		() -> blocksToRun.parallel().forEach(
        				new Consumer<DataBlock>() {
								@Override
								public void accept(DataBlock block) {
									processor.run(block);
								}
        				}
       			)
        );
        
        try {
        	task.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
    }
    
    public void buildBoundaries() {
    	dm.deleteECatchments(EcType.REACH, EcType.BANK);
    	WatershedBoundaryMerger wbm = new WatershedBoundaryMerger(dm);
    	List<WatershedBoundary> watersheds = wbm.merge();
    	dm.writeWatersheds(watersheds);
    }

}
