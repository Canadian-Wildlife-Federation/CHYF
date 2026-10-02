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

import org.junit.jupiter.api.Test;

/**
 * Tests how the z smoothing engine deals with negative raw elevations
 * (for example flowpaths that end just below sea level at the coast).
 *
 * A negative elevation is a valid elevation; it is smoothed the same way
 * as any other value and is never raised to a minimum of zero.
 *
 * @author Emily
 *
 */
class TestZSmootherNegativeZ extends ZSmootherTestBase {

	//nodes
	private static final UUID NODE1 = node(1);
	private static final UUID NODE2 = node(2);
	private static final UUID NODE3 = node(3);

	//edges
	private static final UUID EDGE1 = edge(1);
	private static final UUID EDGE2 = edge(2);

	/**
	 * n1 (0.35) -> n2 (0.12) -> n3 (-0.04)
	 *
	 * The outlet (sink) node is just below zero.  The raw elevations already
	 * decrease downstream so nothing needs smoothing and the outlet keeps its
	 * raw elevation of -0.04 rather than being raised to 0.
	 */
	@Test
	void testNegativeOutletNode() throws Exception {

		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();

		dataSource.addFlowpath(EDGE1, NODE1, NODE2, new double[][] {
			{ 0, 0, 0.35},
			{10, 0, 0.21},
			{20, 0, 0.12}
		});

		dataSource.addFlowpath(EDGE2, NODE2, NODE3, new double[][] {
			{20, 0,  0.12},
			{30, 0,  0.05},
			{40, 0, -0.04}
		});

		smooth(dataSource);

		assertSmoothed(dataSource, EDGE1,
				new double[] {0.35, 0.21, 0.12},
				new double[] {0.35, 0.21, 0.12});

		assertSmoothed(dataSource, EDGE2,
				new double[] {0.12, 0.05, -0.04},
				new double[] {0.12, 0.05, -0.04});
	}

	/**
	 * n1 (0.20) -> n2 (-0.04)
	 *
	 * The outlet node is just below zero and vertex 2 (-0.10) dips below it.
	 * The dip is clamped to the outlet elevation (-0.04), not to zero, and
	 * averaged with the highest elevation downstream of it (0.02).
	 */
	@Test
	void testNegativeInternalVertexBelowNegativeOutletNode() throws Exception {

		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();

		dataSource.addFlowpath(EDGE1, NODE1, NODE2, new double[][] {
			{ 0, 0,  0.20},
			{10, 0,  0.10},
			{20, 0, -0.10},
			{30, 0,  0.02},
			{40, 0, -0.04}
		});

		smooth(dataSource);

		assertSmoothed(dataSource, EDGE1,
				new double[] {0.20, 0.10, -0.01, -0.01, -0.04},
				new double[] {0.20, 0.10, -0.10,  0.02, -0.04});
	}

	/**
	 * n1 (-0.5) -> n2 (-2.0) -> n3 (-1.0)
	 *
	 * Every elevation is below zero and the n2/n3 node pair is inverted
	 * (the downstream node n3 sits 1m above the upstream node n2).
	 *
	 * Upstream pass (max looking downstream):
	 * n1=-0.5, n2=-1.0, n3=-1.0
	 *
	 * Downstream pass (min looking upstream):
	 * n1=-0.5, n2=-2.0, n3=-2.0
	 *
	 * Smoothed (average of the two):
	 * n1=-0.5, n2=-1.5, n3=-1.5
	 */
	@Test
	void testAllNegativeElevations() throws Exception {

		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();

		//e1: n1 (-0.5) -> n2 (-2.0)
		dataSource.addFlowpath(EDGE1, NODE1, NODE2, new double[][] {
			{ 0, 0, -0.5},
			{10, 0, -0.8},
			{20, 0, -1.2},
			{30, 0, -2.0}
		});

		//e2: n2 (-2.0) -> n3 (-1.0); the inverted sink edge
		dataSource.addFlowpath(EDGE2, NODE2, NODE3, new double[][] {
			{30, 0, -2.0},
			{40, 0, -1.8},
			{50, 0, -1.4},
			{60, 0, -1.0}
		});

		smooth(dataSource);

		assertSmoothed(dataSource, EDGE1,
				new double[] {-0.5, -0.8, -1.2, -1.5},
				new double[] {-0.5, -0.8, -1.2, -2.0});

		//the entire inverted sink edge flattens out to the smoothed node value
		assertSmoothed(dataSource, EDGE2,
				new double[] {-1.5, -1.5, -1.5, -1.5},
				new double[] {-2.0, -1.8, -1.4, -1.0});
	}

	/**
	 * n1 (0.30) -> n2 (-0.04) -> n3 (0.04)
	 *
	 * A negative node can legitimately end up with a smoothed elevation of
	 * exactly zero when the network rises again downstream of it.  Here n2
	 * is at -0.04 and the downstream node n3 is at 0.04:
	 *
	 * Upstream pass (max looking downstream):
	 * n1=0.30, n2=0.04, n3=0.04
	 *
	 * Downstream pass (min looking upstream):
	 * n1=0.30, n2=-0.04, n3=-0.04
	 *
	 * Smoothed (average of the two):
	 * n1=0.30, n2=0, n3=0
	 *
	 * The zero is the average of -0.04 and 0.04, not a minimum being applied.
	 */
	@Test
	void testNegativeNodeAveragesToZero() throws Exception {

		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();

		dataSource.addFlowpath(EDGE1, NODE1, NODE2, new double[][] {
			{ 0, 0,  0.30},
			{10, 0,  0.15},
			{20, 0, -0.04}
		});

		dataSource.addFlowpath(EDGE2, NODE2, NODE3, new double[][] {
			{20, 0, -0.04},
			{30, 0,  0.01},
			{40, 0,  0.04}
		});

		smooth(dataSource);

		assertSmoothed(dataSource, EDGE1,
				new double[] {0.30, 0.15,     0},
				new double[] {0.30, 0.15, -0.04});

		assertSmoothed(dataSource, EDGE2,
				new double[] {    0,    0,    0},
				new double[] {-0.04, 0.01, 0.04});
	}

}
