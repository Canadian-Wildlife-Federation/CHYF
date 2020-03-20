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

package net.refractions.chyf.watershed.model;

import org.locationtech.jts.triangulate.quadedge.QuadEdge;

/**
 * Utilities for dealing with {@link WatershedTriangle}s.
 * 
 * @author Martin Davis
 */
public class WatershedTriangleUtil {

    /**
     * Determines the integer from the set [0..2] which is not in the set {n1, n2}.
     * 
     * @param n1 an integer in [0..2]
     * @param n2 an integer in [0..2]
     * @return the integer from [0..2] which is not one of the input values
     */
    public static int otherOutOf3(int n1, int n2) {
        for (int i = 0; i < 3; i++) {
            if (i != n1 && i != n2)
                return i;
        }
        throw new IllegalArgumentException("Input values appear not to lie in range [0..2]");
    }

    public static int[] edgeIndicesSortedByEdgeLength(WatershedTriangle tri) {
        int[] index = new int[]{0, 1, 2};

        // sort using bubble sort
        if (length(tri.getEdge(index[0])) < length(tri.getEdge(index[1])))
            swap(index, 0, 1);
        if (length(tri.getEdge(index[1])) < length(tri.getEdge(index[2])))
            swap(index, 1, 2);
        if (length(tri.getEdge(index[0])) < length(tri.getEdge(index[1])))
            swap(index, 0, 1);

        return index;
    }

    private static void swap(int[] values, int i, int j) {
        int tmp = values[i];
        values[i] = values[j];
        values[j] = tmp;
    }

    public static double length(QuadEdge e) {
        return e.orig().getCoordinate().distance(e.dest().getCoordinate());
    }
}
