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

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.triangulate.Segment;

import net.refractions.chyf.watershed.WatershedSettings;
import net.refractions.chyf.watershed.hydrograph.HydroNode;
import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.Region;
import net.refractions.chyf.watershed.model.WatershedConstraint;
import net.refractions.chyf.watershed.model.WatershedTIN;
import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Prepares the input data for Watershed Boundary Generation.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class HydroEdgePreparer {
	private Collection<HydroEdge> hydroEdges; // Collection<HydroEdge>

	private WatershedTIN watershedTIN;
	// private ConvexPolygonLocater demHullArea;

	// prepared input data
	private List<WatershedVertex> constraintVertices;
	private List<Segment> constraintSegments;

	/**
	 * Prepares the hydrographic edges for input to the watershed building process.
	 * This class does the following:
	 * <ul>
	 * <li>Creates a {@link WatershedVertex} for every included constraint vertex
	 * <li>Creates constraint {@link Segment}s for each included segment of a hydro
	 * edge
	 * <li>Flags nodes in the hydro edge graph
	 * </ul>
	 * 
	 * @param hydroEdges   the HydroEdges to use as constraints
	 * @param watershedTIN the watershed TIN structure
	 */
	public HydroEdgePreparer(Collection<HydroEdge> hydroEdges, WatershedTIN watershedTIN) {
		this.hydroEdges = hydroEdges;
		this.watershedTIN = watershedTIN;
		extractConstraints();
	}

	/**
	 * Gets the list of {@link WatershedVertex}s which are the constraint vertices.
	 * 
	 * @return a list of {@link WatershedVertex}s
	 */
	public List<WatershedVertex> getConstraintVertices() {
		return constraintVertices;
	}

	/**
	 * Gets the list of {@link Segment}s which form the constraints.
	 * 
	 * @return a list of {@link Segment}s
	 */
	public List<Segment> getConstraintSegments() {
		return constraintSegments;
	}

	private void extractConstraints() {
		constraintVertices = new ArrayList<WatershedVertex>();
		constraintSegments = new ArrayList<Segment>();
		// int edgeID = 1;
		for (HydroEdge edge : hydroEdges) {
			Region region = watershedTIN.getRegionFactory().getRegion(edge.getDrainageID());
			// WatershedVertex prev = null;
			extractConstraints(edge, region);
		}
	}

	/**
	 * Extracts the constraint segments for an edge which lie in the fence.
	 * 
	 * @param edge
	 * @param region
	 */
	private void extractConstraints(HydroEdge edge, Region region) {
		// get the vertices for the constraint segments in this edge
		WatershedVertex[] vert = extractConstraintVertices(edge, region);

		// create constraint segments
		WatershedConstraint constraintData = new WatershedConstraint(edge, region);
		for (int j = 0; j < vert.length - 1; j++) {
			if (vert[j] == null || vert[j + 1] == null)
				continue;

			Segment seg = new Segment(vert[j].getCoordinate(), vert[j + 1].getCoordinate(), constraintData);
			constraintSegments.add(seg);
		}
	}

	private WatershedVertex[] extractConstraintVertices(HydroEdge edge, Region region) {
		LineString line = edge.getLine();
		int numPts = line.getNumPoints();
		// create vertices (inside fence)
		WatershedVertex[] vert = new WatershedVertex[numPts];
		for (int i = 0; i < numPts; i++) {
			Coordinate linePt = line.getCoordinateN(i);
			// Hydro segs are forced to have an elevation of 0
			linePt.setZ(WatershedSettings.CONSTRAINT_HEIGHT);

			boolean isEndpoint = (i == 0 || i == numPts - 1);

			WatershedVertex v = createVertex(linePt, isEndpoint);
			v.setConstraint(edge);
			v.setRegion(region);

			// if endpoint, check if it is a hydro graph node
			if (isEndpoint) {
				if (watershedTIN.getHydroGraph().getNode(linePt).isBoundaryNode()) {
					v.setNode(watershedTIN.getHydroGraph().getNode(linePt));
				}
			}
			constraintVertices.add(v);
			vert[i] = v;
		}
		return vert;
	}

	private WatershedVertex createVertex(Coordinate pt, boolean isEndpoint) {
		// see if vertex already exists, if this is a node
		WatershedVertex nodeVert = null;
		HydroNode node = null;
		if (isEndpoint) {
			node = watershedTIN.getHydroGraph().getNode(pt);
			nodeVert = (WatershedVertex) node.getData();
		}
		// create a new vertex if necessary
		WatershedVertex v = nodeVert;
		if (v == null) {
			v = new WatershedVertex(pt);
			v.setClosestVertex(v);
			// EXPERIMENTAL: closestVertexFinder
			//v.setClosestCoordinate(v.getCoordinate());
			
		}
		// save to HydroNode, if needed
		if (isEndpoint && nodeVert == null) {
			node.setData(v);
			v.setNode(node);
		}
		return v;
	}

}