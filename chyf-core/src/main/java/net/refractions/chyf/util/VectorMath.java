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

import org.locationtech.jts.geom.Coordinate;

/**
 * Functions for performing vector mathematics.
 * 
 * @author Martin Davis
 * @version 1.0
 */

public class VectorMath {

    /**
     * Computes the normal vector to the triangle p0-p1-p2. In order to compute the normal each
     * triangle coordinate must have a Z value. If this is not the case, the returned Coordinate
     * will have NaN values. The returned vector has unit length.
     * 
     * @param p0
     * @param p1
     * @param p2
     * @return
     */
	public static Coordinate normalToTriangle(Coordinate p0, Coordinate p1, Coordinate p2) {
        Coordinate v1 = new Coordinate(p1.x - p0.x, p1.y - p0.y, p1.getZ() - p0.getZ());
        Coordinate v2 = new Coordinate(p2.x - p0.x, p2.y - p0.y, p2.getZ() - p0.getZ());
        Coordinate cp = crossProduct(v1, v2);
        normalize(cp);
        return cp;
    }

    public static void normalize(Coordinate v) {
        double absVal = Math.sqrt(v.x * v.x + v.y * v.y + v.getZ() * v.getZ());
        v.x /= absVal;
        v.y /= absVal;
        v.setZ(v.getZ() / absVal);
    }

    public static Coordinate crossProduct(Coordinate v1, Coordinate v2) {
        double x = det(v1.y, v1.getZ(), v2.y, v2.getZ());
        double y = -det(v1.x, v1.getZ(), v2.x, v2.getZ());
        double z = det(v1.x, v1.y, v2.x, v2.y);
        return new Coordinate(x, y, z);
    }

    public static double dotProduct(Coordinate v1, Coordinate v2) {
        return v1.x * v2.x + v1.y * v2.y + v1.getZ() * v2.getZ();
    }

    public static double det(double a1, double a2, double b1, double b2) {
        return (a1 * b2) - (a2 * b1);
    }

    private VectorMath() {}
}