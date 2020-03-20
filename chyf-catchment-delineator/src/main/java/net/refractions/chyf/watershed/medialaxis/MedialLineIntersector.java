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

package net.refractions.chyf.watershed.medialaxis;

import org.locationtech.jts.geom.Coordinate;

/**
 * Computes the intersection of a line segment with the medial axis of the points nearest the ends
 * of the line endpoints.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class MedialLineIntersector {
    // Currently done iteratively, but there's probably a closed-form solution.

    private static final double ERROR_TOL = 1E-6;

    private Coordinate          p0;
    private Coordinate          p1;
    private Coordinate          near0;
    private Coordinate          near1;

    public MedialLineIntersector(Coordinate p0, Coordinate p1, Coordinate near0, Coordinate near1) {
        this.p0 = p0;
        this.p1 = p1;
        this.near0 = near0;
        this.near1 = near1;
    }

    public Coordinate getMedialCoordinate() {
        double dx = p1.x - p0.x;
        double dy = p1.y - p0.y;
        // double len =
        p1.distance(p0);

        double inc = 0.5;
        double factor = 0.5;
        Coordinate candMedial = new Coordinate();
        while (inc > ERROR_TOL) {
            candMedial.x = p0.x + dx * factor;
            candMedial.y = p0.y + dy * factor;

            double d0 = candMedial.distance(near0);
            double d1 = candMedial.distance(near1);
            inc /= 2.0;
            if (d0 < d1)
                factor += inc;
            else
                factor -= inc;
        }
        return candMedial;
    }

}