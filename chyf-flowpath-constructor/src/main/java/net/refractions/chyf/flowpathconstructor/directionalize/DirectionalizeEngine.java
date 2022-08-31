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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.geotools.data.FeatureReader;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.prep.PreparedPolygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfLogger;
import net.refractions.chyf.ChyfLogger.Process;
import net.refractions.chyf.ExceptionWithLocation;
import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.datasource.DirectionType;
import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.datasource.FlowDirection;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.flowpathconstructor.ChyfProperties;
import net.refractions.chyf.flowpathconstructor.FlowpathArgs;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.FlowpathPostGisLocalDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.TerminalNode;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.DEdge;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.DGraph;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.DNode;
import net.refractions.chyf.flowpathconstructor.directionalize.graph.EdgeInfo;

/**
 * Main class for computing direction on dataset
 * @author Emily
 *
 */
public class DirectionalizeEngine {
	
	static final Logger logger = LoggerFactory.getLogger(DirectionalizeEngine.class.getCanonicalName());

	public static void doWork(Path output, ChyfProperties properties) throws Exception {
		try(FlowpathGeoPackageDataSource dataSource = new FlowpathGeoPackageDataSource(output)){
			try {
				doWork(dataSource, properties);
			}catch (ExceptionWithLocation ex) {
				ChyfLogger.INSTANCE.logException(ChyfLogger.Process.DIRECTION, ex);
				throw ex;
			}catch (Exception ex) {
				ChyfLogger.INSTANCE.logException(ChyfLogger.Process.DIRECTION, ex);
				throw ex;
			}
		}
		
		
	}
	
	public static void doWork(IFlowpathDataSource dataSource, ChyfProperties properties) throws Exception {
		if (properties == null) properties = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());

		CoordinateReferenceSystem sourceCRS = null;	

		List<EdgeInfo> edges = new ArrayList<>();
		List<EdgeInfo> banks = new ArrayList<>();

		List<PreparedPolygon> aois = new ArrayList<>();
		for (Polygon p : dataSource.getAoi()) {
			aois.add(new PreparedPolygon(p));
		}

		logger.info("loading flowpaths");
		
		try(FeatureReader<SimpleFeatureType, SimpleFeature> reader = dataSource.query(Layer.EFLOWPATHS)){
			sourceCRS = reader.getFeatureType().getCoordinateReferenceSystem();
			Name eftypeatt = ChyfDataSource.findAttribute(reader.getFeatureType(), ChyfAttribute.EFTYPE);
			Name direatt = ChyfDataSource.findAttribute(reader.getFeatureType(), ChyfAttribute.DIRECTION);
			
			while(reader.hasNext()) {
				SimpleFeature sf = reader.next();
				LineString ls = ChyfDataSource.getLineString(sf);
				
				for (PreparedPolygon p : aois) {
					if (p.contains(ls) || p.getGeometry().relate(ls, "1********")) {
						EfType eftype = EfType.parseValue(((Number)sf.getAttribute(eftypeatt)).intValue());
						DirectionType dtype = DirectionType.parseValue( (Number)sf.getAttribute(direatt) );
						
						EdgeInfo ei = new EdgeInfo(ls.getCoordinateN(0),
								ls.getCoordinateN(1),
								ls.getCoordinateN(ls.getCoordinates().length - 2),
								ls.getCoordinateN(ls.getCoordinates().length - 1),
								eftype,
								sf.getIdentifier(), ls.getLength(), 
								dtype);

						
						if (eftype == EfType.BANK) {
							banks.add(ei); //exclude these from the main graph
						}else {
							edges.add(ei);	
						}		
						break;
					}
				}
			}
		}
		
		//create graph
		logger.info("build graph");
		DGraph graph = DGraph.buildGraphLines(edges);
		
//		//directionalized bank edges
//		logger.info("processing bank edges");
//		HashSet<Coordinate> nodec = new HashSet<>();
//		for(DNode d : graph.nodes) nodec.add(d.getCoordinate());
//		Set<FeatureId> bankstoflip = new HashSet<>();
//		for (EdgeInfo b : banks) {
//			if (nodec.contains(b.getEnd())) {
//			}else if (nodec.contains(b.getStart())) {
//				//flip
//				bankstoflip.add(b.getFeatureId());
//			}else {
//				throw new Exception("Bank flowpath does not intersect flow network: " + b.getStart());
//			}
//		}
//		
//		dataSource.flipFlowEdges(bankstoflip, banks.stream().map(e->e.getFeatureId()).collect(Collectors.toList()));
		
