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

package net.refractions.chyf.watershed.smooth;

import java.util.Collection;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.IntersectionMatrix;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.index.SpatialIndex;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.util.Assert;

import net.refractions.chyf.watershed.WatershedSettings;
import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.WatershedBoundaryEdge;

/**
 * Smoothes a coverage of generated watershed boundary edges to improve their
 * visual appearance and small-scale geometric character. Smoothing is carried
 * out in a way which satisfies the following topological correctness rules:
 * <ul>
 * <li>watershed boundary edges must not cross themselves or each other.
 * <li>watershed boundary edges must not touch stream linework except at hydro
 * graph nodes of degree >= 2
 * <li>smoothing must not violate the local topology at hydrographic nodes
 * </ul>
 * It is not 100% essential that the original input also satisfy these rules,
 * but the smoother will generally only satisfy them if that is the case.
 * 
 * @author Martin Davis
 * @version 1.0
 */
public class WatershedEdgeCoverageSmoother {

	/**
	 * Smoothes a set of {@link WatershedBoundaryEdge}s, using a given
	 * {@link PrecisionModel} and ensuring that the smoothed edges remain
	 * interior-disjoint from a given collection of {@link HydroEdge}s. Isolated
	 * rings are smoothed in a seamless fashion. The geometries for the watershed
	 * boundary edges are updated with the smoothed results.
	 * 
	 * @param wbEdges    a collection of WatershedBoundaryEdges to be smoothed
	 * @param hydroEdges a collection of HydroEdges which must remain
	 *                   interior-disjoint
	 * @param precModel  the precision model to use during smoothing
	 */
	public static void smooth(Collection<WatershedBoundaryEdge> wbEdges, Collection<HydroEdge> hydroEdges,
			PrecisionModel precModel) {
		new WatershedEdgeCoverageSmoother(wbEdges, hydroEdges, precModel);
	}

	private PrecisionModel precModel;
	// private Collection wbEdges;
	// private Collection hydroEdges;
	private SpatialIndex wbEdgeIndex;
	private SpatialIndex hydroEdgeIndex;
	private CoordinateCounter wbNodeDegreeCounter;

	/**
	 * Smoothes a set of {@link WatershedBoundaryEdge}s, using a given
	 * {@link PrecisionModel} and ensuring that the smoothed edges remain
	 * interior-disjoint from a given collection of {@link HydroEdge}s. The
	 * geometries for the watershed boundary edges are updated with the smoothed
	 * results.
	 * 
	 * @param wbEdges    a collection of WatershedBoundaryEdges to be smoothed
	 * @param hydroEdges a collection of HydroEdges which must remain
	 *                   interior-disjoint
	 * @param precModel  the precision model to use during smoothing
	 */
	public WatershedEdgeCoverageSmoother(Collection<WatershedBoundaryEdge> wbEdges, Collection<HydroEdge> hydroEdges,
			PrecisionModel precModel) {
		this.precModel = precModel;
		// this.wbEdges = wbEdges;
		// this.hydroEdges = hydroEdges;
		makePrecise(wbEdges);
		buildWBIndex(wbEdges);
		buildHydroIndex(hydroEdges);
		buildWBNodeDegreeCounter(wbEdges);
		smooth(wbEdges);
	}

	/**
	 * Rounds the input geometry to the supplied precision model. This is necessary
	 * so that the smoothing can maintain the given precision model.
	 * 
	 * @param wbEdges
	 */
	private void makePrecise(Collection<WatershedBoundaryEdge> wbEdges) {
		for (WatershedBoundaryEdge wbe : wbEdges) {
			makePrecise(wbe);
		}
	}

	private void makePrecise(WatershedBoundaryEdge wbe) {
		LineString line = wbe.getGeometry();
		CoordinateSequence seq = line.getCoordinateSequence();
		/**
		 * round endpoints which are NOT hydro nodes (since CWB does not currently obey
		 * precision model, can't round points which are in CWB data)
		 */
		if (!wbe.isHydroNodeEndpoint(0)) {
			makePrecise(seq, 0);
		}
		if (!wbe.isHydroNodeEndpoint(1)) {
			makePrecise(seq, seq.size() - 1);
		}

		// round all interior points
		for (int i = 1; i < seq.size() - 1; i++) {
			makePrecise(seq, i);
		}
	}

