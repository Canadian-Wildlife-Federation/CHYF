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

import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LineString;

/**
 * Tests the z smoothing engine against a single block containing a single
 * graph made up of five flowpaths:
 *
 * <pre>
 *   n1 --e1--\
 *             n3 --e3--\
 *   n2 --e2--/           n4 --e4--> n6
 *             n5 --e5--/
 * </pre>
 *
 * e4 is the sink edge (n6 has no downstream connections).
 *
 * Each edge has five internal vertices, one of which breaks the expected
 * downstream decrease in elevation (a vertex above the upstream node, a
 * vertex below the downstream node, or a vertex higher than the vertex
 * before it).  The n4/n6 node pair is also inverted (the downstream node
 * n6 sits 5m above the upstream node n4) so the node level max/min passes
 * have something to flatten.
 *
 * @author Emily
 *
 */
class TestZSmoother {

	//nodes
	private static final UUID NODE1 = node(1);
	private static final UUID NODE2 = node(2);
	private static final UUID NODE3 = node(3);
	private static final UUID NODE4 = node(4);
	private static final UUID NODE5 = node(5);
	private static final UUID NODE6 = node(6);

	//edges
	private static final UUID EDGE1 = edge(1);
	private static final UUID EDGE2 = edge(2);
	private static final UUID EDGE3 = edge(3);
	private static final UUID EDGE4 = edge(4);
	private static final UUID EDGE5 = edge(5);

	private static UUID node(int i) {
		return UUID.fromString("00000000-0000-0000-0000-00000000000" + i);
	}

	private static UUID edge(int i) {
		return UUID.fromString("00000000-0000-0000-0000-0000000000a" + i);
	}

	/**
	 * Raw node elevations:
	 * n1=100, n2=95, n3=90, n4=80, n5=92, n6=85
	 *
	 * Upstream pass (max looking downstream):
	 * n1=100, n2=95, n3=90, n4=85, n5=92, n6=85
	 *
	 * Downstream pass (min looking upstream):
	 * n1=100, n2=95, n3=90, n4=80, n5=92, n6=80
	 *
	 * Smoothed (average of the two):
	 * n1=100, n2=95, n3=90, n4=82.5, n5=92, n6=82.5
	 */
	@Test
	void testSmoothSingleBlockSingleGraph() throws Exception {

		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();

		//e1: n1 (100) -> n3 (90); vertex 3 (99) rises above the vertex before it
		dataSource.addFlowpath(EDGE1, NODE1, NODE3, new double[][] {
			{ 0,  0, 100},
			{10, 10,  98},
			{20, 20,  96},
			{30, 30,  99},
			{40, 40,  92},
			{50, 50,  91},
			{60, 60,  90}
		});

		//e2: n2 (95) -> n3 (90); vertex 2 (96) is above the upstream node and
		//vertex 4 (89) is below the downstream node
		dataSource.addFlowpath(EDGE2, NODE2, NODE3, new double[][] {
			{ 0, 120, 95},
			{10, 110, 94},
			{20, 100, 96},
			{30,  90, 93},
			{40,  80, 89},
			{50,  70, 91},
			{60,  60, 90}
		});

		//e3: n3 (90) -> n4 (80); vertex 3 (78) drops below the downstream node
		dataSource.addFlowpath(EDGE3, NODE3, NODE4, new double[][] {
			{ 60, 60, 90},
			{ 70, 60, 88},
			{ 80, 60, 86},
			{ 90, 60, 78},
			{100, 60, 84},
			{110, 60, 82},
			{120, 60, 80}
		});

		//e5: n5 (92) -> n4 (80); vertex 2 (93) is above the upstream node
		dataSource.addFlowpath(EDGE5, NODE5, NODE4, new double[][] {
			{ 60,  0, 92},
			{ 70, 10, 90},
			{ 80, 20, 93},
			{ 90, 30, 87},
			{100, 40, 85},
			{110, 50, 83},
			{120, 60, 80}
		});

		//e4: n4 (80) -> n6 (85); the sink edge, and an inverted one - the
		//downstream node is higher than the upstream node
		dataSource.addFlowpath(EDGE4, NODE4, NODE6, new double[][] {
			{120, 60, 80},
			{130, 60, 79},
			{140, 60, 81},
			{150, 60, 83},
			{160, 60, 84},
			{170, 60, 82},
			{180, 60, 85}
		});

		ZSmootherJob job = new ZSmootherJob(() -> dataSource);
		job.run();

		Assertions.assertTrue(dataSource.isBlockFinished(MockZSmootherDataSource.BLOCK_ID),
				"the block should have been marked as finished");
		Assertions.assertTrue(dataSource.isClosed(), "the data source should have been closed");

		//smoothed values are written to the m ordinate; the raw elevation
		//is left on the z ordinate
		assertSmoothed(dataSource, EDGE1,
				new double[] {100, 98.5, 97.5, 97.5, 92, 91, 90},
				new double[] {100,   98,   96,   99, 92, 91, 90});

		assertSmoothed(dataSource, EDGE2,
				new double[] {95, 94.5, 94.5, 93, 90.5, 90.5, 90},
				new double[] {95,   94,   96, 93,   89,   91, 90});

		assertSmoothed(dataSource, EDGE3,
				new double[] {90, 88, 86, 83.25, 83.25, 82.5, 82.5},
				new double[] {90, 88, 86,    78,    84,   82,   80});

		assertSmoothed(dataSource, EDGE5,
				new double[] {92, 91, 91, 87, 85, 83, 82.5},
				new double[] {92, 90, 93, 87, 85, 83,   80});

		//the entire inverted sink edge flattens out to the smoothed node value
		assertSmoothed(dataSource, EDGE4,
				new double[] {82.5, 82.5, 82.5, 82.5, 82.5, 82.5, 82.5},
				new double[] {  80,   79,   81,   83,   84,   82,   85});
	}

	/**
	 * Asserts the smoothed (m) and raw (z) values of the geometry written back
	 * for the given flowpath, and that the smoothed values never increase in
	 * the downstream direction.
	 */
	private void assertSmoothed(MockZSmootherDataSource dataSource, UUID edgeId,
			double[] expectedM, double[] expectedZ) {

		LineString ls = dataSource.getResult(edgeId);
		Assertions.assertNotNull(ls, "no geometry written back for edge " + edgeId);

		CoordinateSequence cs = ls.getCoordinateSequence();
		Assertions.assertEquals(expectedM.length, cs.size(), "vertex count for edge " + edgeId);

		for (int i = 0; i < expectedM.length; i++) {
			Assertions.assertEquals(expectedM[i], cs.getM(i), 0.00001,
					"smoothed elevation of vertex " + i + " of edge " + edgeId);
			Assertions.assertEquals(expectedZ[i], cs.getZ(i), 0.00001,
					"raw elevation of vertex " + i + " of edge " + edgeId);
		}

		for (int i = 1; i < cs.size(); i++) {
			Assertions.assertTrue(cs.getM(i) <= cs.getM(i - 1),
					"elevation increases downstream between vertex " + (i - 1)
					+ " and " + i + " of edge " + edgeId);
		}
	}

}
