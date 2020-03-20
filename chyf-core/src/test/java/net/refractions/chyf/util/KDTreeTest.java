/*******************************************************************************
 * Copyright 2020 Government of Canada
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
 *******************************************************************************/
package net.refractions.chyf.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;

class KDTreeTest {

	private static List<Coordinate> items;
	private static KDTree<Coordinate> index;
	
	@BeforeAll
	static void setUp() throws Exception {
		items = new ArrayList<Coordinate>();
		for(double x = 0; x < 100; x++) {
			for(double y = 0; y < 100; y++) {
				items.add(new Coordinate(x,y));
			}
		}
		
		index = new KDTree<Coordinate>(items, c -> c);
	}

	static Stream<Coordinate> getItems() {
		return items.stream();
	}
	
	@ParameterizedTest
	@MethodSource("getItems")
	void testQueryCoordinateInt(Coordinate testCoord) {
		int nResults = 1;
		List<Coordinate> results = index.query(testCoord, nResults);
		assertPostConditions(results, testCoord, nResults, null, null);
		assertEquals(1, results.size());
		assertTrue(testCoord.equals(results.get(0)));
	}

	@ParameterizedTest
	@MethodSource("getItems")
	void testQueryCoordinateIntInteger(Coordinate testCoord) {
		int nResults = 100;
		Double maxDistance = 2.0;
		List<Coordinate> results = index.query(testCoord, nResults, maxDistance);
		assertTrue(results.size() > 0);
		assertPostConditions(results, testCoord, nResults, null, null);
	}

	@ParameterizedTest
	@MethodSource("getItems")
	void testQueryCoordinateIntIntegerPredicateOfT(Coordinate testCoord) {
		int nResults = 100;
		Double maxDistance = 10.0;
		Predicate<Coordinate> filter = c -> c.getX() == c.getY();
		List<Coordinate> results = index.query(testCoord, nResults, maxDistance, filter);
		for(Coordinate resultCoord : results) {
			assertTrue(testCoord.distance(resultCoord) < maxDistance);
			assertTrue(filter.test(resultCoord), "Result coordinate did not pass filter: " + resultCoord);
		}
	}

	void assertPostConditions(List<Coordinate> results, Coordinate testCoord, int nResults, Double maxDistance, Predicate<Coordinate> filter) {
		assertTrue(results.size() <= nResults);
		assertResultsWithinMaxDistance(testCoord, results, maxDistance);
		assertResultsPassFilter(results, filter);
		assertResultsInOrder(testCoord, results);
	}
	
	void assertResultsWithinMaxDistance(Coordinate testCoord, List<Coordinate> results, Double maxDistance) {
		if(maxDistance == null) return;
		for(Coordinate resultCoord : results) {
			assertTrue(testCoord.distance(resultCoord) < maxDistance);
		}		
	}

	void assertResultsPassFilter(List<Coordinate> results, Predicate<Coordinate> filter) {
		if(filter == null) return;
		for(Coordinate resultCoord : results) {
			assertTrue(filter.test(resultCoord), "Result coordinate did not pass filter: " + resultCoord);
		}		
	}
	
	void assertResultsInOrder(Coordinate testCoord, List<Coordinate> results) {
		if(results.isEmpty()) return;
		double dist = 0.0;
		for(Coordinate resultCoord : results) {
			double newDist = testCoord.distance(resultCoord);
			assertTrue(newDist >= dist);
			dist = newDist;
		}
	}
}
