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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTWriter;
import org.locationtech.jts.triangulate.ConformingDelaunayTriangulator;
import org.locationtech.jts.triangulate.Segment;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.triangulate.quadedge.TriangleVisitor;
import org.locationtech.jts.triangulate.quadedge.Vertex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.util.ProcessStatistics;
import net.refractions.chyf.watershed.hydrograph.HydroGraph;
import net.refractions.chyf.watershed.inputprep.ConvexHullFinder;
import net.refractions.chyf.watershed.inputprep.DEMPreparer;
import net.refractions.chyf.watershed.inputprep.DEMProximityFilter;
import net.refractions.chyf.watershed.inputprep.HydroEdgeClipper;
import net.refractions.chyf.watershed.inputprep.HydroEdgePreparer;
import net.refractions.chyf.watershed.medialaxis.ClosestConstraintGeometryBuilder;
import net.refractions.chyf.watershed.medialaxis.IndexedClosestConstraintVertexFinder;
import net.refractions.chyf.watershed.medialaxis.MedialAxisCornerRefiner;
import net.refractions.chyf.watershed.medialaxis.MedialAxisExtracter;
import net.refractions.chyf.watershed.medialaxis.MedialAxisRefiner;
import net.refractions.chyf.watershed.medialaxis.MedialAxisTriangleRefiner;
import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.WatershedBoundaryEdge;
import net.refractions.chyf.watershed.model.WatershedTIN;
import net.refractions.chyf.watershed.model.WatershedTriangle;
import net.refractions.chyf.watershed.model.WatershedVertex;
import net.refractions.chyf.watershed.smooth.WatershedEdgeCoverageSmoother;

/**
 * Builds a height-of-land using a triangulation.
 */
public class WatershedBoundaryBuilder {
	public static Logger logger = LoggerFactory.getLogger(WatershedBoundaryBuilder.class);

	ProcessStatistics stats;

	// disable to do Delaunay construction only
	private boolean doWatershed = true;

	private WatershedTIN watershedTIN;
	private Collection<Coordinate> demCoords;
	private Collection<Coordinate> respectedDEMCoords;
	private Collection<HydroEdge> hydroEdges;
	private GeometryFactory gf;

	private Polygon fenceGeom = null;

	// prepared input data
	private List<WatershedVertex> demVertices;
	private List<WatershedVertex> constraintVertices;
	private List<Segment> constraintSegments;
	private List<HydroEdge> trimmedHydroEdges;

	private Collection<WatershedTriangle> triangles = null;
	private List<Coordinate> pitBorderMidpoints = null;

	// algorithm objects
	private ConformingDelaunayTriangulator cdt;
	private IndexedClosestConstraintVertexFinder ccf;
	//IndexedClosestHydroFinder hydroFinder;
	// private ClosestConstraintVertexFinder ccf;
	private MedialAxisExtracter mab;
	private SteepestDescentTrickler trickleTracer;
	private WatershedEdgeExtracter wee;

	private Collection<WatershedBoundaryEdge> boundaryEdges;
	private double areaRatio = 0.0;
	private double maxVertexDistance = 0.0;

	/**
	 * Builds the watershed surface and boundary edges for the given surface and
	 * hydrological data
	 * 
	 * @param demCoords          the ordinary DEM points (Collection<Coordinate>)
	 * @param respectedDEMCoords the DEM points to be respected by the generated
	 *                           boundary lines (Collection<Coordinate>)
	 * @param constraintLines    the hydrographic edges (Collection<HydroEdge>)
	 */
	public WatershedBoundaryBuilder(Collection<Coordinate> demCoords, Collection<Coordinate> respectedDEMCoords,
			Collection<HydroEdge> hydroEdges, GeometryFactory gf, ProcessStatistics stats) {
		watershedTIN = new WatershedTIN();
		this.demCoords = demCoords;
		this.respectedDEMCoords = respectedDEMCoords;
		this.hydroEdges = hydroEdges;
		//this.hydroFinder = hydroFinder;
		this.gf = gf;
		this.stats = stats;
	}

	/**
	 * Sets the fence (bounding area) for all DEM points and linear constraints).
	 * The fence must be a convex polygon. (This implies no holes.)
	 * 
	 * @param fence the polygon defining the fence
	 */
	public void setFence(Polygon fence) {
		fenceGeom = fence;
	}

	public void setTriangulationOnly(boolean doTriangulationOnly) {
		if (doTriangulationOnly)
			doWatershed = false;
	}

	/**
	 * Gets the 3D:2D area ratio of the DEM TIN. This is computed before the
	 * addition of the constraints, since their elevation clamping would result in a
	 * distorted value.
	 * 
	 * @return the 3D:2D area ratio of the DEM TIN
	 */
	public double getAreaRatio() {
		return areaRatio;
	}

