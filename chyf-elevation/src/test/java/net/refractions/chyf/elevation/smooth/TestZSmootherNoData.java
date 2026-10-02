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
 * Tests how the z smoothing engine deals with no data (-9999) raw
 * elevations, both on the nodes and on the internal vertices of a flowpath.
 *
 * A no data value carries no elevation information, so it must never
 * change the smoothed elevation of a node or vertex that has valid data,
 * and it must never be averaged with a real elevation (which produces
 * values around -4950).
 *
 * Where the smoothed value of the no data node/vertex itself depends on
 * how the engine chooses to fill it, the tests only require the value to
 * be either no data or within the range of the valid elevations around it.
 *
 * @author Emily
 *
 */
class TestZSmootherNoData extends ZSmootherTestBase {

	private static final double NO_DATA = Utils.NO_DATA;

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
	 * n1 (no data) -> n2 (100) -> n3 (90) -> n4 (80)
	 *
	 * The headwater node has no data.  Everything downstream of n2 has valid
	 * data that already decreases downstream so it should be left as is.
	 */
	@Test
	void testNoDataHeadwaterNode() throws Exception {

		MockZSmootherDataSource dataSource = chain(
				new double[] {NO_DATA, 100, 90, 80},
				new double[] {105, 95, 85});

		smooth(dataSource);

		assertNoDataOrWithin(dataSource, EDGE1, 100, 105);

		assertSmoothed(dataSource, EDGE2,
				new double[] {100, 95, 90},
				new double[] {100, 95, 90});

		assertSmoothed(dataSource, EDGE3,
				new double[] {90, 85, 80},
				new double[] {90, 85, 80});
	}

	/**
	 * n1 (110) -> n2 (no data) -> n3 (90) -> n4 (80)
	 *
	 * A node in the middle of the network has no data.  It has valid data
	 * both upstream and downstream so it is filled with a value between the
	 * vertices on either side of it.
	 */
	@Test
	void testNoDataMiddleNode() throws Exception {

		MockZSmootherDataSource dataSource = chain(
				new double[] {110, NO_DATA, 90, 80},
				new double[] {105, 95, 85});

		smooth(dataSource);

		//e1: n1 -> n2; the last vertex is the no data node
		assertSmoothedVertex(dataSource, EDGE1, 0, 110, 110);
		assertSmoothedVertex(dataSource, EDGE1, 1, 105, 105);
		assertSmoothedVertexWithin(dataSource, EDGE1, 2, 95, 105, NO_DATA);

		//e2: n2 -> n3; the first vertex is the no data node
		assertSmoothedVertexWithin(dataSource, EDGE2, 0, 95, 105, NO_DATA);
		assertSmoothedVertex(dataSource, EDGE2, 1, 95, 95);
		assertSmoothedVertex(dataSource, EDGE2, 2, 90, 90);

		assertSmoothed(dataSource, EDGE3,
				new double[] {90, 85, 80},
				new double[] {90, 85, 80});

		assertNeverIncreases(dataSource, EDGE1);
		assertNeverIncreases(dataSource, EDGE2);
	}

	/**
	 * n1 (110) -> n2 (100) -> n3 (90) -> n4 (no data)
	 *
	 * The sink node has no data.  Everything upstream of n3 has valid data
	 * that already decreases downstream so it should be left as is.
	 */
	@Test
	void testNoDataSinkNode() throws Exception {

		MockZSmootherDataSource dataSource = chain(
				new double[] {110, 100, 90, NO_DATA},
				new double[] {105, 95, 85});

		smooth(dataSource);

		assertSmoothed(dataSource, EDGE1,
				new double[] {110, 105, 100},
				new double[] {110, 105, 100});

		assertSmoothed(dataSource, EDGE2,
				new double[] {100, 95, 90},
				new double[] {100, 95, 90});

		assertNoDataOrWithin(dataSource, EDGE3, 85, 90);
	}

