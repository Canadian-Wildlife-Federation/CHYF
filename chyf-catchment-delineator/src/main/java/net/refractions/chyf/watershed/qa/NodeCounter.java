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

package net.refractions.chyf.watershed.qa;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import org.locationtech.jts.geom.Coordinate;

/**
 * Counts edges incident on nodes (endpoints). Tracks edges of two different types.
 * 
 * @author Martin Davis
 */
public class NodeCounter {
    private Map<Coordinate,Counter> nodeCountMap = new TreeMap<Coordinate,Counter>();
    private int numTypes;

    public NodeCounter(int numTypes) {
        this.numTypes = numTypes;
    }

    public void add(Coordinate p, int typeIndex) {
        Counter counter = (Counter) nodeCountMap.get(p);
        if (counter == null) {
            counter = new Counter(p, numTypes);
            nodeCountMap.put(p, counter);
        }
        counter.add(typeIndex);
    }

    /**
     * @return a Collection of Counter
     */
    public Collection<Counter> getCounts() {
        return nodeCountMap.values();
    }

    public static class Counter {
        private Coordinate p;
        private int[]      count;

        public Counter(Coordinate p, int numTypes) {
            this.p = p;
            count = new int[numTypes];
        }

        public void add(int typeIndex) {
            count[typeIndex]++;
        }

        public Coordinate getCoordinate() {
            return p;
        }

        public int count(int typeIndex) {
            return count[typeIndex];
        }
    }
}
