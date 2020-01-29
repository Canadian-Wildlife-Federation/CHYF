package net.refractions.chyf.directionalize.graph;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

public class CycleFinder {

	
	private HashMap<DNode, DNode> parents = new HashMap<>();
	private HashMap<DNode, Integer> state = new HashMap<>();

	private HashMap<DNode, Integer> cycleNum = new HashMap<>();
	
	private int cyclenum;
	
	public void findCycles(DNode sink) {
		
		this.cyclenum = 1;
	
		cycleFinder(sink,  null);
		
		HashMap<Integer, Set<DNode>> groups = new HashMap<Integer, Set<DNode>>();
		
		for (Entry<DNode,Integer> e : cycleNum.entrySet()) {
			Set<DNode> items = groups.get(e.getValue());
			if (items == null) {
				items = new HashSet<>();
				groups.put(e.getValue(), items);
			}
			items.add(e.getKey());
		}
		
		for (Entry<Integer, Set<DNode>> g : groups.entrySet()) {
			System.out.println(g.getKey());
			for (DNode d : g.getValue()) System.out.println(d.toString());
		}
	}
	
	private void cycleFinder(DNode current, DNode parent){ 
 		 // already (completely) visited vertex. 
		 if (state.containsKey(current) && state.get(current) == 2) { 
		     return; 
		 } 
		
		 // seen vertex, but was not completely visited -> cycle detected. 
		 // backtrack based on parents to find the complete cycle. 
		 if (state.containsKey(current) && state.get(current) == 1) { 
		
		     cyclenum++; 
		     DNode cur = parent; 
		     
		     // backtrack the vertex which are 
		     // in the current cycle thats found 
		     while (cur != current) { 
		         cur = parents.get(cur); 
		         cycleNum.put(cur,  cyclenum);
		     } 
		     return; 
		 } 
		 parents.put(current, parent);
	 
	
		 // partially visited. 
		 state.put(current, 1); 
		
		 // simple dfs on graph 
		 for (DEdge e : current.getEdges()) { 
			 DNode v = e.getOtherNode(current);
		     // if it has not been visited previously 
		     if (v == parents.get(current)) { 
		         continue; 
		     } 
		     cycleFinder(v, current);
		 } 
		state.put(current,2); 
	} 
}
