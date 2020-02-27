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
package net.refractions.chyf.directionalize.graph;

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
	
    private int cnt;          // counter

    public BridgeFinder() {
    	
    }
    
    /**
     * 
     * @param graph graph to compute bridges for
     * @param source any node in the graph
     */
    public void computeBridges(DGraph graph, DNode source) {
    	cnt = 0;
    	graph.nodes.forEach(n->{
    		n.bridgeMin = -1;
    		n.bridgePre = -1;
    	});
    	source.bridgePre = -1;
    	
    	dfs(source, source);
    }
    
    //TODO: make not recursive
    private void dfs(DNode u, DNode v) {
    	v.bridgePre = cnt++;
    	v.bridgeMin = v.bridgePre;
    	for (DEdge e : v.getEdges()) {
    		DNode w = e.getOtherNode(v);
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