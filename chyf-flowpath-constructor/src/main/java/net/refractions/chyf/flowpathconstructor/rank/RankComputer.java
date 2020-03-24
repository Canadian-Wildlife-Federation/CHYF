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
package net.refractions.chyf.flowpathconstructor.rank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.linearref.LengthIndexedLine;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.flowpathconstructor.ChyfProperties;
import net.refractions.chyf.flowpathconstructor.ChyfProperties.Property;
import net.refractions.chyf.flowpathconstructor.datasource.ChyfAngle;

/**
 * Compute rank by looking at angles.
 * 
 * @author Emily
 *
 */
public class RankComputer {

	static final Logger logger = LoggerFactory.getLogger(RankComputer.class.getCanonicalName());
	
	private ChyfAngle angleComputer;
	private ChyfDataSource source;
	private ChyfProperties properties;
	
	private CoordinateReferenceSystem sourceCRS;
	
	public RankComputer(CoordinateReferenceSystem sourceCRS, ChyfDataSource source, ChyfProperties properties) {
		this.angleComputer = new ChyfAngle(sourceCRS);
		this.source = source;
		this.sourceCRS = sourceCRS;
		this.properties = properties;
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

		boolean inwb = true;
		for (REdge out : node.getOutEdges()) {
			if (out.getType() != EfType.SKELETON) inwb = false;
			
			double angle = 0;
			for (REdge in : node.getInEdges()) {
				double a = computeAngle(in.getToC(), node.getCoordinate(), out.getFromC());
				if (a > angle) {
					angle = a;
				}
			}
			angles.put(out,  angle/ Math.PI);
		}
		

		if ( inwb ) {
			//compute channel width
			//find waterbody that contains the node
			Polygon wb = null;
			FeatureId wbfid = null;
			Point nodep = ((new GeometryFactory())).createPoint(node.getCoordinate());
			
			try(SimpleFeatureReader reader = source.query(Layer.ECATCHMENTS, new ReferencedEnvelope(nodep.getEnvelopeInternal(), sourceCRS), source.getWbTypeFilter())){
				while(reader.hasNext()) {
					SimpleFeature sf = reader.next();
					Polygon p = ChyfDataSource.getPolygon(sf);
					if (p.intersects(nodep)) {
						wb = p;
						wbfid = sf.getIdentifier();
						break;
					}
				}
			}
			if (wb == null) {
				throw new Exception("waterbody could not be found for node: " + node.toString());
			}	
			List<LineString> wbparts = new ArrayList<>();
			wbparts.add(wb.getExteriorRing());
			for (int i = 0; i < wb.getNumInteriorRing();i ++) wbparts.add(wb.getInteriorRingN(i));
				
			List<LineString> tempparts = new ArrayList<>();
				
			//break waterbody parts where they intersect with another waterbody or the coastline
			try(SimpleFeatureReader reader = source.query(Layer.ECATCHMENTS, new ReferencedEnvelope(wb.getEnvelopeInternal(), sourceCRS), source.getWbTypeFilter())){
				while(reader.hasNext()) {
					SimpleFeature sf = reader.next();
					if (sf.getIdentifier().equals(wbfid)) continue;
					Polygon p = ChyfDataSource.getPolygon(sf);
					
					tempparts.clear();
					for (LineString l : wbparts) {
						if (l.getEnvelopeInternal().intersects(p.getEnvelopeInternal()) && l.intersects(p)) {
							Geometry g = l.difference(p);
							for (int i = 0; i < g.getNumGeometries(); i ++) {
								LineString ls = ((LineString)g.getGeometryN(i));
								if (!ls.isEmpty()) tempparts.add(ls);
							}	
						}else {
							tempparts.add(l);
						}
					}
					wbparts.clear();
					wbparts.addAll(tempparts);
					
				}
			}
			//do the some thing with the coastline
			try(SimpleFeatureReader reader = source.query(Layer.SHORELINES, new ReferencedEnvelope(wb.getEnvelopeInternal(), sourceCRS), null)){
				if (reader != null) {
					while(reader.hasNext()) {
						SimpleFeature sf = reader.next();
						LineString p = ChyfDataSource.getLineString(sf);
						
						tempparts.clear();
						for (LineString l : wbparts) {
							if (l.getEnvelopeInternal().intersects(p.getEnvelopeInternal())) {
								Geometry g = l.difference(p);
								for (int i = 0; i < g.getNumGeometries(); i ++) {
									LineString ls = ((LineString)g.getGeometryN(i));
									if (!ls.isEmpty()) tempparts.add(ls);
								}	
							}else {
								tempparts.add(l);
							}
						}
						wbparts.clear();
						wbparts.addAll(tempparts);
						
					}
				}
			}
				
			
			HashMap<REdge, Double> widths = new HashMap<>();
			for (REdge out : node.getOutEdges()) {
			
				LineString ls = getOutGeometry(out);
				if (ls == null) continue; //line goes outside of waterbody
				
				//closest points on different rings
				double target = ls.getLength() / 2.0;
				LengthIndexedLine ll = new LengthIndexedLine(ls);
				Point center = wb.getFactory().createPoint( ll.extractPoint(target) );
				
				Coordinate p1 = null;
				LineString f1 = null;
				Double d1 = Double.MAX_VALUE;
					
				Coordinate p2 = null;
				LineString f2 = null;
				Double d2 = Double.MAX_VALUE;
				
				for (LineString lls : wbparts) {
					Double dt = lls.distance(center);
					if (dt < d1) {
						if (d1 < d2) {
							d2 = d1;
							p2 = p1;
							f2 = f1;
						}
						d1 = dt;
						p1 = DistanceOp.nearestPoints(lls,  center)[0];
						f1 = lls;
							
						
					}else if (dt < d2) {
						
						d2 = dt;
						p2 = DistanceOp.nearestPoints(lls,  center)[0];
						f2 = lls;
					}
				}
						
				if (f1 == null || f2 == null) {
					//single lake case
					break;
				}
				Coordinate[] closest =  DistanceOp.nearestPoints(f1,  ls);
				Coordinate[] closest2 =  DistanceOp.nearestPoints(f2,  ls);
				p1 = closest [0];
				p2 = closest2 [0];
					
				LineString item = wb.getFactory().createLineString(new Coordinate[] {p1,p2});
					
				Geometry g1 = item.difference(wb);
				if (g1.getLength() > item.getLength() * 0.1) {
					break;
				}
				
//				System.out.println(item.toText());
				//look at the angle generate between the linestring item
				//and where it crosses an eflowpath and generate warning if acute
				LineSegment toText = new LineSegment(p1, p2);
				for (int i = 1; i < ls.getCoordinates().length; i ++) {
					LineSegment l1 = new LineSegment(ls.getCoordinateN(i-1), ls.getCoordinateN(i));
					Coordinate pnt = l1.intersection(toText);
					if (pnt == null) continue;
					//compute angle between p1; pnt and 
					double angle = angleComputer.angle(p1, pnt, ls.getCoordinateN(i)) ;
					if (angle < 0.872665 || angle > (Math.PI - 0.872665)) {  //0.872665=50degrees
						logger.warn("Channel width intersects channel at acute angle: " + item.toString());
					}
					break;	
				}
				
				double width = -1*p1.distance(p2);					
				widths.put(out, width);	
			}
			if (widths.size() == angles.size()) {
				//every edge also has channel
				double total = widths.values().stream().mapToDouble(Double::doubleValue).sum();
				for (Entry<REdge, Double> e : widths.entrySet()) {
					e.setValue(e.getValue() / total);
				}
				
				double p = properties.getProperty(Property.RANK_CHANNEL_WEIGHT);
				for (Entry<REdge, Double> e : angles.entrySet()) {
					e.setValue(e.getValue() * (1.0 - p) + widths.get(e.getKey()) * p);
				}
			}else {
				//we don't have channels for every edge so only use angles
			}
		}
		
		//find min angle and set others to secondary
		REdge e = angles.entrySet().stream().max(Map.Entry.comparingByValue()).get().getKey();

		for (REdge out : node.getOutEdges()) {
			if (out == e) continue;
			out.setRank(RankType.SECONDARY);
			RNode oout = out.getToNode();
			while(true) {
				if (oout.getOutDegree() == 1 && oout.getInDegree() == 1) {
					REdge e1 = oout.getOutEdges().get(0);
					e1.setRank(RankType.SECONDARY);
					oout = e1.getToNode();
				}else if (oout.getOutDegree() == 1) {
					int bcnt = 0;
					int ocnt = 0;
					for (REdge in : oout.getInEdges()) {
						if (in.getType() == EfType.BANK) {
							bcnt ++;
						}else {
							ocnt ++;
						}
					}
					if (bcnt >= 1 && ocnt == 1) {
						REdge e1 = oout.getOutEdges().get(0);
						e1.setRank(RankType.SECONDARY);
						oout = e1.getToNode();
					}else {
						break;
					}
				} else {
					break;
				}
				
			}
		}
		
	}
	
