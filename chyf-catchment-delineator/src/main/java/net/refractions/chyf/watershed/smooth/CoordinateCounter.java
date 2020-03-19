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

package net.refractions.chyf.watershed.smooth;

import java.util.Map;
import java.util.TreeMap;

import org.locationtech.jts.geom.Coordinate;

/**
 * Counts the number of times each value occurs in a collection of
 * {@link Coordinate}s.
 * 
 * @author Martin Davis
 */
class CoordinateCounter {
	public class Counter {
		int count;

		public Counter() {
			count = 0;
		}

		public void increment() {
			count++;
		}

		public int getCount() {
			return count;
		}
	}

	private Map<Coordinate, Counter> ptMap = new TreeMap<Coordinate, Counter>();

	public CoordinateCounter() {
	}

	public void addPoint(Coordinate p) {
		Counter counter = ptMap.get(p);
		if (counter == null) {
			counter = new Counter();
			ptMap.put(p, counter);
		}
		counter.increment();
	}

	public int getCount(Coordinate p) {
		Counter counter = (Counter) ptMap.get(p);
		if (counter == null)
			return 0;
		return counter.getCount();
	}
}
