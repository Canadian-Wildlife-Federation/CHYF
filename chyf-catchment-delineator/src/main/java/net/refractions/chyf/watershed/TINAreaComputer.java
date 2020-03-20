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

import java.util.List;

import org.locationtech.jts.algorithm.Area;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.triangulate.quadedge.QuadEdgeSubdivision;
import org.locationtech.jts.util.Debug;

/**
 * Computes the total 2D and 3D area for all triangles in a TIN.
 * 
 * @author Martin Davis
 */
public class TINAreaComputer {
	/**
	 * Computes the ratio of the 3D:2D areas of a TIN. This should always be >= 1
	 * 
	 * @param subdiv the TIN to compute
	 * @return the ratio of the 3D to 2D area.
	 */
	public static double computeAreaRatio(QuadEdgeSubdivision subdiv) {
		TINAreaComputer ac = new TINAreaComputer(subdiv);
		double[] area = ac.compute(subdiv);
		double ratio32 = area[1] / area[0];
		Debug.println("TINAreaComputer: 2D area = " + area[0] + "   3D area = " + area[1] + "   Ratio = " + ratio32);
		return ratio32;
	}

	private TINAreaComputer(QuadEdgeSubdivision subdiv) {
		compute(subdiv);
	}

	private double[] compute(QuadEdgeSubdivision subdiv) {
		@SuppressWarnings("unchecked")
		List<Coordinate[]> triPtList = subdiv.getTriangleCoordinates(false);

		double totalArea2D = 0.0;
		double totalArea3D = 0.0;
		for (Coordinate[] triPt : triPtList) {
			double aread2D = Area.ofRing(triPt);

			double aread3D = area3D(triPt);

			totalArea2D += aread2D;
			totalArea3D += aread3D;

		}
		return new double[] { totalArea2D, totalArea3D };
	}

	/**
	 * Computes the 3D area of a triangle
	 * 
	 * @param pts an array of at least 3 Coordinates
	 * @return the 3D area
	 */
	public static double area3D(Coordinate[] pts) {
		// side vectors a and b
		double ax = pts[1].x - pts[0].x;
		double ay = pts[1].y - pts[0].y;
		double az = pts[1].getZ() - pts[0].getZ();

		double bx = pts[2].x - pts[0].x;
		double by = pts[2].y - pts[0].y;
		double bz = pts[2].getZ() - pts[0].getZ();

		// cross-product = a x b
		double crossx = ay * bz - by * az;
		double crossy = az * bx - ax * bz;
		double crossz = ax * by - ay * bx;

		// tri area = 1/2 * | a x b |
		double absSq = crossx * crossx + crossy * crossy + crossz * crossz;
		double area3D = Math.sqrt(absSq) / 2;

		return area3D;
	}
}
