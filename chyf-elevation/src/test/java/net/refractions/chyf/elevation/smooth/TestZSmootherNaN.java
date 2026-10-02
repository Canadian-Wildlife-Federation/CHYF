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

import net.refractions.chyf.elevation.Utils;

/**
 * Tests how the z smoothing engine deals with NaN raw elevations, which is
 * what a flowpath that was never given a raw elevation (a 2d geometry) has
 * on every vertex.
 *
 * A NaN elevation is treated the same way as a no data (-9999) elevation;
 * it must never produce a NaN or zero smoothed elevation, and it must never
 * change the smoothed elevation of a node or vertex that has valid data.
 *
 * The raw (z) value written back for a NaN vertex is only checked where
 * the flowpath could not be smoothed, in which case it is set to no data.
 *
 * @author Emily
 *
 */
class TestZSmootherNaN extends ZSmootherTestBase {

	private static final double NO_DATA = Utils.NO_DATA;
	private static final double NaN = Double.NaN;

	//nodes
	private static final UUID NODE1 = node(1);
	private static final UUID NODE2 = node(2);
	private static final UUID NODE3 = node(3);
	private static final UUID NODE4 = node(4);

	//edges
	private static final UUID EDGE1 = edge(1);
	private static final UUID EDGE2 = edge(2);
	private static final UUID EDGE3 = edge(3);

	/**
	 * n1 (NaN) -> n2 (100) -> n3 (90) -> n4 (80)
	 *
	 * The headwater node is NaN so e1 cannot be smoothed.  Everything
	 * downstream of n2 has valid data that already decreases downstream so
	 * it is left as is.
	 */
	@Test
	void testNaNHeadwaterNode() throws Exception {

		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();

		dataSource.addFlowpath(EDGE1, NODE1, NODE2, new double[][] {
			{ 0, 0, NaN},
			{ 5, 0, 105},
			{10, 0, 100}
		});
		dataSource.addFlowpath(EDGE2, NODE2, NODE3, new double[][] {
			{10, 0, 100},
			{15, 0,  95},
			{20, 0,  90}
		});
		dataSource.addFlowpath(EDGE3, NODE3, NODE4, new double[][] {
			{20, 0, 90},
			{25, 0, 85},
			{30, 0, 80}
		});

		smooth(dataSource);

		assertSmoothed(dataSource, EDGE1,
				new double[] {NO_DATA, NO_DATA, NO_DATA},
				new double[] {NO_DATA,     105,     100});

		assertSmoothed(dataSource, EDGE2,
				new double[] {100, 95, 90},
				new double[] {100, 95, 90});

		assertSmoothed(dataSource, EDGE3,
				new double[] {90, 85, 80},
				new double[] {90, 85, 80});
	}

	/**
	 * n1 (110) -> n2 (100) -> n3 (NaN)
	 *
	 * The sink node is NaN so e2 cannot be smoothed.  Everything upstream of
	 * n2 has valid data that already decreases downstream so it is left as
	 * is.
	 */
	@Test
	void testNaNSinkNode() throws Exception {

		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();

		dataSource.addFlowpath(EDGE1, NODE1, NODE2, new double[][] {
			{ 0, 0, 110},
			{ 5, 0, 105},
			{10, 0, 100}
		});
		dataSource.addFlowpath(EDGE2, NODE2, NODE3, new double[][] {
			{10, 0, 100},
			{15, 0,  95},
			{20, 0, NaN}
		});

		smooth(dataSource);

		assertSmoothed(dataSource, EDGE1,
				new double[] {110, 105, 100},
				new double[] {110, 105, 100});

		assertSmoothed(dataSource, EDGE2,
				new double[] {NO_DATA, NO_DATA, NO_DATA},
				new double[] {    100,      95, NO_DATA});
	}

