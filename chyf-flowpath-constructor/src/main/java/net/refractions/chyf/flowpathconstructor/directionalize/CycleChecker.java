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
package net.refractions.chyf.flowpathconstructor.directionalize;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.geotools.data.FeatureReader;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.DirectionType;
import net.refractions.chyf.datasource.EcType;
import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.TerminalNode;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.DEdge;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.DGraph;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.DNode;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.EdgeInfo;

/**
 * Simple cycle checker for CHyF flowpath dataset
 * 
 * @author Emily
 *
 */
public class CycleChecker {
	
	private static final Logger logger = LoggerFactory.getLogger(CycleChecker.class.getCanonicalName());

	private DGraph graph = null;
	private IFlowpathDataSource dataSource;
	
	public CycleChecker(IFlowpathDataSource dataSource) {
		this.dataSource = dataSource;
	}
	
	private void createGraph() throws Exception {
		if (this.graph != null) return;
		
		List<EdgeInfo> edges = new ArrayList<>();
		
		List<PreparedPolygon> aois = new ArrayList<>();
		for (Polygon p : dataSource.getAoi()) aois.add(new PreparedPolygon(p));
			
		try(FeatureReader<SimpleFeatureType, SimpleFeature> reader = dataSource.query(Layer.EFLOWPATHS)){
			Name efatt = ChyfDataSource.findAttribute(reader.getFeatureType(), ChyfAttribute.EFTYPE);
			Name diratt = ChyfDataSource.findAttribute(reader.getFeatureType(), ChyfAttribute.DIRECTION);
			while(reader.hasNext()) {
				SimpleFeature sf = reader.next();
				
				LineString ls = ChyfDataSource.getLineString(sf);
				for (PreparedPolygon p : aois) {
					if (p.contains(ls) || p.getGeometry().relate(ls, "1********")) {
						EfType eftype = EfType.parseValue( (Number)sf.getAttribute(efatt) );					
						DirectionType dtype = DirectionType.parseValue((Number)sf.getAttribute(diratt));
						if (dtype == DirectionType.UNKNOWN) throw new Exception("An unknown direction edge type found. Cannot check cycles when direction is unknown");					
						EdgeInfo ei = new EdgeInfo(ls.getCoordinateN(0),
								ls.getCoordinateN(1),
								ls.getCoordinateN(ls.getCoordinates().length - 2),
								ls.getCoordinateN(ls.getCoordinates().length - 1),
								eftype,
								sf.getIdentifier(), ls.getLength(), 
								dtype);
						
						edges.add(ei);
						break;
					}
				}
				
			}
		}
		this.graph = DGraph.buildGraphLines(edges);
	}
	/**
	 * 
	 * @param output link to geopackage containing CHyF data
	 * @return
	 * @throws Exception
	 */
	public boolean checkCycles() throws Exception{
		createGraph();
		return findCycles(graph);
	}
	
	public boolean findCycles(DGraph graph) {
		graph.getEdges().forEach(e->e.setVisited(false));
		for(DEdge f : graph.getEdges()) {
			if(findCycles(f, new HashSet<DEdge>())) {
				return true;
			}
		}
		return false;
	}
	
	private boolean findCycles(DEdge f, HashSet<DEdge> visited) {
		if (f.isVisited()) return false;
		if(visited.contains(f)) {
			logger.error("Cycle found at " + f.toString());
			return true;
		}
		visited.add(f);
		for(DEdge u: f.getNodeA().getEdges()) {
			if (u.getNodeB() == f.getNodeA())
			if(findCycles(u, visited)) {
				return true;
			}
		}
		f.setVisited(true);
		visited.remove(f);
		return false;
	}
	
	//search for invalid sources and sinks
	//for sinks:
	//sinks that sink on waterbody boundaries and are not a terminal node and degree > 1
	//sources that start on a waterbody boundary are not a terminal node and degree > 1
	/**
	 * 
	 * @return array, first element is list of potential sink errors, the second source errors
	 * @throws Exception
	 */
	public List<Point>[] findInvalidSourceSinkNodes() throws Exception {
		createGraph();
		
		List<DNode> sinkstocheck = new ArrayList<>();
		List<DNode> sourcestocheck = new ArrayList<>();
		
		//find sinks and sources with degree > 1
		for (DNode d : graph.getNodes()) {
			if (d.getDegree() == 1) continue;
			
			int incnt = 0;
			int outcnt = 0;
			for (DEdge e : d.getEdges()) {
				if (e.getNodeA() == d) outcnt++;
				if (e.getNodeB() == d) incnt ++;
			}
			
			if (incnt == 0 && outcnt > 1) sourcestocheck.add(d);
			if (outcnt == 0 && incnt > 1) sinkstocheck.add(d);	
		}
		
		//remove any terminal nodes
		List<DNode> toRemove = new ArrayList<>();
		for (TerminalNode n : dataSource.getTerminalNodes()) {
			for (DNode d : sinkstocheck) {
				if (d.getCoordinate().equals2D(n.getPoint().getCoordinate())) toRemove.add(d);
			}
			for (DNode d : sourcestocheck) {
				if (d.getCoordinate().equals2D(n.getPoint().getCoordinate())) toRemove.add(d);
			}
		}
		sinkstocheck.removeAll(toRemove);
		sourcestocheck.removeAll(toRemove);
		
		
		Filter wbFilter = ChyfDataSource.ff.equals(ChyfDataSource.ff.property(ChyfAttribute.ECTYPE.getFieldName()), ChyfDataSource.ff.literal(EcType.WATER.getChyfValue()));

		//report any that are on waterbody boundaries
		List<Point> sinkwarnings = new ArrayList<>();
		for (DNode n : sinkstocheck) {
			ReferencedEnvelope env = new ReferencedEnvelope(new Envelope(n.getCoordinate(), n.getCoordinate()), dataSource.getCoordinateReferenceSystem());
			try(FeatureReader<SimpleFeatureType, SimpleFeature> reader = dataSource.query(Layer.ECATCHMENTS, env, wbFilter)) {
				while(reader.hasNext()) {
					SimpleFeature sf = reader.next();
					Geometry g = (Geometry)sf.getDefaultGeometry();
					if (g.intersects(n.toGeometry())){
						sinkwarnings.add((Point)n.toGeometry());
					}
				}
			}
		}
		List<Point> sourcewarnings = new ArrayList<>();
		for (DNode n : sourcestocheck) {
			ReferencedEnvelope env = new ReferencedEnvelope(new Envelope(n.getCoordinate(), n.getCoordinate()), dataSource.getCoordinateReferenceSystem());
			try(FeatureReader<SimpleFeatureType, SimpleFeature> reader = dataSource.query(Layer.ECATCHMENTS, env, wbFilter)) {
				while(reader.hasNext()) {
					SimpleFeature sf = reader.next();
					Geometry g = (Geometry)sf.getDefaultGeometry();
					if (g.intersects(n.toGeometry())){
						sourcewarnings.add((Point)n.toGeometry());
					}
				}
			}
		}
		
		return new List[]{sinkwarnings, sourcewarnings};
	}
	
	
}
