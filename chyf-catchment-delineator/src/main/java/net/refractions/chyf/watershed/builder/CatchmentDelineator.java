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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.datasource.EcType;
import net.refractions.chyf.util.ProcessStatistics;
import net.refractions.chyf.watershed.model.HydroEdge;

/**
 * The CatchmentDelineator is a Java application that can be used
 * to build catchment/watershed polygons from a DEM and hydrography linear constraints.
 */
public class CatchmentDelineator {
    private static Logger logger = LoggerFactory.getLogger(CatchmentDelineator.class);
    
    private int numThreads = 1;
    private int maxBlocks = Integer.MAX_VALUE;
    private DataManager dm;
    private boolean recover = false;
    private List<DataBlock> blocks;
    
    public static void main(String[] args) throws IOException {	
    	Args a = Args.parseArguments(args, "CatchmentDelineator");
		if (a == null) return;
    	
    	CatchmentDelineator cd = new CatchmentDelineator(a);
    	cd.build();
    }
    
    public CatchmentDelineator(Args args) throws IOException {
    	
    	ICatchmentDelineatorDataSource dataSource = null;
    	
    	Path inputTiffDir = args.getTiffDir();
    	numThreads = args.getCores();
		recover = args.getRecover();
		
    	if (args.geopkg) {
    		Path inputPath = Paths.get(args.getInput());
    		Path outputPath = Paths.get(args.getOutput());
			
    		if (!recover) {
    			ChyfGeoPackageDataSource.deleteOutputFile(outputPath);
    			Files.copy(inputPath, outputPath, StandardCopyOption.REPLACE_EXISTING);
    		}
    		dataSource = new CatchmentDelineatorGeoPackageDataSource(outputPath);
    	}else {
    		if (!args.hasAoi()) return;
    		dataSource = new CatchmentDelineatorPostgisDataSource(args.dbstring, args.getInput(), args.getOutput());
    		((CatchmentDelineatorPostgisDataSource)dataSource).setAoi(args.getAoi());
    		if (!recover) {
    			((CatchmentDelineatorPostgisDataSource)dataSource).clearOutputTables();
    		}
    	}
		
    	dm = new DataManager(dataSource, inputTiffDir, recover);
    }
    
    public void build() {
    	//List<HydroEdge> hydroEdges = null;
    	if(recover) {
    		blocks = dm.getBlocks();
    		//hydroEdges = dm.getHydroEdges(null);
    	} else {
    		HydroEdgeLoader loader = new HydroEdgeLoader(dm);
    		List<HydroEdge> hydroEdges = loader.load();	
    		blocks = dm.generateBlocks(hydroEdges);
    	}
    	//IndexedClosestHydroFinder hydroFinder = new IndexedClosestHydroFinder(hydroEdges);
    	
    	processBlocks();
    	buildBoundaries();
    	// QA?
    }
    
    public void processBlocks() {
        BlockProcessor processor = new BlockProcessor(dm);
        List<DataBlock> blocksToRun = blocks.stream().filter(new Predicate<DataBlock>() {

			@Override
			public boolean test(DataBlock block) {
				if(block.getState() == BlockState.COMPLETE) {
					return false;
				}
				return true;
			}
        	
        }).limit(maxBlocks).collect(Collectors.toList());
        
        ProcessStatistics ps = new ProcessStatistics();
        
        ForkJoinPool pool = new ForkJoinPool(numThreads);
        ForkJoinTask<?> task = pool.submit(
        		() -> blocksToRun.parallelStream().forEach(
        				new Consumer<DataBlock>() {
        						int completedBlocks = 0;
								@Override
								public void accept(DataBlock block) {
									processor.run(block);
									ps.reportStatus(logger, "Block Processing status: " + (++completedBlocks) + "/" + blocksToRun.size() + " blocks completed.");
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
    	List<Catchment> watersheds = wbm.merge();
    	dm.writeCatchments(watersheds);
    }

}