	/**
	 * <pre>
	 *   n1 (no data) --e1--\
	 *                       n3 (90) --e3--> n4 (80)
	 *   n2 (100) ------e2--/
	 * </pre>
	 *
	 * One of the two headwater nodes flowing into a confluence has no data.
	 * The other tributary and everything downstream of the confluence has
	 * valid data that already decreases downstream so it should be left as
	 * is.  The result must not depend on the order the flowpaths are read in.
	 */
	@Test
	void testNoDataHeadwaterNodeAtConfluence() throws Exception {

		for (boolean noDataFirst : new boolean[] {true, false}) {

			MockZSmootherDataSource dataSource = new MockZSmootherDataSource();

			double[][] e1 = new double[][] {{0,  0, NO_DATA}, {5, 0, 95}, {10, 0, 90}};
			double[][] e2 = new double[][] {{0, 10,     100}, {5, 5, 95}, {10, 0, 90}};

			if (noDataFirst) {
				dataSource.addFlowpath(EDGE1, NODE1, NODE3, e1);
				dataSource.addFlowpath(EDGE2, NODE2, NODE3, e2);
			} else {
				dataSource.addFlowpath(EDGE2, NODE2, NODE3, e2);
				dataSource.addFlowpath(EDGE1, NODE1, NODE3, e1);
			}
			dataSource.addFlowpath(EDGE3, NODE3, NODE4, new double[][] {
				{10, 0, 90},
				{15, 0, 85},
				{20, 0, 80}
			});

			smooth(dataSource);

			assertNoDataOrWithin(dataSource, EDGE1, 90, 95);

			assertSmoothed(dataSource, EDGE2,
					new double[] {100, 95, 90},
					new double[] {100, 95, 90});

			assertSmoothed(dataSource, EDGE3,
					new double[] {90, 85, 80},
					new double[] {90, 85, 80});
		}
	}

	/**
	 * n1 (no data) -> n2 (no data)
	 *
	 * Neither node has data so there is nothing to smooth against; the
	 * smoothed value of every vertex is no data and the raw elevations are
	 * left as is.
	 */
	@Test
	void testNoDataOnAllNodes() throws Exception {

		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();
		dataSource.addFlowpath(EDGE1, NODE1, NODE2, new double[][] {
			{ 0, 0, NO_DATA},
			{10, 0,      95},
			{20, 0,      93},
			{30, 0, NO_DATA}
		});

		smooth(dataSource);

		assertSmoothed(dataSource, EDGE1,
				new double[] {NO_DATA, NO_DATA, NO_DATA, NO_DATA},
				new double[] {NO_DATA,      95,      93, NO_DATA});
	}

	/**
	 * n1 (110) -> n2 (100) -> n3 (90) -> n4 (80)
	 *
	 * The only internal vertex of e2 has no data.  It is filled with a value
	 * between the two nodes of the flowpath and nothing else changes.
	 */
	@Test
	void testNoDataInternalVertex() throws Exception {

		MockZSmootherDataSource dataSource = chain(
				new double[] {110, 100, 90, 80},
				new double[] {105, NO_DATA, 85});

		smooth(dataSource);

		assertSmoothed(dataSource, EDGE1,
				new double[] {110, 105, 100},
				new double[] {110, 105, 100});

		assertSmoothedVertex(dataSource, EDGE2, 0, 100, 100);
		assertSmoothedVertexWithin(dataSource, EDGE2, 1, 90, 100, NO_DATA);
		assertSmoothedVertex(dataSource, EDGE2, 2, 90, 90);
		assertNeverIncreases(dataSource, EDGE2);

		assertSmoothed(dataSource, EDGE3,
				new double[] {90, 85, 80},
				new double[] {90, 85, 80});
	}

	/**
	 * n1 (100) -> n2 (80)
	 *
	 * Several internal vertices have no data, including two in a row.  The
	 * vertices with valid data already decrease downstream so they should be
	 * left as is; each no data vertex is filled with a value between the
	 * valid vertices on either side of it.
	 */
	@Test
	void testNoDataInternalVerticesDoNotAffectValidVertices() throws Exception {

		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();
		dataSource.addFlowpath(EDGE1, NODE1, NODE2, new double[][] {
			{ 0, 0,     100},
			{10, 0,      98},
			{20, 0, NO_DATA},
			{30, 0,      94},
			{40, 0, NO_DATA},
			{50, 0, NO_DATA},
			{60, 0,      84},
			{70, 0,      80}
		});

		smooth(dataSource);

		assertSmoothedVertex(dataSource, EDGE1, 0, 100, 100);
		assertSmoothedVertex(dataSource, EDGE1, 1, 98, 98);
		assertSmoothedVertexWithin(dataSource, EDGE1, 2, 94, 98, NO_DATA);
		assertSmoothedVertex(dataSource, EDGE1, 3, 94, 94);
		assertSmoothedVertexWithin(dataSource, EDGE1, 4, 84, 94, NO_DATA);
		assertSmoothedVertexWithin(dataSource, EDGE1, 5, 84, 94, NO_DATA);
		assertSmoothedVertex(dataSource, EDGE1, 6, 84, 84);
		assertSmoothedVertex(dataSource, EDGE1, 7, 80, 80);

		assertNeverIncreases(dataSource, EDGE1);
	}

