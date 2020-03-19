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

/**
 * A decorator which records a HydroEdge with an orientation.
 * 
 * @author Martin Davis
 */
public class OrientedHydroEdge {
    private HydroEdge edge;
    private boolean   isForward = true;

    public OrientedHydroEdge(HydroEdge edge, boolean isForward) {
        this.edge = edge;
        this.isForward = isForward;
    }

    public HydroEdge getEdge() {
        return edge;
    }
    public boolean isForward() {
        return isForward;
    }

    public boolean isWaterLeft() {
        if (isForward && edge.isWaterLeft())
            return true;
        if (!isForward && edge.isWaterRight())
            return true;
        return false;
    }

    public boolean isWaterRight() {
        if (isForward && edge.isWaterRight())
            return true;
        if (!isForward && edge.isWaterLeft())
            return true;
        return false;
    }

    public boolean isSingleLine() {
        return edge.isSingleLine();
    }
}
