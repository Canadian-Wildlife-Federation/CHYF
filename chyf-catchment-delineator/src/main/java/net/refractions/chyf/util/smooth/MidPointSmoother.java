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

package net.refractions.chyf.util.smooth;


import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;

/**
 * Smooths a LineString by replacing the vertices by the endpoints and the midpoint of every
 * segment.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class MidPointSmoother {
    public static Geometry smooth(Geometry geom) {
        MidPointSmoother smoother = new MidPointSmoother(geom);
        return smoother.getGeometry();
    }

    private LineString line;

    public MidPointSmoother(Geometry geom) {
        this.line = (LineString) geom;
    }

    public Geometry getGeometry() {
        CoordinateSequence seq = line.getCoordinateSequence();
        int nPts = seq.size();
        CoordinateSequence seq2 = line.getFactory().getCoordinateSequenceFactory().create(
            seq.size() + 1,
            seq.getDimension());
        Coordinate p1 = new Coordinate();
        Coordinate p2 = new Coordinate();

        seq.getCoordinate(0, p1);
        CoordinateSequenceUtil.setCoordinate2D(seq2, 0, p1);
        seq.getCoordinate(nPts - 1, p1);
        CoordinateSequenceUtil.setCoordinate2D(seq2, nPts, p1);

        for (int i = 0; i < nPts - 1; i++) {
            seq.getCoordinate(i, p1);
            seq.getCoordinate(i + 1, p2);

            double xMid = (p1.x + p2.x) / 2;
            double yMid = (p1.y + p2.y) / 2;
            seq2.setOrdinate(i + 1, CoordinateSequence.X, xMid);
            seq2.setOrdinate(i + 1, CoordinateSequence.Y, yMid);
        }

        return line.getFactory().createLineString(seq2);
    }

}