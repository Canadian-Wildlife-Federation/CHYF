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

package net.refractions.chyf.watershed.hydrograph;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.locationtech.jts.geom.Coordinate;

import net.refractions.chyf.watershed.model.HydroEdge;

/**
 * A node in a hydrographic network
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class HydroNode {
    private Set<HydroEdge> edgeSet = new HashSet<HydroEdge>();
    private Set<Integer> edgeIdSet = new TreeSet<Integer>();
    private int incidentEdgeCount = 0;
    private Object data;

    public HydroNode(Coordinate pt) {}

    public Object getData() {
        return data;
    }
    public void setData(Object data) {
        this.data = data;
    }

    /**
     * Adds an edge to the graph, with an optional edge ID.
     * 
     * @param edgeID may be null
     * @param edge the HydroEdge to add
     */
    public void addEdge(Integer edgeID, HydroEdge edge) {
        edgeSet.add(edge);
        if (edgeID != null)
            edgeIdSet.add(edgeID);
        incidentEdgeCount++;
    }

    public int getDegree() {
        return edgeSet.size();
    }

    /**
     * Gets the {@link HydroEdge}s incident on this node.
     * 
     * @return a Collection of HydroEdges
     */
    public Collection<HydroEdge> getEdges() {
        return edgeSet;
    }

    public Set<Integer> getEdgeIDs() {
        return edgeIdSet;
    }

    public boolean isAdjacentToRegion(int regionID) {
        for(Integer id :edgeIdSet) {
            if (id.intValue() == regionID)
                return true;
        }
        return false;
    }

    /**
     * Tests whether this node is a true topological node (i.e. has at least 3 edges incident on
     * it.)
     * 
     * @return true if this is a topologically meaningful node
     */
    public boolean isTrueNode() {
        return incidentEdgeCount >= 3;
    }

    /**
     * Tests whether this node is a start node for a watershed boundary edge. Start nodes occur at
     * the junction of constraint edges with different Region IDs.
     * 
     * @return true if this is a start node in the constraint graph
     */
    public boolean isBoundaryNode() {
        // no edge IDs have been reported, so report boundary node iff degree >= 3
        if (edgeIdSet.size() == 0)
            return isTrueNode();

        // otherwise, is a boundary node if more than one drainage area edge is incident on this
        // node
        return edgeIdSet.size() >= 2;
    }

    /**
     * Gets a HydroNode which is not adjacent to a given region, if any.
     * 
     * @param nodes a collection of HydroNodes
     * @param regionID the region id to search for
     * @return a HydroNode which is adjacent to the given region
     * @return null if no such node is found
     */
    public static HydroNode getNodeNotAdjacentToRegion(Collection<HydroNode> nodes, int regionID) {
        for( HydroNode node : nodes) {
            if (!node.isAdjacentToRegion(regionID))
                return node;
        }
        return null;
    }

}