	/**
	 * Gets the maximum distance from a DEM vertex to a constraint.
	 * <p>
	 * This number provides an indication of whether the hydrographic constraints
	 * were dense enough to determine correct watershed boundaries within the core
	 * of the process block. If the block buffer distance was not safely more than
	 * the max distance, there is a risk that the computed watershed lines in the
	 * block core will be affected by edge effects. (Using a large block buffer
	 * distance should eliminate this problem).
	 * 
	 * @return the maximum distance from a DEM vertex to a constraint
	 */
	public double getMaximumVertexDistance() {
		return maxVertexDistance;
	}

	public WatershedTIN getTIN() {
		return watershedTIN;
	}

	private void prepareInputData() {
		// prepare the DEM vertices and determine their convex hull
		// (which all other data will be constrained to lie inside)
		DEMPreparer demPrep = new DEMPreparer(demCoords, respectedDEMCoords, watershedTIN, gf);
		if (fenceGeom != null)
			demPrep.setFence(fenceGeom);
		Collection<WatershedVertex> allDEMVertices = demPrep.getDEMVertices();

		// Polygon demHull = ConvexHullFinder.getHull(demCoords, respectedDEMCoords);
		Polygon demHull = ConvexHullFinder.getRetractedHull(demCoords, respectedDEMCoords,
				WatershedSettings.DEM_HULL_INSET, gf);
		trimmedHydroEdges = HydroEdgeClipper.getClippedEdges(hydroEdges, demHull);

		/*
		 * Polygon demHull = demPrep.getDEMConvexHull(); // shrink containing poly by a
		 * small amount, to keep constraints away from sides Polygon demHullCore =
		 * demHull; if (WatershedSettings.DEM_HULL_INSET > 0.0) demHullCore = (Polygon)
		 * demHull.buffer(-WatershedSettings.DEM_HULL_INSET); // trim the hydrographic
		 * edge linestrings so they lie inside the convex hull HydroEdgeTrimmer het =
		 * new HydroEdgeTrimmer(hydroEdges, demHullCore); trimmedHydroEdges =
		 * het.getTrimmedEdges();
		 */

		// build graph of hydrographic edges to allow determining nodes
		watershedTIN.setHydroGraph(HydroGraph.buildHydroGraph(trimmedHydroEdges));
		// extract constraint vertices and segments from hydro edges
		HydroEdgePreparer hydroPrep = new HydroEdgePreparer(trimmedHydroEdges, watershedTIN);
		constraintVertices = hydroPrep.getConstraintVertices();
		constraintSegments = hydroPrep.getConstraintSegments();

		DEMProximityFilter demFilter = new DEMProximityFilter(allDEMVertices, constraintSegments);
		demVertices = demFilter.getFilteredVertices(WatershedSettings.MIN_DEM_CONSTRAINT_DISTANCE);
		stats.reportStatus(logger, (allDEMVertices.size() - demVertices.size()) + " DEM points removed due to proximity with edges ");
	}

	public void build() {
		prepareInputData();

		if (constraintSegments.size() == 0) {
			throw new IllegalArgumentException("No constraint segments are in processing area");
		}

		cdt = new ConformingDelaunayTriangulator(demVertices, WatershedSettings.SNAP_TOLERANCE);
		cdt.setVertexFactory(watershedTIN.getVertexFactory());
		cdt.setConstraints(constraintSegments, constraintVertices);
		cdt.setSplitPointFinder(new WatershedConstraintSplitPointFinder(watershedTIN, gf));

		cdt.formInitialDelaunay();
		stats.reportStatus(logger, "Initial Delaunay triangulation computed");

		// MD - debugging only
		// QuadEdgeSubdivision.dumpTriangles();

		// if (true) return;
		// MD - no longer needed
		// areaRatio = TINAreaComputer.computeAreaRatio(cdt.getSubdivision());

		cdt.enforceConstraints();
		stats.reportStatus(logger, "Constraints enforced");

		// MD - debugging only
		// QuadEdgeSubdivision.dumpTriangles();

		if (doWatershed) {

			watershedTIN.setSubdivision(cdt.getSubdivision());
			setHullVertexConstraints();
			doMedialAxisRefinement(watershedTIN);
			// EXPERIMENTAL: closestVertexFinder
			//hydroFinder.assignClosest(cdt.getSubdivision().getPrimaryEdges(false));
			doFindClosestConstraintVertices(cdt, constraintVertices);
			
			triangles = buildTriangles();

			doTrickle();
			// triangle list is now stale, since trickling may have split triangles
			triangles = null;

			doConnectAndMerge();

			/**
			 * MD - disabled for now, since don't need this for farthest-point-from-water
			 * calculation (water areas essentially don't contain DEM points, so don't need
			 * to worry about in-water points biasing the result
			 */
			//doWaterAssignment();
			doBoundaryEdgeExtract(cdt);
		}

		stats.reportStatus(logger, "Build finished");
	}

