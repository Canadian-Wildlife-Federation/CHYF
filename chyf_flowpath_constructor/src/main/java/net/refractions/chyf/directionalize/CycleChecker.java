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
package net.refractions.chyf.directionalize;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.geotools.data.simple.SimpleFeatureReader;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.DirectionType;
import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.directionalize.graph.DEdge;
import net.refractions.chyf.directionalize.graph.DGraph;
import net.refractions.chyf.directionalize.graph.DNode;
import net.refractions.chyf.directionalize.graph.EdgeInfo;

/**
 * Simple cycle checker for CHyF flowpath dataset
 * 
 * @author Emily
 *
 */
public class CycleChecker {
	
	private static final Logger logger = LoggerFactory.getLogger(CycleChecker.class.getCanonicalName());

	/**
	 * 
	 * @param output link to geopackage containing CHyF data
	 * @return
	 * @throws Exception
	 */
	public boolean checkCycles(Path output) throws Exception{
		List<EdgeInfo> edges = new ArrayList<>();

		try(FlowpathGeoPackageDataSource dataSource = new FlowpathGeoPackageDataSource(output)){
			
			List<PreparedPolygon> aois = new ArrayList<>();
			for (Polygon p : dataSource.getAoi()) aois.add(new PreparedPolygon(p));
			
			try(SimpleFeatureReader reader = dataSource.getFeatureReader(Layer.EFLOWPATHS,null, null)){
				Name efatt = ChyfDataSource.findAttribute(reader.getFeatureType(), ChyfAttribute.EFTYPE);
				Name diratt = ChyfDataSource.findAttribute(reader.getFeatureType(), ChyfAttribute.DIRECTION);
				while(reader.hasNext()) {
					SimpleFeature sf = reader.next();
					
					LineString ls = ChyfDataSource.getLineString(sf);
					for (PreparedPolygon p : aois) {
						if (p.contains(ls) || p.getGeometry().relate(ls, "1********")) {
							EfType eftype = EfType.parseValue((Integer)sf.getAttribute(efatt));					
							DirectionType dtype = DirectionType.parseValue((Integer)sf.getAttribute(diratt));
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
		}
					
		DGraph graph = DGraph.buildGraphLines(edges);
		
		//TODO: add an options and write these to the datastore
//		List<DNode> sources = new ArrayList<>();
//		List<DNode> ssinks = new ArrayList<>();
//		for (DNode n : graph.nodes) {
//			boolean in = true;
//			boolean out = true;
//			for (DEdge e : n.getEdges()) {
//				if (e.getType() == EfType.BANK) {
//					in = false;
//					out = false;
//				}
//				if (e.getNodeA() == n) {
//					in = false;
//				}
//				if (e.getNodeB() == n) {
//					out = false;
//				}
//			}
//			if (in) {
//				sources.add(n);
//			}
//			if (out) {
//				ssinks.add(n);
//			}
//		}
//		System.out.println("sources");
//		sources.forEach(n->n.print());
//		System.out.println("sinks");
//		ssinks.forEach(n->n.print());
		
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
}
