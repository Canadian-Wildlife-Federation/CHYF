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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

import net.refractions.chyf.watershed.model.HydroEdge;

/**
 * Clips a set of HydroEdges to a given polygon. The edges are clipped precisely to the polygon.
 * Extremely short segments created by the clipping can be removed by specifying a length tolerance.
 * 
 * @author Martin Davis
 */
public class HydroEdgeClipper {
    public static List<HydroEdge> getClippedEdges(Collection<HydroEdge> hydroEdges, Polygon clipPoly) {
        HydroEdgeClipper clipper = new HydroEdgeClipper(hydroEdges, clipPoly);
        return clipper.getClippedEdges();
    }

    private Collection<HydroEdge> hydroEdges;
    private Polygon clipPoly;
    private List<HydroEdge> resultEdges = new ArrayList<HydroEdge>();

    public HydroEdgeClipper(Collection<HydroEdge> hydroEdges, Polygon clipPoly) {
        this.hydroEdges = hydroEdges;
        this.clipPoly = clipPoly;
        clip();
    }

    public List<HydroEdge> getClippedEdges() {
        return resultEdges;
    }

    private void clip() {
        for (HydroEdge he: hydroEdges) {
            clip(he);
        }
    }

    private void clip(HydroEdge he) {
        LineString heLine = he.getLine();
        List<LineString> clippedLines = LineStringClipper.clip(heLine, clipPoly);

        if (clippedLines.size() == 0) {
            return;
        } else if (isUnchanged(clippedLines, heLine)) {
            resultEdges.add(he);
        } else {
            // System.out.println(clipPoly);
            // System.out.println(clippedLines.get(0));

            for (LineString subline : clippedLines) {
                HydroEdge heNew = new HydroEdge(subline, he.getDrainageID(), he.getWaterSide());
                resultEdges.add(heNew);
            }
        }
    }

    private static boolean isUnchanged(List<LineString> clippedLines, LineString inputLine) {
        if (clippedLines.size() != 1)
            return false;
        double clipLen = ((LineString) clippedLines.get(0)).getLength();
        double lineLen = inputLine.getLength();
        return (clipLen == lineLen);
    }

    // private void OLDclip() {
    // // for now just do trimming
    // HydroEdgeTrimmer het = new HydroEdgeTrimmer(hydroEdges, clipPoly);
    // resultEdges = het.getTrimmedEdges();
    // }
}
