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
package net.refractions.chyf.flowpathconstructor.directionalize.graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.io.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfLogger;
import net.refractions.chyf.datasource.DirectionType;
import net.refractions.chyf.datasource.EfType;
import net.refractions.chyf.flowpathconstructor.directionalize.Directionalizer;

/**
 * Computes "bridge" edges in a graph.  Sets
 * the isBridge flag of these edges.  A bridge edge
 * is an edge that if removed from the graph increases the
 * number of distinct connected components in the graph.
 * 
 * @author Emily
 *
 */
public class BridgeFinder {
	
//	private static final Logger logger = LoggerFactory.getLogger(BridgeFinder.class.getCanonicalName());

    private int cnt;          // counter

    public BridgeFinder() {
    	
    }
    
    /**
     * 
     * @param graph graph to compute bridges for
     * @param source any node in the graph
     * @throws ParseException 
     * @throws IOException 
     */
    public void computeBridges(DGraph graph, DNode source) throws IOException, ParseException {
    	cnt = 0;
    	graph.nodes.forEach(n->{
    		n.bridgeMin = -1;
    		n.bridgePre = -1;
    	});
    	source.bridgePre = -1;
        	
//    	logger.info("bridges: " + graph.edges.size() + ":" + graph.nodes.size());
//    	for (DEdge e : graph.edges) {
//    		logger.info(e.toString());
//    	}
    	dfs(source, source);
    }
    
    //TODO: make not recursive
    private void dfs(DNode u, DNode v) throws IOException, ParseException {
//		logger.info("Processing node: " + u.toString() + " " + v.toString());

    	v.bridgePre = cnt++;
    	v.bridgeMin = v.bridgePre;
    	for (DEdge e : v.getEdges()) {
    		DNode w = e.getOtherNode(v);
    		if (w == null) {
    			ChyfLogger.INSTANCE.logError(ChyfLogger.Process.DIRECTION," Could not compute bridge edges in graph.", e.getNodeA().toGeometry());
    			throw new RuntimeException("Could not compute bridge edges in graph (null pointer exception): " + e.getNodeA().toGeometry().toText());
    		}
    		if (w.bridgePre == -1) {
    			dfs(v, w);
    			v.bridgeMin = Math.min(v.bridgeMin, w.bridgeMin);
    			if (w.bridgeMin == w.bridgePre) {
    				e.setBridge();
    			}
    		}else if (w != u){
    			v.bridgeMin = Math.min(v.bridgeMin, w.bridgePre);
    		}
    	}     
    }

}