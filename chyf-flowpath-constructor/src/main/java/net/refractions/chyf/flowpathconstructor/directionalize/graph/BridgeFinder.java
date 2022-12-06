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
import java.util.List;
import java.util.Stack;

import org.locationtech.jts.io.ParseException;

import net.refractions.chyf.ChyfLogger;

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
	
	//private static final Logger logger = LoggerFactory.getLogger(BridgeFinder.class.getCanonicalName());

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
        	
    	dfs2(source, source);    	
    }

    //no recursive version of function
    private void dfs2(DNode us, DNode vs) throws IOException, ParseException {
    	
    	Stack<TaskDetails> tasks = new Stack<>();
    	
    	tasks.push(new TaskDetails(us, vs, null, null, 1)); 
    	
    	while(!tasks.isEmpty()) {
    		
    		TaskDetails next = tasks.pop();
        	if (next.tasktype == 1 ) {
    			next.v.bridgePre = cnt++;
    			next.v.bridgeMin = next.v.bridgePre;
   			
    			List<DEdge> items = next.v.getEdges();
    			for (int i = items.size() - 1; i >= 0; i --) {
    				DEdge e = items.get(i);
    	    		
    	    		DNode w = e.getOtherNode(next.v);
    	    		if (w == null) {
    	    			ChyfLogger.INSTANCE.logError(ChyfLogger.Process.DIRECTION," Could not compute bridge edges in graph.", e.getNodeA().toGeometry());
    	    			throw new RuntimeException("Could not compute bridge edges in graph (null pointer exception): " + e.getNodeA().toGeometry().toText());
    	    		}
    	    		tasks.push(new TaskDetails(next.u, next.v, w, e, 4));
    	    		
    	    	}
        	}else if (next.tasktype == 2) {
        		next.v.bridgeMin = Math.min(next.v.bridgeMin, next.w.bridgePre);
    		}else if (next.tasktype == 3) {
    			next.v.bridgeMin = Math.min(next.v.bridgeMin, next.w.bridgeMin);
	    		if (next.w.bridgeMin == next.w.bridgePre) {
	    			next.e.setBridge();
	    		}
    		}else if (next.tasktype == 4) {
	    		if (next.w.bridgePre == -1) {
	    			tasks.push(new TaskDetails(next.u, next.v, next.w, next.e, 3));
    	    		tasks.push(new TaskDetails(next.v, next.w, null, null, 1));
    	    		
    	    	}else if (next.w != next.u){
    	    		tasks.push(new TaskDetails(next.u, next.v, next.w, next.e, 2));
    	    	}
    		}
    	}
    }

    class TaskDetails{
    	DNode u;
    	DNode v;
    	DNode w;
    	DEdge e;
    	int tasktype;
    	
    	public TaskDetails(DNode u, DNode v, DNode w, DEdge e, int tasktype) {
    		this.u = u;
    		this.v = v;
    		this.w = w;
    		this.tasktype = tasktype;
    		this.e = e;
    	}
    }
    
    //original recursive function
//    private void dfs(DNode u, DNode v) throws IOException, ParseException {
//    	v.bridgePre = cnt++;
//    	v.bridgeMin = v.bridgePre;
//    	
//    	for (DEdge e : v.getEdges()) {
//    		DNode w = e.getOtherNode(v);
//    		if (w == null) {
//    			ChyfLogger.INSTANCE.logError(ChyfLogger.Process.DIRECTION," Could not compute bridge edges in graph.", e.getNodeA().toGeometry());
//    			throw new RuntimeException("Could not compute bridge edges in graph (null pointer exception): " + e.getNodeA().toGeometry().toText());
//    		}
//    		if (w.bridgePre == -1) {
//    			dfs(v, w);
//    			v.bridgeMin = Math.min(v.bridgeMin, w.bridgeMin);
//    			if (w.bridgeMin == w.bridgePre) {
//    				e.setBridge();
//    			}
//    		}else if (w != u){
//    			v.bridgeMin = Math.min(v.bridgeMin, w.bridgePre);
//    		}
//    	}     
//    }
    
}

