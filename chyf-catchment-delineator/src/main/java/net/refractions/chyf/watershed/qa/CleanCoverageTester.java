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

import org.locationtech.jts.algorithm.BoundaryNodeRule;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.IsSimpleOp;

/**
 * Tests whether a set of {@link LineString}s has no intersections except at endpoints.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class CleanCoverageTester {
    private Geometry   multiLine   = null;
    private Coordinate nonSimplePt = null;

    public CleanCoverageTester(Collection<LineString> lines) {
        if (lines.size() == 0)
            return;
        LineString[] lineArray = GeometryFactory.toLineStringArray(lines);
        GeometryFactory fact = lineArray[0].getFactory();
        multiLine = fact.createMultiLineString(lineArray);
    }

    public Coordinate getNonSimpleLocation() {
        return nonSimplePt;
    }

    public boolean isValid() {
        // handle empty input case
        if (multiLine == null)
            return true;

        IsSimpleOp op = new IsSimpleOp(multiLine, BoundaryNodeRule.ENDPOINT_BOUNDARY_RULE);
        boolean isSimple = op.isSimple();
        nonSimplePt = op.getNonSimpleLocation();
        return isSimple;
    }
}