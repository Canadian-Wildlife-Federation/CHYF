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
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.LineString;

/**
 * Shared helpers for the z smoothing engine tests: node/edge ids, running
 * the smoother against a {@link MockZSmootherDataSource} and asserting the
 * geometries it writes back.
 *
 * @author Emily
 *
 */
abstract class ZSmootherTestBase {

	protected static UUID node(int i) {
		return UUID.fromString("00000000-0000-0000-0000-00000000000" + i);
	}

	protected static UUID edge(int i) {
		return UUID.fromString("00000000-0000-0000-0000-0000000000a" + i);
	}

	/**
	 * Runs the smoother against the data source and asserts that its block
	 * was processed.
	 */
	protected void smooth(MockZSmootherDataSource dataSource) {
		ZSmootherJob job = new ZSmootherJob(() -> dataSource);
		job.run();

		Assertions.assertTrue(dataSource.isBlockFinished(MockZSmootherDataSource.BLOCK_ID),
				"the block should have been marked as finished");
	}

	/**
	 * Asserts the smoothed (m) and raw (z) values of the geometry written back
	 * for the given flowpath, and that the smoothed values never increase in
	 * the downstream direction.
	 */
	protected void assertSmoothed(MockZSmootherDataSource dataSource, UUID edgeId,
			double[] expectedM, double[] expectedZ) {

		CoordinateSequence cs = getResult(dataSource, edgeId);
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

	/**
	 * @return the coordinates of the geometry written back by the engine for
	 * the given flowpath
	 */
	protected CoordinateSequence getResult(MockZSmootherDataSource dataSource, UUID edgeId) {
		LineString ls = dataSource.getResult(edgeId);
		Assertions.assertNotNull(ls, "no geometry written back for edge " + edgeId);
		return ls.getCoordinateSequence();
	}

}
