/*
 * Copyright 2019 Government of Canada
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
 */
package net.refractions.chyf.rank;

import java.util.HashMap;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import net.refractions.chyf.datasource.ChyfAngle;
import net.refractions.chyf.datasource.ChyfDataSource.RankType;

/**
 * Compute rank by looking at angles.
 * 
 * @author Emily
 *
 */
public class RankComputer {

	private ChyfAngle angleComputer;
	
	public RankComputer(CoordinateReferenceSystem sourceCRS) {
		this.angleComputer = new ChyfAngle(sourceCRS);
	}
	
	public void computeRank(RGraph graph) throws Exception {
		for (RNode node : graph.getNodes()) {
			if (node.getOutDegree() > 1) {
				//compute primary/secondary
				computePrimarySecondary(node);
			}
		}
	}
	
	
	private void computePrimarySecondary(RNode node) throws Exception {
		HashMap<REdge, Double> angles = new HashMap<>();
		
		for (REdge out : node.getOutEdges()) {
			double angle = 0;
			for (REdge in : node.getInEdges()) {
				double a = computeAngle(in.getToC(), node.getCoordinate(), out.getFromC());
				
				if (Math.PI - a < Math.PI - angle) {
					angle = (Math.PI-a);
				}
			}
			angles.put(out,  angle);
		}
		//find min angle and set that to secondary
		REdge e = angles.entrySet().stream().min(Map.Entry.comparingByValue()).get().getKey();
		
		for (REdge out : node.getOutEdges()) {
			if (out == e) continue;
			out.setRank(RankType.SECONDARY);
			RNode oout = out.getToNode();
			while(oout.getOutDegree() == 1 && oout.getInDegree() == 1) {
				REdge e1 = oout.getOutEdges().get(0);
				e1.setRank(RankType.SECONDARY);
				oout = e1.getToNode();
				
			}
		}
		
		
	}
	
	private double computeAngle(Coordinate c0, Coordinate c1, Coordinate c2) throws Exception{
		double a = angleComputer.angle(c0, c1, c2);
//		double a = Angle.toDegrees( Angle.angleBetweenOriented(c0, c1, c2) );
		if (a < 0) a += 2*Math.PI;
		if (a > Math.PI) a = (2*Math.PI) - a;
		return a;
	}
}
