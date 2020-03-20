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

package net.refractions.chyf.watershed.inputprep;

import java.util.Collection;

import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateList;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

/**
 * Computes a convex hull of one or more collections of Coordinates.
 * 
 * @author Martin Davis
 */
public class ConvexHullFinder {
    public static Polygon getHull(Collection<? extends Coordinate> pts1, Collection<? extends Coordinate> pts2, GeometryFactory gf) {
        ConvexHullFinder finder = new ConvexHullFinder(pts1, pts2);
        return finder.getConvexHull(gf);
    }

    public static Polygon getRetractedHull(Collection<? extends Coordinate> pts1, Collection<? extends Coordinate> pts2, double distance, GeometryFactory gf) {
        ConvexHullFinder finder = new ConvexHullFinder(pts1, pts2);
        return finder.getRetractedHull(distance, gf);
    }

    private CoordinateList allPts = new CoordinateList();

    public ConvexHullFinder(Collection<? extends Coordinate> pts) {
        allPts.addAll(pts);
    }

    public ConvexHullFinder(Collection<? extends Coordinate> pts1, Collection<? extends Coordinate> pts2) {
        allPts.addAll(pts1);
        allPts.addAll(pts2);
    }

    public Polygon getConvexHull(GeometryFactory gf) {
        Coordinate[] pts = allPts.toCoordinateArray();
        ConvexHull hullFinder = new ConvexHull(pts, gf);
        Polygon hull = (Polygon) hullFinder.getConvexHull();
        return hull;
    }

    public Polygon getRetractedHull(double distance, GeometryFactory gf) {
        Polygon hull = getConvexHull(gf);
        Polygon hullRetract = hull;
        if (distance > 0.0)
            hullRetract = (Polygon) hull.buffer(-distance);
        return hullRetract;
    }
}
