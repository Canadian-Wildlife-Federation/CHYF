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

package net.refractions.chyf.watershed;

import java.util.Collection;

import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.Vertex;

import net.refractions.chyf.watershed.medialaxis.ClosestConstraintVertexFinder;
import net.refractions.chyf.watershed.model.WatershedVertex;


/**
 * Finds the constraint vertex which is furthest from a constraint. Depends on the results from
 * {@link ClosestConstraintVertexFinder}.
 * 
 * @author Martin Davis
 */
public class FurthestConstraintVertexFinder {
    public static double getFurthestDistance(QuadEdgeSubdivision subdiv) {
        FurthestConstraintVertexFinder finder = new FurthestConstraintVertexFinder(subdiv);
        finder.compute();
        return finder.getDistance();
    }

    // private QuadEdgeSubdivision subdiv;
    private Collection<QuadEdge> edgesToRun;

    private double     maxDistance = 0.0;
    private Vertex[]   maxVertices = new Vertex[2];

    @SuppressWarnings("unchecked")
	public FurthestConstraintVertexFinder(QuadEdgeSubdivision subdiv) {
        // this.subdiv = subdiv;
        edgesToRun = subdiv.getPrimaryEdges(false);
    }

    public double getDistance() {
        return maxDistance;
    }
    public Vertex[] getVertices() {
        return maxVertices;
    }

    public double compute() {
        for (QuadEdge q : edgesToRun) {
            updateWith(q.orig());
            updateWith(q.dest());
        }
        return maxDistance;
    }

    private void updateWith(Vertex v) {
        if (!(v instanceof WatershedVertex))
            return;

        WatershedVertex wv = (WatershedVertex) v;
        Vertex closeV = wv.getClosestVertex();
        if (closeV == null)
            return;

        double distance = closeV.getCoordinate().distance(wv.getCoordinate());
        if (distance > maxDistance) {
            maxDistance = distance;
            maxVertices[0] = wv;
            maxVertices[1] = closeV;
        }
    }
}
