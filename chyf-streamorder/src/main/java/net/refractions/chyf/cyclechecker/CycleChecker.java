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
package net.refractions.chyf.cyclechecker;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main class for stream order computer.
 * 
 * @author Emily
 *
 */
public class CycleChecker {

	private static Logger logger = LoggerFactory.getLogger(CycleChecker.class);

	private PostgresqlCycleDataSource dataSource;

	private static int EXIT = 1;
	private static int ENTER = 0;
	
	public CycleChecker(PostgresqlCycleDataSource dataSource) {
		this.dataSource = dataSource;
	}

	public void doCycleCheck() throws SQLException {
		logger.info("Building Graph");
		Collection<Node> nodes = dataSource.buildGraph();
		logger.info("Checking Cycles");
		this.doCycleCheck(nodes);
	}
	
	//https://stackoverflow.com/questions/46506077/how-to-detect-cycles-in-a-directed-graph-using-the-iterative-version-of-dfs
	private void doCycleCheck(Collection<Node> nodes) {
		List<Node> sinks = new ArrayList<>();
		for (Node n : nodes) {
			if (n.outNodes().length == 0)
				sinks.add(n);
		}


		Stack<Object[]> toVisit = new Stack<>();
		for (Node node : sinks)
			toVisit.push(new Object[] {node, ENTER});

		while (!toVisit.isEmpty()) {
			Object[] data = toVisit.pop();
			Node current = (Node) data[0];
			int state = (int) data[1];

			if (state == EXIT) {
				current.setState(Node.State.CLOSED);
			}else {
				current.setState(Node.State.WORKING);
				toVisit.push(new Object[] {current, EXIT});
			
				for (Node n : current.inNodes()) {
					if (n.getState() == Node.State.WORKING ) {
						System.out.println("CYCLE DETECTED around nexus id: " + n.getId());
						logger.error("CYCLE DETECTED around nexus id: " + n.getId());
						return;
					}else if (n.getState() == Node.State.OPEN) {
						toVisit.push(new Object[] {n, ENTER});		
					}
				}
			}
		}

		for (Node n : nodes) {
			if (n.getState() != Node.State.CLOSED) {
				logger.error("Nexus not visited: " + n.getId());
			}
		}
		System.out.println("NO CYCLES DETECTED");
		logger.info("NO CYCLES DETECTED");
	}

	public static void main(String[] args) throws Exception {

		CycleCheckerArgs cargs = new CycleCheckerArgs(CycleChecker.class.getCanonicalName());
		if (!cargs.parseArguments(args))
			return;

		Long now = System.nanoTime();
		try (PostgresqlCycleDataSource source = new PostgresqlCycleDataSource(cargs.getDbConnectionString(),
				cargs.getInputSchema())) {

			source.connect();

			CycleChecker cc = new CycleChecker(source);
			cc.doCycleCheck();
		}

		Long end = System.nanoTime();
		double time = (end - now) / (double) Math.pow(10, 9);
		logger.info("Processing Time:" + time + " sec");
	}

}