	/**
	 * Builds a chain of flowpaths n1 -> n2 -> n3 ... where flowpath i runs
	 * from node i to node i+1 and has a single internal vertex.
	 *
	 * @param nodeZ raw elevation of each node, upstream to downstream
	 * @param vertexZ raw elevation of the internal vertex of each flowpath
	 */
	private MockZSmootherDataSource chain(double[] nodeZ, double[] vertexZ) {
		MockZSmootherDataSource dataSource = new MockZSmootherDataSource();
		for (int i = 0; i < vertexZ.length; i++) {
			dataSource.addFlowpath(edge(i + 1), node(i + 1), node(i + 2), new double[][] {
				{i * 10,      0, nodeZ[i]},
				{i * 10 + 5,  0, vertexZ[i]},
				{i * 10 + 10, 0, nodeZ[i + 1]}
			});
		}
		return dataSource;
	}

	/**
	 * Asserts the smoothed (m) and raw (z) values of a single vertex of the
	 * geometry written back for the given flowpath.
	 */
	private void assertSmoothedVertex(MockZSmootherDataSource dataSource, UUID edgeId, int vertex,
			double expectedM, double expectedZ) {

		CoordinateSequence cs = getResult(dataSource, edgeId);

		Assertions.assertEquals(expectedM, cs.getM(vertex), 0.00001,
				"smoothed elevation of vertex " + vertex + " of edge " + edgeId);
		Assertions.assertEquals(expectedZ, cs.getZ(vertex), 0.00001,
				"raw elevation of vertex " + vertex + " of edge " + edgeId);
	}

	/**
	 * Asserts that a vertex has been filled with a smoothed (m) value within
	 * the given range and that its raw (z) value is unchanged.
	 */
	private void assertSmoothedVertexWithin(MockZSmootherDataSource dataSource, UUID edgeId, int vertex,
			double minM, double maxM, double expectedZ) {

		CoordinateSequence cs = getResult(dataSource, edgeId);

		double m = cs.getM(vertex);
		Assertions.assertTrue(m >= minM && m <= maxM,
				"smoothed elevation of vertex " + vertex + " of edge " + edgeId
				+ " should be between " + minM + " and " + maxM + " but was " + m);
		Assertions.assertEquals(expectedZ, cs.getZ(vertex), 0.00001,
				"raw elevation of vertex " + vertex + " of edge " + edgeId);
	}

	/**
	 * Asserts that the smoothed (m) value of every vertex of the given
	 * flowpath is either no data or within the given range, and that the
	 * vertices that do have a smoothed value never increase downstream.
	 */
	private void assertNoDataOrWithin(MockZSmootherDataSource dataSource, UUID edgeId,
			double minM, double maxM) {

		CoordinateSequence cs = getResult(dataSource, edgeId);

		for (int i = 0; i < cs.size(); i++) {
			double m = cs.getM(i);
			Assertions.assertTrue(m == NO_DATA || (m >= minM && m <= maxM),
					"smoothed elevation of vertex " + i + " of edge " + edgeId
					+ " should be no data or between " + minM + " and " + maxM + " but was " + m);
		}
		assertNeverIncreases(dataSource, edgeId);
	}

	/**
	 * Asserts that the smoothed (m) values of the given flowpath never
	 * increase in the downstream direction; vertices with a no data smoothed
	 * value are skipped.
	 */
	private void assertNeverIncreases(MockZSmootherDataSource dataSource, UUID edgeId) {

		CoordinateSequence cs = getResult(dataSource, edgeId);

		double last = Double.MAX_VALUE;
		for (int i = 0; i < cs.size(); i++) {
			double m = cs.getM(i);
			if (m == NO_DATA) continue;
			Assertions.assertTrue(m <= last,
					"elevation increases downstream at vertex " + i + " of edge " + edgeId);
			last = m;
		}
	}

}