	public List<Coordinate> getPitBorderMidpoints() {
		return pitBorderMidpoints;
	}

	public Collection<WatershedTriangle> getTriangles() {
		if (triangles == null)
			triangles = findTriangles();
		return triangles;
	}

	private Set<WatershedTriangle> findTriangles() {
		Set<WatershedTriangle> triangles = new HashSet<WatershedTriangle>();
		@SuppressWarnings("unchecked")
		Collection<QuadEdge> edges = cdt.getSubdivision().getEdges();
		for (QuadEdge e : edges) {
			addTriangle(e, triangles);
			addTriangle(e.sym(), triangles);
		}
		return triangles;
	}

	private void addTriangle(QuadEdge e, Collection<WatershedTriangle> triangles) {
		WatershedTriangle tri = (WatershedTriangle) e.getData();
		if (tri != null)
			triangles.add(tri);
	}

	private Collection<WatershedTriangle> buildTriangles() {
		WatershedTriangleBuilder visitor = new WatershedTriangleBuilder(watershedTIN);
		cdt.getSubdivision().visitTriangles(visitor, false);
		triangles = visitor.getTriangles();
		return triangles;
	}

	public List<LineString> getCloseLines() {
		ClosestConstraintGeometryBuilder ccg = new ClosestConstraintGeometryBuilder(cdt.getSubdivision());
		return ccg.getGeometry();
	}

	public List<LineString> getMidLines() {
		return mab.getMidLines();
	}

	public List<Geometry> getTrickleTraces() {
		if (trickleTracer == null)
			return null;
		return trickleTracer.getTraceGeometry();
	}

	/**
	 * Gets the trimmed Hydro Edges. The Hydro Edges are trimmed to the inset convex
	 * hull of the DEM points.
	 * 
	 * @return a list of {@link LineString}s
	 */
	public List<HydroEdge> getTrimmedHydroEdges() {
		return trimmedHydroEdges;
	}

	/**
	 * Gets the computed {@link WatershedBoundaryEdge}s.
	 * 
	 * @return a collection of WatershedBoundaryEdges
	 */
	public Collection<WatershedBoundaryEdge> getBoundaryEdges() {
		return boundaryEdges;
	}

	/**
	 * For all hull edges in the TIN, sets the constraint information for their
	 * vertices. If a vertex is already a constraint vertex, it is not updated (this
	 * gives priority to hydro constraints).
	 */
	@SuppressWarnings("unchecked")
	private void setHullVertexConstraints() {
		QuadEdgeSubdivision subdiv = cdt.getSubdivision();
		Geometry convexHull = cdt.getConvexHull();
		Coordinate[] pts = convexHull.getCoordinates();
		Set<Coordinate> cvSet = getCoordinateSet(pts);

		for (QuadEdge qe : (Collection<QuadEdge>) subdiv.getEdges()) {
			if (cvSet.contains(qe.orig().getCoordinate()))
				setHullVertexConstraint(qe.orig());
			if (cvSet.contains(qe.dest().getCoordinate()))
				setHullVertexConstraint(qe.dest());
		}
	}

	private void setHullVertexConstraint(Vertex v) {
		WatershedVertex wv = (WatershedVertex) v;
		wv.setOnHull(true);
		if (wv.getRegion() == null) {
			wv.setRegion(watershedTIN.getRegionFactory().getBorder());
			// System.out.println(WKTWriter.toPoint(pt));
		}
	}

	private static Set<Coordinate> getCoordinateSet(Coordinate[] pts) {
		Set<Coordinate> coordSet = new TreeSet<Coordinate>();
		for (int i = 0; i < pts.length; i++) {
			coordSet.add(pts[i]);
		}
		return coordSet;
	}

	private static final boolean FIND_FURTHEST_VERTEX = false;
	@SuppressWarnings("unchecked")
	private void doFindClosestConstraintVertices(ConformingDelaunayTriangulator cdt,
			List<WatershedVertex> constraintVertices) {
		ccf = new IndexedClosestConstraintVertexFinder(constraintVertices);
		ccf.assignClosest(cdt.getSubdivision().getPrimaryEdges(false));

		if (FIND_FURTHEST_VERTEX) {
			if (constraintVertices.size() > 0) {
				// find the vertex which is furthest from a constraint
				FurthestConstraintVertexFinder finder = new FurthestConstraintVertexFinder(cdt.getSubdivision());
				finder.compute();
				maxVertexDistance = finder.getDistance();
				Vertex[] maxVert = finder.getVertices();
				logger.info("Max distance from water: " + maxVertexDistance + "     "
						+ WKTWriter.toLineString(maxVert[0].getCoordinate(), maxVert[1].getCoordinate()));
			} else {
				logger.info("No constraints present - max distance not computed");
			}
		}

		stats.reportStatus(logger, "Closest Constraint Vertices computed");
	}

