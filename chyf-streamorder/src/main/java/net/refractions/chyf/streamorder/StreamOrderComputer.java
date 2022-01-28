/*
 * Copyright 2022 Canadian Wildlife Federation
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
package net.refractions.chyf.streamorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for stream order computer.
 * 
 * @author Emily
 *
 */
public class StreamOrderComputer {

	private static Logger logger = LoggerFactory.getLogger(StreamOrderComputer.class);

	public static void main(String[] args) throws Exception {
		
		StreamOrderArgs cargs = new StreamOrderArgs(StreamOrderComputer.class.getCanonicalName());
		if (!cargs.parseArguments(args)) return;
		
		Long now = System.nanoTime();
		try(PostgresqlGraphDataSource source = new PostgresqlGraphDataSource(
				cargs.getDbConnectionString(), cargs.getInputSchema(), cargs.getOutputTable())){
			
			source.connect();
	
			StreamOrderMainstemEngine computer = new StreamOrderMainstemEngine(cargs.useNames());
			try {
				computer.computeOrderValues(source);
			} finally {
				source.close();
			}
		}

		Long end = System.nanoTime();
		double time = (end - now) / (double) Math.pow(10, 9);
		logger.info("Processing Time:" + time + " sec");
	}
}
