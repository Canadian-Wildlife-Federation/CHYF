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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.elevation.AppProperties;
import net.refractions.chyf.elevation.ElevationArgs;

/**
 * Main entry point for the elevation processor. Reads configuration
 * and launches a pool of {@link ElevationJob} workers to assign
 * elevation values to flowpath geometries.
 *
 * @author Emily
 *
 */
public class ElevationEngine {

	public static double NO_DATA = -9999;
	private static Logger logger = LoggerFactory.getLogger(ElevationEngine.class);

	private int numThreads = 1;
	
	public ElevationEngine(int numThreads) {
		this.numThreads = numThreads;
	}
	
	public void doElevation(Supplier<IElevationDataSource> dataSource) throws InterruptedException {
		ExecutorService service = Executors.newFixedThreadPool(numThreads);
				
		for (int i = 0; i < numThreads; i ++) {
			service.submit(new ElevationJob(dataSource));
		}
		
		service.shutdown();
		service.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		
	}

	public static void main(String[] args) throws Exception {

		ElevationArgs cargs = new ElevationArgs(ElevationEngine.class.getCanonicalName());
		if (!cargs.parseArguments(args))
			return;

		AppProperties props = AppProperties.loadFromFile(Paths.get(cargs.getPropertiesFile()));
		
		Long now = System.nanoTime();
		try (ElevationPostGisDataSource source = new ElevationPostGisDataSource(
				cargs.getDbConnectionString(), props)) {
			if (!cargs.getDoContinue()) {
				source.initWorkingTable();
			}
		}
		
		ElevationEngine computer = new ElevationEngine(props.getNumThreads());
		computer.doElevation(()->new ElevationPostGisDataSource(cargs.getDbConnectionString(), props));
		

		Long end = System.nanoTime();
		double time = (end - now) / (double) Math.pow(10, 9);
		logger.info("Processing Time:" + time + " sec");
	}
	
	
	public static Properties readProperties(String filename) {
		Properties props = new Properties();

		try (InputStream is = Files.newInputStream(Paths.get(filename))) {
		    if (is == null) {
		        throw new IOException("Properties file " + filename + " not found.");
		    }
		    props.load(is);
		} catch (IOException e) {
		    e.printStackTrace();
		}
		return props;
	}
}
