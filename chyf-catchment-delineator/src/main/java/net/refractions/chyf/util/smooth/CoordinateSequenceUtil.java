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

/**
 * Utility methods for {@link CoordinateSequence}s.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class CoordinateSequenceUtil {
    public static void setCoordinate2D(CoordinateSequence seq, int i, Coordinate p) {
        seq.setOrdinate(i, CoordinateSequence.X, p.x);
        seq.setOrdinate(i, CoordinateSequence.Y, p.y);
    }

}