	// temp storage
	private Coordinate pt = new Coordinate();

	private void makePrecise(CoordinateSequence seq, int i) {
		seq.getCoordinate(i, pt);
		precModel.makePrecise(pt);
		seq.setOrdinate(i, CoordinateSequence.X, pt.x);
		seq.setOrdinate(i, CoordinateSequence.Y, pt.y);
	}

	private void smooth(Collection<WatershedBoundaryEdge> wbEdges) {
		for (WatershedBoundaryEdge wbe : wbEdges) {
			if (wbe.isSmoothable())
				smooth(wbe);
		}
	}

	private void buildWBIndex(Collection<WatershedBoundaryEdge> wbEdges) {
		wbEdgeIndex = new STRtree();
		for (WatershedBoundaryEdge wbe : wbEdges) {
			wbEdgeIndex.insert(wbe.getGeometry().getEnvelopeInternal(), wbe);
		}
	}

	/**
	 * Builds a counter for the nodes in the graph of watershed boundary edges. This
	 * can be used for detecting degree-2 nodes.
	 * 
	 * @param wbEdges
	 */
	private void buildWBNodeDegreeCounter(Collection<WatershedBoundaryEdge> wbEdges) {
		wbNodeDegreeCounter = new CoordinateCounter();
		for (WatershedBoundaryEdge wbe : wbEdges) {
			LineString line = wbe.getGeometry();
			wbNodeDegreeCounter.addPoint(line.getCoordinateN(0));
			wbNodeDegreeCounter.addPoint(line.getCoordinateN(line.getNumPoints() - 1));
		}
	}

	/**
	 * Tests if a linestring is an isolated ring.
	 * <p>
	 * A ring is isolated if the ring endnode has only 2 edges incident on it (which
	 * must be the two ends of the ring edge).
	 * 
	 * @param p the endpoint of a ring
	 * @return true if the ring is isolated.
	 */
	private boolean isIsolatedRing(LineString g) {
		if (g.isClosed() && wbNodeDegreeCounter.getCount(g.getCoordinateN(0)) <= 2)
			return true;
		return false;
	}

	private void buildHydroIndex(Collection<HydroEdge> hydroEdges) {
		hydroEdgeIndex = new STRtree();
		for (HydroEdge he : hydroEdges) {
			hydroEdgeIndex.insert(he.getLine().getEnvelopeInternal(), he);
		}
	}

	private void smooth(WatershedBoundaryEdge wbe) {
		LineString smoothLine = smoothStandard(wbe);
		if (smoothLine == null) {
			// otherwise, try incremental smoothing to smooth at least some of the line
			smoothLine = smoothIncrementally(wbe);
		}
		// only update the original line if smoothing was carried out correctly
		if (smoothLine != null)
			wbe.setGeometry(smoothLine);
	}

	/**
	 * Performs standard, non-topologically-aware smoothing on an edge. Smoothing is
	 * performed twice, checking topological consistency after each time. This
	 * should result in detecting any situations where the full smoothing causes the
	 * boundary to "skip over" the hydro feature it encloses (e.g. when a small lake
	 * feature is very close to a section of boundary with a high degree of
	 * curvature).
	 * 
	 * @param wbe
	 * @return the smoothed edge
	 * @return null if the smoothing fails the topology constraints
	 */
	private LineString smoothStandard(WatershedBoundaryEdge wbe) {
		boolean isIsolatedRing = isIsolatedRing(wbe.getGeometry());
		WatershedEdgeSmoother smoother = new WatershedEdgeSmoother(wbe.getGeometry(), wbe.getRespectedVertex(),
				isIsolatedRing);

		LineString smoothLine = smoother.smooth();
		if (!isTopologyPreserved(wbe, smoothLine)) {
			return null;
		}
		LineString smooth2Line = smoother.smooth();
		if (!isTopologyPreserved(wbe, smooth2Line)) {
			return null;
		}
		return smooth2Line;
	}