		//find sink nodes
		logger.info("locating sink nodes");
		List<Coordinate> sinks = getSinkPoints(dataSource, graph);
//		for (Coordinate s : sinks) {
//			System.out.println("POINT(" + s.x + " " + s.y + ")");
//		}
		
		//directionalize dataset
		logger.info("directionalizing network");
		Directionalizer dd = new Directionalizer(sourceCRS, properties);
		dd.directionalize(graph, sinks);

		//flip edges
		logger.info("saving results");
		dataSource.flipFlowEdges(dd.getFeaturesToFlip(), dd.getProcessedFeatures());
		
		
		logger.info("checking output for cycles");
		CycleChecker checker = new CycleChecker(dataSource);
		if (checker.checkCycles()) {
			throw new Exception("Dataset contains cycles after directionalization.");
		}
		
		logger.info("checking output for invalid source/sink nodes");
		List<Point>[] warnings = checker.findInvalidSourceSinkNodes();
		for (Point p : warnings[0]) {
			ChyfLogger.INSTANCE.logWarning(Process.DIRECTION, "Potential sink error", p);
		}
		for (Point p : warnings[1]) {
			ChyfLogger.INSTANCE.logWarning(Process.DIRECTION, "Potential source error", p);
		}
	}

	/*
	 * Compute sinks points as :
	 * - any boundary points with flowdirection of out
	 * - any known direction edges that form a sink
	 * - and flow edge that intersects the coastline
	 * 
	 */
	private static List<Coordinate> getSinkPoints(IFlowpathDataSource source, DGraph graph) throws Exception {
		List<Coordinate> sinks = new ArrayList<>();
		for (TerminalNode p : source.getTerminalNodes()) {
			if ( p.getDirection() == FlowDirection.OUTPUT ) {
				if (!sinks.contains(p.getPoint().getCoordinate())) sinks.add(p.getPoint().getCoordinate());
			}
		}
		
		//add coastline sinks
		Set<Coordinate> clc = new HashSet<>();
		try(FeatureReader<SimpleFeatureType, SimpleFeature> reader = source.query(Layer.SHORELINES)){
			if (reader != null) {
				while(reader.hasNext()) {
					SimpleFeature sf = reader.next();
					LineString ls = ChyfDataSource.getLineString(sf);
					for (Coordinate c : ls.getCoordinates()) clc.add(c);
				}
			}
		}
		
		//now lets search the graph for sink points based on known direction
		for (DNode node : graph.getNodes()) {
			boolean issink = true;
			if (clc.contains(node.getCoordinate())) {
				//sink node
			}else {
				//if all in edges are directionalized && all sink at this node
				for (DEdge e : node.getEdges()) {
					if (e.getDType() == DirectionType.UNKNOWN) {
						issink = false;
						break;
					}else if (e.getNodeB() != node) {
						issink = false;
						break;
					}
				}
			}
			if (issink && !sinks.contains(node.getCoordinate())) sinks.add(node.getCoordinate());
		}
		
		Set<Coordinate> cpoints = source.getOutputConstructionPoints();
		for(DNode node : graph.getNodes()) {
			if (node.getDegree() == 1 && cpoints.contains(node.getCoordinate()) && !sinks.contains(node.getCoordinate())) {
				sinks.add(node.getCoordinate());
			}			
		}
		return sinks;
	}
	
	public static void main(String[] args) throws Exception {		
		FlowpathArgs runtime = new FlowpathArgs("DirectionalizeEngine");
		if (!runtime.parseArguments(args)) return;
			
		long now = System.nanoTime();
		IFlowpathDataSource dataSource = null;
		try{
			if (runtime.isGeopackage()) {
				Path input = Paths.get(runtime.getInput());
				Path output = Paths.get(runtime.getOutput());
				ChyfGeoPackageDataSource.prepareOutput(input, output);
				
				dataSource = new FlowpathGeoPackageDataSource(output);
			}else if (runtime.isPostigs()){
				if (!runtime.hasAoi()) return;
				dataSource = new FlowpathPostGisLocalDataSource(runtime.getDbConnectionString(), runtime.getInput(), runtime.getOutput());
				((FlowpathPostGisLocalDataSource)dataSource).setAoi(runtime.getAoi());
			}
			ChyfProperties prop = runtime.getPropertiesFile();
			if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			DirectionalizeEngine.doWork(dataSource, prop);
			dataSource.finish();
		}finally {
			if (dataSource != null) dataSource.close();
		}
		long then = System.nanoTime();
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
}