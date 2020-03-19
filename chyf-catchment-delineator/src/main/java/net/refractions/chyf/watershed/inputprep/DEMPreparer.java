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

import org.locationtech.jts.algorithm.ConvexHull;
import org.locationtech.jts.algorithm.locate.IndexedPointInAreaLocator;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Location;
import org.locationtech.jts.geom.Polygon;

import net.refractions.chyf.watershed.model.WatershedTIN;
import net.refractions.chyf.watershed.model.WatershedVertex;

/**
 * Prepares the DEM points (including basic points and respected points).
 * Provides the convex hull of the input points. Allows specifiying a fence to
 * restrict which points are used. The prepared points are returned as a set of
 * {@link WatershedVertex}es
 * 
 * @author Martin Davis
 * @version 1.0
 */

public class DEMPreparer {
	private Collection<Coordinate> demCoords; // Collection<Coordinate>
	private Collection<Coordinate> respectedDEMCoords; // Collection<Coordinate>

	private Polygon fenceGeom = null;
	private GeometryFactory gf;

	// computed data
	private IndexedPointInAreaLocator fenceArea;

	// prepared input data
	private List<WatershedVertex> demVertices;

	public DEMPreparer(Collection<Coordinate> demCoords, Collection<Coordinate> respectedDEMCoords,
			WatershedTIN watershedTIN, GeometryFactory gf) {
		this.demCoords = demCoords;
		this.respectedDEMCoords = respectedDEMCoords;
		this.gf = gf;
		prepare();
	}

	/**
	 * Sets the fence (bounding area) for all DEM points and linear constraints).
	 * The fence must be a convex polygon. (This implies no holes.)
	 * 
	 * @param fence the polygon defining the fence
	 */
	public void setFence(Polygon fence) {
		fenceGeom = fence;
		// should check convexity here
		fenceArea = new IndexedPointInAreaLocator(fenceGeom);
	}

	/**
	 * Gets the list of prepared DEM vertices.
	 * 
	 * @return a List of {@link WatershedVertex}es
	 */
	public List<WatershedVertex> getDEMVertices() {
		return demVertices;
	}

	/**
	 * Computes the convex hull of the DEM points (including the respected DEM
	 * points). This is be used to "trim" the hydro constraint edges so that all
	 * constraint segments lie within the hull.
	 * 
	 * @return the convex hull of the DEM points
	 */
	public Polygon getDEMConvexHull() {
		Coordinate[] coords = new Coordinate[demCoords.size() + respectedDEMCoords.size()];
		int i = 0;
		for (Coordinate c : demCoords) {
			coords[i++] = c;
		}
		for (Coordinate c : respectedDEMCoords) {
			coords[i++] = c;
		}

		ConvexHull hullFinder = new ConvexHull(coords, gf);
		Polygon hull = (Polygon) hullFinder.getConvexHull();
		return hull;
	}

	private boolean isInFence(Coordinate pt) {
		if (fenceArea != null)
			return fenceArea.locate(pt) == Location.INTERIOR;
		// default is to accept all points
		return true;
	}

	private void prepare() {
		// prepare the DEM vertices
		demVertices = new ArrayList<WatershedVertex>();
		extractVerticesFromDEMPoints(demCoords, false, demVertices);
		if (respectedDEMCoords != null)
			extractVerticesFromDEMPoints(respectedDEMCoords, true, demVertices);
	}

	private void extractVerticesFromDEMPoints(Collection<Coordinate> coords, boolean isRespected,
			Collection<WatershedVertex> verts) {
		for (Coordinate pt : coords) {
			// only need to check fence if one was set, since otherwise processArea contains
			// all DEM pts
			if (!isInFence(pt))
				continue;

			checkValidZ(pt);

			WatershedVertex v = new WatershedVertex(pt);
			v.setRespected(isRespected);
			verts.add(v);
		}
	}

	private void checkValidZ(Coordinate p) {
		if (Double.isNaN(p.getZ()))
			throw new IllegalArgumentException("Coordinate has an invalid Z value (NaN)");
	}

}