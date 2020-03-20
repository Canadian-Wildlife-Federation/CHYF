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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.locationtech.jts.geom.LineString;

/**
 * Models an edge of a watershed boundary between two drainage regions.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class WatershedBoundaryEdge {

    /**
     * Extracts the linestrings from a collection of {@link WatershedBoundaryEdge}s
     * 
     * @param wbEdges a collection of WatershedBoundaryEdges
     * @return a list of LineStrings
     */
	public static List<LineString> extractLines(Collection<WatershedBoundaryEdge> wbEdges) {
        List<LineString> lines = new ArrayList<LineString>();
        for (WatershedBoundaryEdge edge : wbEdges) {
            lines.add(edge.getGeometry());
        }
        return lines;
    }

    /**
     * The index for the left side of a boundary edge (relative to the orientation of the edge)
     */
    public static final int LEFT         = 0;
    /**
     * The index for the right side of a boundary edge (relative to the orientation of the edge)
     */
    public static final int RIGHT        = 1;

    private LineString      line;
    private boolean[]       isVertexRespected;
    private int[]           regionID;
    private boolean         isSmoothable = false;
    private boolean         isNodeFirst;
    private boolean         isNodeLast;

    public WatershedBoundaryEdge(LineString line, boolean[] isVertexRespected, int[] regionID,
            boolean isHydroNodeFirst, boolean isHydroNodeLast) {
        this.line = line;
        this.isVertexRespected = isVertexRespected;
        this.regionID = regionID;
        this.isNodeFirst = isHydroNodeFirst;
        this.isNodeLast = isHydroNodeLast;
    }

    public LineString getGeometry() {
        return line;
    }
    public void setGeometry(LineString line) {
        this.line = line;
    }

    /**
     * Gets the array indicating which vertices are localMaxima
     * 
     * @return
     */
    public boolean[] getRespectedVertex() {
        return isVertexRespected;
    }

    /**
     * Gets the region (drainage ID) which lies on the given side of this edge.
     * 
     * @param i the index for LEFT or RIGHT
     * @return the region id for the given edge side
     */
    public int getRegionID(int i) {
        return regionID[i];
    }

    /**
     * Sets whether this boundary edge should be smoothed.
     * 
     * @param isSmoothable whether this edge should be smoothed
     */
    public void setSmoothable(boolean isSmoothable) {
        this.isSmoothable = isSmoothable;
    }

    /**
     * Tests whether this boundary edge should be smoothed.
     * 
     * @return true if this edge should be smoothed
     */
    public boolean isSmoothable() {
        return isSmoothable;
    }

    /**
     * Tests whether an endpoint is a hydro node.
     * 
     * @param i 0 or 1 (= start or end)
     * @return true if the endpoint is a hydro node
     */
    public boolean isHydroNodeEndpoint(int i) {
        if (i == 0)
            return isNodeFirst;
        return isNodeLast;
    }

    public String toString() {
        return line.toString();
    }
}