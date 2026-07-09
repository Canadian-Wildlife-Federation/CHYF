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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.elevation.AppProperties;

/**
 * Main entry point for the elevation smoothing processor. Reads
 * configuration and launches a pool of {@link ZSmootherJob} workers
 * to smooth flowpath elevations block by block.
 *
 * @author Emily
 *
 */
public class ZSmoothingEngine {

	private static Logger logger = LoggerFactory.getLogger(ZSmoothingEngine.class);

	private int numThreads = 1;

	/**
	 * @param numThreads the number of worker threads to run smoothing
	 * jobs on
	 */
	public ZSmoothingEngine(int numThreads) {
		this.numThreads = numThreads;
	}

	/**
	 * Runs the smoothing process, launching one {@link ZSmootherJob}
	 * per thread and blocking until all blocks have been processed.
	 *
	 * @param dataSource
	 * @throws InterruptedException
	 */
	public void doSmoothing(IZSmootherDataSource dataSource) throws InterruptedException {
		ExecutorService service = Executors.newFixedThreadPool(numThreads);

		for (int i = 0; i < numThreads; i ++) {
			service.submit(new ZSmootherJob(dataSource));
		}

		service.shutdown();
		service.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

	}

	/**
	 * Parses command line arguments, loads the properties file, and
	 * runs the smoothing process against the configured data source.
	 *
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {

		SmootherArgs cargs = new SmootherArgs(ZSmoothingEngine.class.getCanonicalName());
		if (!cargs.parseArguments(args))
			return;

		AppProperties props = AppProperties.loadFromFile(Paths.get(cargs.getPropertiesFile()));

		
		Long now = System.nanoTime();
		try (ZSmootherPostGisDataSource source = new ZSmootherPostGisDataSource(
				cargs.getDbConnectionString(), props, !cargs.getDoContinue())) {

			ZSmoothingEngine computer = new ZSmoothingEngine(props.getNumThreads());
			computer.doSmoothing(source);
		}

		Long end = System.nanoTime();
		double time = (end - now) / (double) Math.pow(10, 9);
		logger.info("Processing Time:" + time + " sec");
	}
	
	
	/**
	 * @param filename path to the properties file
	 * @return the loaded properties, or an empty/partial set if the
	 * file cannot be read
	 */
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