	private void doMedialAxisRefinement(WatershedTIN watershedTIN) {
		MedialAxisCornerRefiner.refineMedial(watershedTIN);
		stats.reportStatus(logger, "Medial Axis Corners refined");

		MedialAxisRefiner.refine(watershedTIN);
		MedialAxisTriangleRefiner.refine(watershedTIN);
		stats.reportStatus(logger, "Medial Axis refined");
	}

	// private void doMedialAxisExtract(ConstrainedDelaunayTriangulator cdt) {
	// mab = new MedialAxisExtracter(cdt.getSubdivision());
	// mab.compute();
	// }

	

	// private List getTestTriangle(Coordinate pt) {
	// List list = new ArrayList();
	// QuadEdge qe = cdt.getSubdivision().locate(pt);
	// WatershedTriangle tri = (WatershedTriangle) qe.getData();
	// list.add(tri);
	// return list;
	// }

	private void doTrickle() {
		Collection<WatershedTriangle> triangles = getTriangles();
		stats.reportStatus(logger, "Trickling " + triangles.size() + " triangles");
		
		// debugging only
		// triangles = getTestTriangle(new Coordinate(1579529.794185806,
		// 591340.9937288532));

		trickleTracer = new SteepestDescentTrickler(cdt.getSubdivision(), watershedTIN, triangles, ccf);
		trickleTracer.compute();
		// pitRegions = tricker
		stats.reportStatus(logger, "Trickling computed");
	}

	private void doConnectAndMerge() {
		Collection<WatershedTriangle> triangles = getTriangles();

		WatershedTriangle.saveInitialRegionIDs(triangles);

		// debugging only
		// triangles = getTestTriangle(new Coordinate(1579529.794185806,
		// 591340.9937288532));

		/**
		 * Since pits can be disconnected, and may remain disconnected even after
		 * merging, disconnected region merging must be done after pit merging.
		 */
		PitRegionMerger pitMerger = new PitRegionMerger(trickleTracer.getPitRegions());
		pitMerger.merge();
		pitBorderMidpoints = pitMerger.getMidPoints();

		ConnectedRegionFinder.compute(triangles);

		DisconnectedAreaMerger discAreaMerger = new DisconnectedAreaMerger(triangles);
		discAreaMerger.merge();
		stats.reportStatus(logger, "Disconnected Areas merged");

		BoundarySpikeClipper spikeClipper = new BoundarySpikeClipper(triangles);
		spikeClipper.compute();
		stats.reportStatus(logger, "Boundary Spike clipping completed");

		PinchTriangleFixer pinchFixer = new PinchTriangleFixer(triangles);
		pinchFixer.compute();
		stats.reportStatus(logger, "Pinch Fixing completed");

		stats.reportStatus(logger, "Connecting & Merging computed");
	}

	// MD - currently not used, since water assignment algorithm is not yet
	// functional
//    private void doWaterAssignment() {
//    	WaterTriangleAssigner.compute(getTriangles());
//    	stats.reportStatus(logger, "Water triangles assigned");
//	}

	private void doBoundaryEdgeExtract(ConformingDelaunayTriangulator cdt) {
		wee = new WatershedEdgeExtracter(cdt.getSubdivision(), gf);
		boundaryEdges = wee.getBoundaryEdges();

		WatershedEdgeCoverageSmoother.smooth(boundaryEdges, trimmedHydroEdges,
				gf.getPrecisionModel());

		stats.reportStatus(logger, "Watershed Edges extracted");
	}

	public GeometryFactory getGeometryFactory() {
		return gf;
	}
}

class WatershedTriangleBuilder implements TriangleVisitor {
	private WatershedTIN watershedTIN;
	private List<WatershedTriangle> triangles = new ArrayList<WatershedTriangle>();

	public WatershedTriangleBuilder(WatershedTIN watershedTIN) {
		this.watershedTIN = watershedTIN;
	}

	public void visit(QuadEdge[] edges) {
		triangles.add(new WatershedTriangle(watershedTIN, edges));
	}

	public List<WatershedTriangle> getTriangles() {
		return triangles;
	}
}