	/**
	 * n1 (110) -> n2 (100) -> n3 (NaN) -> n4 (80)
	 *
	 * e2 is a 2d flowpath (every vertex is NaN) that sits between two
	 * flowpaths with valid data.  n3 is first seen on e2, so it has no
	 * elevation and is filled from the nodes on either side of it:
	 *
	 * Upstream pass (max looking downstream):
	 * n1=110, n2=100, n3=80, n4=80
	 *
	 * Downstream pass (min looking upstream):
	 * n1=110, n2=100, n3=100, n4=80
	 *
	 * Smoothed (average of the two):
	 * n1=110, n2=100, n3=90, n4=80
	 *
	 * The internal vertex of e2 is filled with a value between its two
	 * nodes, and the flowpaths on either side are left as is.
	 */
	@Test
	void testNaNFlowpathBetweenValidFlowpaths() throws Exception {

		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();

		dataSource.addFlowpath(EDGE1, NODE1, NODE2, new double[][] {
			{ 0, 0, 110},
			{ 5, 0, 105},
			{10, 0, 100}
		});
		dataSource.addFlowpath(EDGE2, NODE2, NODE3, new double[][] {
			{10, 0, NaN},
			{15, 0, NaN},
			{20, 0, NaN}
		});
		dataSource.addFlowpath(EDGE3, NODE3, NODE4, new double[][] {
			{20, 0, 90},
			{25, 0, 85},
			{30, 0, 80}
		});

		smooth(dataSource);

		assertSmoothed(dataSource, EDGE1,
				new double[] {110, 105, 100},
				new double[] {110, 105, 100});

		assertSmoothedM(dataSource, EDGE2, new double[] {100, 95, 90});

		assertSmoothed(dataSource, EDGE3,
				new double[] {90, 85, 80},
				new double[] {90, 85, 80});
	}

	/**
	 * n1 (NaN) -> n2 (NaN)
	 *
	 * A 2d flowpath (every vertex is NaN) that is not connected to anything
	 * with valid data.  There is nothing to smooth against so both the
	 * smoothed and the raw elevation of every vertex are set to no data.
	 */
	@Test
	void testNaNFlowpathWithNoValidData() throws Exception {

		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();

		dataSource.addFlowpath(EDGE1, NODE1, NODE2, new double[][] {
			{ 0, 0, NaN},
			{10, 0, NaN},
			{20, 0, NaN}
		});

		smooth(dataSource);

		assertSmoothed(dataSource, EDGE1,
				new double[] {NO_DATA, NO_DATA, NO_DATA},
				new double[] {NO_DATA, NO_DATA, NO_DATA});
	}

	/**
	 * n1 (100) -> n2 (80)
	 *
	 * Several internal vertices are NaN, including two in a row.  The
	 * vertices with valid data already decrease downstream so they are left
	 * as is; each NaN vertex is filled with the average of the valid
	 * vertices on either side of it.
	 */
	@Test
	void testNaNInternalVertices() throws Exception {

		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();

		dataSource.addFlowpath(EDGE1, NODE1, NODE2, new double[][] {
			{ 0, 0, 100},
			{10, 0,  98},
			{20, 0, NaN},
			{30, 0,  94},
			{40, 0, NaN},
			{50, 0, NaN},
			{60, 0,  84},
			{70, 0,  80}
		});

		smooth(dataSource);

		assertSmoothedM(dataSource, EDGE1, new double[] {100, 98, 96, 94, 89, 89, 84, 80});
	}

	/**
	 * Asserts the smoothed (m) values of the geometry written back for the
	 * given flowpath, and that they never increase in the downstream
	 * direction.  The raw (z) values are not checked.
	 */
	private void assertSmoothedM(MockZSmootherDataSource dataSource, UUID edgeId, double[] expectedM) {

		CoordinateSequence cs = getResult(dataSource, edgeId);
		Assertions.assertEquals(expectedM.length, cs.size(), "vertex count for edge " + edgeId);

		for (int i = 0; i < expectedM.length; i++) {
			Assertions.assertEquals(expectedM[i], cs.getM(i), 0.00001,
					"smoothed elevation of vertex " + i + " of edge " + edgeId);
		}

		for (int i = 1; i < cs.size(); i++) {
			Assertions.assertTrue(cs.getM(i) <= cs.getM(i - 1),
					"elevation increases downstream between vertex " + (i - 1)
					+ " and " + i + " of edge " + edgeId);
		}
	}

}
