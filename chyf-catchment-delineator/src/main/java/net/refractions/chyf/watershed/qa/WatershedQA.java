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
import java.util.List;
import java.util.stream.Collectors;

import org.locationtech.jts.algorithm.BoundaryNodeRule;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateArrays;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.util.LinearComponentExtracter;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.operation.BoundaryOp;
import org.locationtech.jts.util.Debug;

import net.refractions.chyf.watershed.WatershedBoundaryBuilder;
import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.WatershedBoundaryEdge;

/**
 * Performs various QA checks on the results of Watershed Boundary building. QA
 * checks performed are:
 * <ul>
 * <li>the Watershed Boundary Edges form a clean coverage (ie. do not intersect
 * in their interior)
 * <li>the Watershed Boundary Edges intersect the Hydro edges only at
 * multi-valent (degree > 1) hydro nodes. (In other words, WB edges should not
 * touch the hydro edges at monovalent (degree 1) nodes.)
 * <li>[NOT YET IMPLEMENTED] the topology of watershed edges and hydro edges at
 * hydro nodes is correct. Correct topology requires that around each node:
 * <ul>
 * <li>any two consecutive watershed edges must have a hydro edge between them,
 * and
 * <li>any two consecutive hydro edges with different drainage ids must have a
 * watershed edge between them
 * </ul>
 * <li>there are not more WB edges than hydro edges incident on each hydro node
 * (this is currently substituting for the node topology check above)
 * </ul>
 * TODO: implement topology consistency check at hydro nodes
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class WatershedQA {
	private Collection<WatershedBoundaryEdge> wbEdges;
	private Collection<LineString> wbLines;
	private Collection<HydroEdge> trimmedHydroEdges;
	private Collection<LineString> heLines;
	private Geometry borderHydroMP;
	private String errMsg;
	private GeometryFactory gf;

	public WatershedQA(WatershedBoundaryBuilder wsb) {
		gf = wsb.getGeometryFactory();
		this.wbEdges = wsb.getBoundaryEdges();
		wbLines = WatershedBoundaryEdge.extractLines(wbEdges);

		this.trimmedHydroEdges = wsb.getTrimmedHydroEdges();
		heLines = trimmedHydroEdges.stream().map(e -> e.getLine()).collect(Collectors.toList());

		Collection<Coordinate> borderHydroPts = wsb.getTIN().getBorderHydroCoords();
		borderHydroMP = gf.createMultiPointFromCoords(CoordinateArrays.toCoordinateArray(borderHydroPts));
	}

	/**
	 * If a QA check failed, provides an error message describing the reason for the
	 * failure.
	 * 
	 * @return a error message
	 */
	public String getErrorMessage() {
		return errMsg;
	}

	/**
	 * Tests whether the Watershed Boundaries pass the QA checks
	 * 
	 * @return true if the boundaries are valid
	 */
	public boolean isValid() {
		if (!isBoundaryEdgesCleanCoverage())
			return false;
		if (!isBoundaryHydroIntersectAtMultiValentNodes())
			return false;
		if (!isNodeWBHydroEdgeCountConsistent())
			return false;
		return true;
	}

	/**
	 * Tests whether the Watershed Boundary Edges form a clean coverage (ie. do not
	 * intersect in their interior)
	 * 
	 * @return true if the Watershed Boundary Edges form a clean coverage
	 */
	private boolean isBoundaryEdgesCleanCoverage() {
		CleanCoverageTester ccTest = new CleanCoverageTester(wbLines);
		if (!ccTest.isValid()) {
			errMsg = "Boundary Edge intersection found at: " + WKTWriter.toPoint(ccTest.getNonSimpleLocation());
			return false;
		}
		return true;
	}

	/**
	 * Tests whether the Watershed Boundary Edges intersect the Hydro edges only at
	 * multi-valent hydro nodes. In other words, WB edges should not touch the hydro
	 * edges at monovalent (degree 1) nodes.
	 * 
	 * @return true if there are no monovalent WB/Hydro edge intersections
	 */
	private boolean isBoundaryHydroIntersectAtMultiValentNodes() {
		Geometry wbML = gf.buildGeometry(wbLines);

		Geometry heML = gf.buildGeometry(heLines);

		/**
		 * Get the multi-valent endpoints of the HE lines (i.e. the valence-1 endpoints
		 * are considered to be in the interior, and should not intersect the watershed
		 * boundary lines)
		 */
		Geometry heBdy = (new BoundaryOp(heML, BoundaryNodeRule.MULTIVALENT_ENDPOINT_BOUNDARY_RULE)).getBoundary();
		// System.out.println(borderHydroMP);

		Geometry wbHeIntersection = wbML.intersection(heML);

		/**
		 * If the intersection has lines in it, this is an error. (It indicates that a
		 * boundary edge and a hydro edge are coincident).
		 */
		Coordinate intPt = null;
		if (wbHeIntersection.getDimension() == 1) {
			@SuppressWarnings("unchecked")
			List<LineString> lines = LinearComponentExtracter.getLines(wbHeIntersection);
			if (lines.isEmpty())
				return true;
			LineString line = (LineString) lines.get(0);
			intPt = line.getCoordinate();
			if (intPt != null) {
				errMsg = "Boundary / Hydro Edge coincident line found at: " + WKTWriter.toPoint(intPt);
				return false;
			}
		} else {
			// intersection contains points only
			Geometry interiorIntPts = wbHeIntersection.difference(heBdy);
			/**
			 * Intersections with hydro points on the border are not a concern. In order to
			 * prevent reporting intersections with valence-1 hydro points along the border,
			 * delete them from the intersection points.
			 */
			Geometry interiorIntPtsNonBorder = interiorIntPts.difference(borderHydroMP);
			if (!interiorIntPtsNonBorder.isEmpty()) {
				intPt = interiorIntPtsNonBorder.getCoordinate();

				if (Debug.isDebugging()) {
					Debug.println("Boundary / Hydro Edge intersections:");
					System.out.println(interiorIntPtsNonBorder);
				}
			}
		}

		if (intPt != null) {
			errMsg = "Boundary / Hydro Edge crossing found at: " + WKTWriter.toPoint(intPt);
			return false;
		}
		return true;
	}

	private static final int WB_EDGE = 0;
	private static final int HYDRO_EDGE = 1;

	private boolean isNodeWBHydroEdgeCountConsistent() {
		Coordinate errorPt = null;

		NodeCounter nodeCounter = new NodeCounter(2);

		addLines(wbLines, WB_EDGE, nodeCounter);
		addLines(heLines, HYDRO_EDGE, nodeCounter);

		Collection<NodeCounter.Counter> counts = nodeCounter.getCounts();
		for (NodeCounter.Counter counter : counts) {
			int nWBEdges = counter.count(WB_EDGE);
			int nHydroEdges = counter.count(HYDRO_EDGE);
			Coordinate pt = counter.getCoordinate();

			// WatershedDebug.watchPoint(pt, new Coordinate(1448231.5138, 507770.5573));

			if (nHydroEdges > 0 && nWBEdges > nHydroEdges) {
				errorPt = pt;
			}
		}

		if (errorPt != null) {
			errMsg = "More WB edges than hydro edges found at: " + WKTWriter.toPoint(errorPt);
			return false;
		}
		return true;

	}

	private void addLines(Collection<LineString> lines, int typeIndex, NodeCounter nodeCounter) {
		for (LineString line : lines) {
			nodeCounter.add(line.getCoordinateN(0), typeIndex);
			nodeCounter.add(line.getCoordinateN(line.getNumPoints() - 1), typeIndex);
		}
	}

}