	/**
	 * Performs topologically-aware smoothing on an edge. The output of this method
	 * should always satisfy the topological constraints.
	 * 
	 * @param wbe
	 * @return a smoothed version of the edge
	 */
	private LineString smoothIncrementally(WatershedBoundaryEdge wbe) {
		LineString originalLine = wbe.getGeometry();

		ProgressiveCentroidSmoother pcs = new ProgressiveCentroidSmoother(originalLine, precModel);
		pcs.setMaximumSmoothAngle(WatershedSettings.MAX_SMOOTH_ANGLE);
		boolean isChanged = false;
		while (pcs.hasNext()) {
			pcs.generateNext();
			LineString candidateLine = pcs.getSmoothedLine();
			boolean isAcceptable = isTopologyPreserved(wbe, candidateLine);
			if (!isAcceptable) {
				pcs.retract();
			} else {
				isChanged = true;
			}
		}
		if (isChanged) {
			return pcs.getSmoothedLine();
		}
		return null;
	}

	private boolean isTopologyPreserved(WatershedBoundaryEdge wbe, LineString smoothLine) {
		// lines must not self-intersect
		if (!smoothLine.isSimple())
			return false;

		LineString originalLine = wbe.getGeometry();

		/**
		 * This algorithm depends on the fact that smoothed lines are inside the
		 * envelope of the original line. The following assertion tests this. If this is
		 * ever NOT the case, then a different line-smoothing algorithm must be used, OR
		 * this smoother must be enhanced to use before/after indexes.
		 */
		boolean isInOriginalEnvelope = originalLine.getEnvelopeInternal().contains(smoothLine.getEnvelopeInternal());
		Assert.isTrue(isInOriginalEnvelope,
				"Smoothed line is outside original envelope (at " + smoothLine.getEnvelope());

		if (!isBoundaryValid(wbe, smoothLine))
			return false;

		if (!isBoundaryHydroValid(smoothLine))
			return false;

		return true;
	}

	private boolean isBoundaryValid(WatershedBoundaryEdge wbe, LineString smoothLine) {
		// test if smoothed boundary line is interior-disjoint with other boundary lines
		@SuppressWarnings("unchecked")
		List<WatershedBoundaryEdge> items = wbEdgeIndex.query(smoothLine.getEnvelopeInternal());
		for (WatershedBoundaryEdge testWBE : items) {
			// don't test against itself
			if (testWBE == wbe)
				continue;
			LineString testLine = testWBE.getGeometry();
			if (interiorIntersects(testLine, smoothLine))
				return false;
		}
		return true;
	}

	private boolean isBoundaryHydroValid(LineString smoothLine) {

		// MD - this test needs to be improved, because it still allows
		// smoothed line to touch a mono-valent endpoint

		// test if smoothed line is interior-disjoint with hydro edge lines
		@SuppressWarnings("unchecked")
		List<HydroEdge> items = hydroEdgeIndex.query(smoothLine.getEnvelopeInternal());
		for (HydroEdge testHE : items) {
			LineString testLine = testHE.getLine();
			if (interiorIntersects(testLine, smoothLine))
				return false;
		}
		return true;
	}

	/**
	 * Tests whether the interiors of two geometries intersect. There is no OGC
	 * named predicate which does this, so have to use relate explicitly. The
	 * interiors intersect iff IM[Int][Int] is not F.
	 * 
	 * @param g1 a geometry
	 * @param g2 a geometry
	 * @return true if the interiors of the geometry intersect
	 */
	private static boolean interiorIntersects(Geometry g1, Geometry g2) {
		IntersectionMatrix im = g1.relate(g2);
		boolean interiorsIntersect = im.get(Location.INTERIOR, Location.INTERIOR) >= 0;
		return interiorsIntersect;
	}

}