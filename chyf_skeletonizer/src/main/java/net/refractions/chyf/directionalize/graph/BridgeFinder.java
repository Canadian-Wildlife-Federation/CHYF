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