	private LineString getOutGeometry(REdge e) {
		List<LineString> mm = new ArrayList<>();
		mm.add(e.getLineString());
		
		while(true) {
			if (e.getToNode().getInDegree() == 1 && e.getToNode().getOutDegree() == 1) {
				if (e.getToNode().getInEdges().get(0).getType().equals(e.getToNode().getOutEdges().get(0).getType())) {
					break;
				}else {
					
					e = e.getToNode().getOutEdges().get(0);
					if (e.getType() != EfType.SKELETON) return null;
					mm.add(e.getLineString());
				}
			}else if (e.getToNode().getOutDegree() == 1) {
				//if one of those in edges is bank then continue else stop
				//we don't want to push through to a different waterbody
				int bcnt = 0;
				int ocnt = 0;
				for (REdge in : e.getToNode().getInEdges()) {
					if (in.getType() == EfType.BANK) {
						bcnt ++;
					}else {
						ocnt ++;
					}
				}
				if (bcnt >= 1 && ocnt == 1) {
					e = e.getToNode().getOutEdges().get(0);
					mm.add(e.getLineString());
				}else {
					break;
				} 
			}else {
				break;
			}
		}
		if (mm.size() == 1) return mm.get(0);
		
		List<Coordinate> cs = new ArrayList<>();
		cs.add(mm.get(0).getCoordinateN(0));
		for (LineString ls : mm) {
			for (int i = 1; i < ls.getCoordinates().length; i ++) {
				cs.add(ls.getCoordinateN(i));
			}
		}
		LineString lls = e.getLineString().getFactory().createLineString(cs.toArray(new Coordinate[cs.size()]));
		return lls;
	}
	
	private double computeAngle(Coordinate c0, Coordinate c1, Coordinate c2) throws Exception{
		double a = angleComputer.angle(c0, c1, c2);
		if (a < 0) a += 2*Math.PI;
		if (a > Math.PI) a = (2*Math.PI) - a;
		return a;
	}
	
}


