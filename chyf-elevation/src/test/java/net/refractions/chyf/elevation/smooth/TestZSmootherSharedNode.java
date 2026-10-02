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

/**
 * Tests that a node has a single smoothed elevation: every flowpath that
 * touches a node must carry that node's smoothed elevation on the shared
 * vertex, whether the node is the first or the last vertex of the flowpath.
 *
 * The raw elevations are taken from the flowpaths around 64.8575W, 47.7348N:
 *
 * <pre>
 *   n1 (0.4719) --e1--\
 *                      n3 (-0.0411) --e3--> n4 (-0.2891) --e4--> n5 (0.0411)
 *   n2 (0.3515) --e2--/
 * </pre>
 *
 * e1, e2 and e3 are the real flowpaths.  e4 and n5 are assumed; they stand
 * in for whatever sits downstream of n4 in the real network, which must
 * rise to 0.0411 to produce the smoothed elevations that were observed.
 *
 * @author Emily
 *
 */
class TestZSmootherSharedNode extends ZSmootherTestBase {

	//nodes
	private static final UUID NODE1 = node(1);
	private static final UUID NODE2 = node(2);
	private static final UUID NODE3 = node(3);
	private static final UUID NODE4 = node(4);
	private static final UUID NODE5 = node(5);

	//edges
	private static final UUID EDGE1 = edge(1);
	private static final UUID EDGE2 = edge(2);
	private static final UUID EDGE3 = edge(3);
	private static final UUID EDGE4 = edge(4);

	/**
	 * Raw node elevations:
	 * n1=0.4719, n2=0.3515, n3=-0.0411, n4=-0.2891, n5=0.0411
	 *
	 * Upstream pass (max looking downstream):
	 * n1=0.4719, n2=0.3515, n3=0.0411, n4=0.0411, n5=0.0411
	 *
	 * Downstream pass (min looking upstream):
	 * n1=0.4719, n2=0.3515, n3=-0.0411, n4=-0.2891, n5=-0.2891
	 *
	 * Smoothed (average of the two):
	 * n1=0.4719, n2=0.3515, n3=0, n4=-0.124, n5=-0.124
	 *
	 * n3 is the last vertex of e1 and e2 and the first vertex of e3, so all
	 * three must have a smoothed elevation of 0 on that vertex.
	 */
	@Test
	void testNodeHasSameSmoothedElevationOnEveryFlowpath() throws Exception {

		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();

		//e1: n1 (0.4719) -> n3 (-0.0411); vertices 1 to 7 rise above the
		//upstream node
		dataSource.addFlowpath(EDGE1, NODE1, NODE3, new double[][] {
			{  0, 0,  0.4719},
			{ 10, 0,  0.5253},
			{ 20, 0,  0.5814},
			{ 30, 0,  0.6177},
			{ 40, 0,  0.6494},
			{ 50, 0,  0.6764},
			{ 60, 0,  0.6671},
			{ 70, 0,  0.6556},
			{ 80, 0,  0.4421},
			{ 90, 0,  0.412},
			{100, 0,  0.37},
			{110, 0,  0.1272},
			{120, 0,  0.0502},
			{130, 0, -0.0411}
		});

		//e2: n2 (0.3515) -> n3 (-0.0411)
		dataSource.addFlowpath(EDGE2, NODE2, NODE3, new double[][] {
			{130, 50,  0.3515},
			{130,  0, -0.0411}
		});

		//e3: n3 (-0.0411) -> n4 (-0.2891)
		dataSource.addFlowpath(EDGE3, NODE3, NODE4, new double[][] {
			{130, 0, -0.0411},
			{140, 0, -0.1086},
			{150, 0, -0.2891}
		});

		//e4: n4 (-0.2891) -> n5 (0.0411); the sink edge, and an inverted one
		dataSource.addFlowpath(EDGE4, NODE4, NODE5, new double[][] {
			{150, 0, -0.2891},
			{160, 0,  0.0411}
		});

		smooth(dataSource);

		assertSmoothed(dataSource, EDGE1,
				new double[] {0.4719, 0.4719, 0.4719, 0.4719, 0.4719, 0.4719, 0.4719, 0.4719,
						0.4421, 0.412, 0.37, 0.1272, 0.0502, 0},
				new double[] {0.4719, 0.5253, 0.5814, 0.6177, 0.6494, 0.6764, 0.6671, 0.6556,
						0.4421, 0.412, 0.37, 0.1272, 0.0502, -0.0411});

		assertSmoothed(dataSource, EDGE2,
				new double[] {0.3515,       0},
				new double[] {0.3515, -0.0411});

		assertSmoothed(dataSource, EDGE3,
				new double[] {      0, -0.1086,  -0.124},
				new double[] {-0.0411, -0.1086, -0.2891});

		assertSmoothed(dataSource, EDGE4,
				new double[] { -0.124, -0.124},
				new double[] {-0.2891, 0.0411});

		//n3: the end of e1 and e2, and the start of e3
		assertSameSmoothedElevation(dataSource, EDGE1, 13, EDGE2, 1);
		assertSameSmoothedElevation(dataSource, EDGE1, 13, EDGE3, 0);

		//n4: the end of e3 and the start of e4
		assertSameSmoothedElevation(dataSource, EDGE3, 2, EDGE4, 0);
	}

	/**
	 * Asserts that two flowpaths have the same smoothed (m) value on the
	 * vertex they share.
	 */
	private void assertSameSmoothedElevation(MockZSmootherDataSource dataSource,
			UUID edgeId1, int vertex1, UUID edgeId2, int vertex2) {

		double m1 = getResult(dataSource, edgeId1).getM(vertex1);
		double m2 = getResult(dataSource, edgeId2).getM(vertex2);

		Assertions.assertEquals(m1, m2, 0.00001,
				"smoothed elevation of the node shared by vertex " + vertex1 + " of edge " + edgeId1
				+ " and vertex " + vertex2 + " of edge " + edgeId2);
	}

}
