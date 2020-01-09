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
package net.refractions.chyf.skeletonizer.voronoi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineSegment;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.operation.linemerge.LineMerger;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

import net.refractions.chyf.ChyfProperties;
import net.refractions.chyf.ChyfProperties.Property;
import net.refractions.chyf.datasource.ChyfDataSource.EfType;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource.FlowDirection;
import net.refractions.chyf.skeletonizer.points.SkeletonPoint;
import net.refractions.chyf.skeletonizer.points.SkeletonPoint.Type;

/**
 * Graph used for processing skeletons
 * @author Emily
 *
 */
public class SkeletonGraph {
	
	private List<Node> nodes = new ArrayList<>();
	private List<Edge> edges = new ArrayList<>();

	/**
	 * Creates a new skeleton graph from a set of segments and inout points
	 * @param segments
	 * @param inout
	 * @return
	 */
	public static SkeletonGraph buildGraph(Collection<LineSegment> segments, List<SkeletonPoint> inout) {
		SkeletonGraph graph = new SkeletonGraph();
		HashMap<Coordinate, Node> nodes = new HashMap<>();
		
		for (LineSegment ls : segments) {
			Coordinate c0 = ls.p0;
			Coordinate c1 = ls.p1;
			
			Node a1 = graph.createNode(c0);
			Node a2 = graph.createNode(c1);
			if (nodes.containsKey(c0)) {
				a1 = nodes.get(c0);
			}else {
				nodes.put(c0, a1);
				graph.nodes.add(a1);
			}
			if (nodes.containsKey(c1)) {
				a2 = nodes.get(c1);
			}else {
				nodes.put(c1, a2);
				graph.nodes.add(a2);
			}
			
			EfType type = EfType.SKELETON;
			for (SkeletonPoint p : inout) {
				if (p.getType() == Type.BANK && (p.getCoordinate().equals2D(c0) || p.getCoordinate().equals2D(c1))) {
					type = EfType.BANK;
				}
			}
			ArrayList<Coordinate> coords = new ArrayList<>();
			coords.add(c0);
			coords.add(c1);
			Edge e = graph.createEdge(type, a1, a2, coords);
			
			a1.getEdges().add(e);
			a2.getEdges().add(e);
			graph.edges.add(e);
		}
		return graph;
	}
	
	
	/**
	 * Creates a new skeleton graph from a set of segments and inout points
	 * @param segments
	 * @param inout
	 * @return
	 */
	public static SkeletonGraph buildGraphLines(Collection<LineString> segments, List<SkeletonPoint> inout) {
		SkeletonGraph graph = new SkeletonGraph();
		HashMap<Coordinate, Node> nodes = new HashMap<>();
		
		for (LineString ls : segments) {
			Coordinate c0 = ls.getCoordinateN(0);
			Coordinate c1 = ls.getCoordinateN(ls.getCoordinates().length - 1);
			
			Node a1 = graph.createNode(c0);
			Node a2 = graph.createNode(c1);
			if (nodes.containsKey(c0)) {
				a1 = nodes.get(c0);
			}else {
				nodes.put(c0, a1);
				graph.nodes.add(a1);
			}
			if (nodes.containsKey(c1)) {
				a2 = nodes.get(c1);
			}else {
				nodes.put(c1, a2);
				graph.nodes.add(a2);
			}
			
			EfType type = EfType.SKELETON;
			for (SkeletonPoint p : inout) {
				if (p.getType() == Type.BANK && (p.getCoordinate().equals2D(c0) || p.getCoordinate().equals2D(c1))) {
					type = EfType.BANK;
				}
			}
			ArrayList<Coordinate> coords = new ArrayList<>();
			for (Coordinate c : ls.getCoordinates()) coords.add(c);
			Edge e = graph.createEdge(type, a1, a2, coords);
			a1.getEdges().add(e);
			a2.getEdges().add(e);
			graph.edges.add(e);
		}
		return graph;
	}
	
	public SkeletonGraph() {
	}
	
	public LineString shortestPath(GeometryFactory gf){
		//find the shortest path between the two degree one nodes;  Makes the assumption
		//that this graph only has two points
		
		HashMap<Node, List<Edge>> paths = new HashMap<>();
		HashMap<Node, Double> distances = new HashMap<>();
		
		Node start = null;
		Node end = null;
		for (Node n : nodes) {
			if (start == null && n.getDegree() == 1) start = n;
			if (start != null && n.getDegree() == 1) end = n;
		}
	
		List<Node> toVisit = new ArrayList<>();
		toVisit.add(start);
		
		distances.put(start, 0.0);
		paths.put(start, new ArrayList<>());
		
		while(!toVisit.isEmpty()) {
			Node n = toVisit.remove(0);
			for (Edge e : n.edges) {
				Node next = null;
				if (e.getNodeA() == n) next = e.getNodeB();
				if (e.getNodeB() == n) next = e.getNodeA();
				
				double newd = distances.get(n) + e.getDistance();
				
				if (!distances.containsKey(next) || newd < distances.get(next) ) {
					distances.put(next, newd);
					ArrayList<Edge> edges = new ArrayList<>(paths.get(n));
					edges.add(e);
					paths.put(next, edges);
					toVisit.add(next);
				}
			}
		}
		List<Edge> path = paths.get(end);
		LineMerger m = new LineMerger();
		for (Edge e : path) {
			LineString ls = gf.createLineString(e.coordinates.toArray(new Coordinate[e.coordinates.size()]));
			m.add(ls);
		}
		return (LineString)m.getMergedLineStrings().iterator().next();
		
	}
	
	
	private Node createNode(Coordinate cc) {
		return new Node(cc);
	}
	private Edge createEdge(EfType type, Node a1, Node a2, List<Coordinate> coords) {
		return new Edge(type, a1, a2, coords);
	}
	
	
	
	public Set<LineString> getSkeletons(ChyfProperties prop, GeometryFactory gf){
		Set<LineString> skeletons = new HashSet<>();
		for (Edge e : edges) {
			List<Coordinate> c = e.getCoordinates();
			LineString ls = gf.createLineString(c.toArray(new Coordinate[c.size()]));
			if (prop != null) ls = (LineString)DouglasPeuckerSimplifier.simplify(ls,  prop.getProperty(Property.SKEL_SIMPLIFY_FACTOR));
			ls.setUserData(e.getType());
			skeletons.add(ls);
		}
		return skeletons;
	}
	
	public void directionalize(List<SkeletonPoint> inout) {
		Set<Coordinate> keeppoints = inout.stream().map(e -> e.getCoordinate()).collect(Collectors.toSet());

		// process bank edges
		for (Edge e : edges) {
			if (e.type == EfType.BANK) {
				e.visited = true;
				if (keeppoints.contains(e.getEndCoordinate())) {
					// reverse
					Collections.reverse(e.coordinates);
				}
			}
		}

		Set<Coordinate> inpoints = inout.stream().filter(e -> e.getDirection() == FlowDirection.IN)
				.map(e -> e.getCoordinate()).collect(Collectors.toSet());

		// find the start points based on the input flows
		List<Node> toProcess = new ArrayList<>();
		for (Edge e : edges) {
			if (e.visited)
				continue;
			
			if (inpoints.contains(e.getStartCoordinate())) {
				e.visited = true;
			} else if (inpoints.contains(e.getEndCoordinate())) {
				Collections.reverse(e.coordinates);
				e.visited = true;
			}
			
			if (e.visited) {
				if (e.getNodeA().getDegree() != 1) toProcess.add(e.getNodeA());
				else if (e.getNodeB().getDegree() != 1)toProcess.add(e.getNodeB());
			}
		}

		while (!toProcess.isEmpty()) {
			Node n = toProcess.remove(0);
			// n.print();
			for (Edge e : n.edges) {
				if (e.visited) continue;

				if (n.c.equals2D(e.getStartCoordinate())) {
					// ok
					e.visited = true;
				} else if (n.c.equals2D(e.getEndCoordinate())) {
					Collections.reverse(e.coordinates);
					e.visited = true;
				}
				if (e.visited) {
					if (e.getNodeA() == n) toProcess.add(e.getNodeB());
					if (e.getNodeB() == n) toProcess.add(e.getNodeA());
				}
			}
		}
	}

	/**
	 * remmove in/out edges that don't correspond to the 
	 * flow in/out points 
	 * @param inout
	 */
	public void trim(List<SkeletonPoint> inout) {
		
		Set<Coordinate> keeppoints = inout.stream().map(e -> e.getCoordinate()).collect(Collectors.toSet());
		List<Node> toremove = new ArrayList<>();
		for (Node n : nodes) {
			if (n.getDegree() == 1 && !keeppoints.contains(n.c))
				toremove.add(n);
		}
		while (!toremove.isEmpty()) {
			Node n = toremove.remove(0);

			List<Edge> all = new ArrayList<>(n.edges);
			for (Edge e : all) {
				edges.remove(e);
				e.getNodeA().edges.remove(e);
				e.getNodeB().edges.remove(e);
				if (e.getNodeA() != n && e.getNodeA().getDegree() == 1) toremove.add(e.getNodeA());
				if (e.getNodeB() != n && e.getNodeB().getDegree() == 1) toremove.add(e.getNodeB());
			}
			nodes.remove(n);
		}
		//merge any remaining degree 2 nodes
		mergedegree2();
	}

	/**
	 * merge all degree 2 nodes
	 */
	public void mergedegree2() {

		Set<Node> d2 = nodes.stream().filter(e->e.getDegree() == 2).collect(Collectors.toSet());
		while (!d2.isEmpty()) {
			// find a degree 2 node
			Node node = d2.iterator().next();
			d2.remove(node);

			Edge e1 = node.getEdges().get(0);
			Edge e2 = node.getEdges().get(1);

			ArrayList<Coordinate> c = new ArrayList<>();
			Node a1 = null;
			Node a2 = null;
			if (e1.getNodeB() == node) {
				c.addAll(e1.coordinates);
				a1 = e1.getNodeA();
				
				if (e2.getNodeA() == node) {
					c.addAll(e2.getCoordinates());
					a2 = e2.getNodeB();
				}else {
					for (int i = e2.coordinates.size() - 1; i >= 0; i --) {
						c.add(e2.coordinates.get(i));
					}
					a2 = e2.getNodeA();
				}
				
			}else if (e1.getNodeA() == node) {
				if (e2.getNodeA() == node) {
					for (int i = e2.coordinates.size() - 1; i >= 0; i --) {
						c.add(e2.coordinates.get(i));
					}
					a1 = e2.getNodeB();	
				}else {
					c.addAll(e2.coordinates);
					a1 = e2.getNodeA();
				}
				c.addAll(e1.coordinates);
				a2 = e1.getNodeB();
			}
//			if (e1.getNodeA() == node) {
//			}
//			
//			
//			} else if (e1.getNodeA() == node) {
//				for (int i = e1.coordinates.size() - 1; i >= 0; i --) {
//					c.add(e1.coordinates.get(i));
//				}
//				a1 = e1.getNodeB();
//			}
//			
//			Node a2 = null;
//			if (e2.getNodeA() == node) {
//				c.addAll(e2.coordinates);
//				a2 = e2.getNodeB();
//			} else if (e2.getNodeB() == node) {
//				for (int i = e2.coordinates.size() - 1; i >= 0; i --) {
//					c.add(e2.coordinates.get(i));
//				}
//				a2 = e2.getNodeA();
//			}

			//remove the node and existing edges
			nodes.remove(node);
			edges.remove(e1);
			edges.remove(e2);
			e1.getNodeA().getEdges().remove(e1);
			e1.getNodeA().getEdges().remove(e2);
			e1.getNodeB().getEdges().remove(e1);
			e1.getNodeB().getEdges().remove(e2);
			e2.getNodeA().getEdges().remove(e1);
			e2.getNodeA().getEdges().remove(e2);
			e2.getNodeB().getEdges().remove(e1);
			e2.getNodeB().getEdges().remove(e2);
			
			//add the new edges
			EfType type = EfType.SKELETON;
			if (e1.getType() == EfType.BANK || e2.getType() == EfType.BANK) type = EfType.BANK;
			
			Edge e = new Edge(type, a1, a2, c);
			
			a1.getEdges().add(e);
			a2.getEdges().add(e);
			edges.add(e);
		}
	}

	public void collapseShortEdges(double minlength) {
		if (minlength < 0) return;
		
		for (Iterator<Edge> iterator = edges.iterator(); iterator.hasNext();) {
			Edge e =  iterator.next();
		
			//if one of the nodes is degree 1 never collapse
			if (e.getNodeA().getDegree() == 1 || e.getNodeB().getDegree() == 1 ) continue;
			
			double d = 0;
			for (int i = 1; i < e.coordinates.size(); i ++) {
				d += e.coordinates.get(i-1).distance(e.getCoordinates().get(i));
				if (d > minlength) break;
			}
			if (d > minlength) continue;
			
			//collapse edge
			Node n1 = e.getNodeA();
			Node n2 = e.getNodeB();
			
			n1.edges.remove(e);
			n2.edges.remove(e);
			for (Edge t : n1.getEdges()) {
				if (t.getNodeA() == n1) {
					t.setNodeA(n2);
				}
				if (t.getNodeB() == n1) {
					t.setNodeB(n2);
				}
			}
			
			n2.edges.addAll(n1.getEdges());
			
			nodes.remove(n1);
			iterator.remove();
		}
	}
	class Edge {
		private Node n1;
		private Node n2;
		
		private List<Coordinate> coordinates;
		private EfType type = EfType.SKELETON;
		private boolean visited = false;

		private Double distance = null;
		public Edge(EfType type, Node n1, Node n2, List<Coordinate> coords) {
			this.type = type;
			this.n1 = n1;
			this.n2 = n2;
			this.coordinates = coords;
		}
		
		public double getDistance() {
			if (this.distance == null) {
				double t = 0;
				for (int i = 1; i < coordinates.size(); i ++) {
					t += coordinates.get(i-1).distance(coordinates.get(i));
				}
				this.distance = t;
			}
			return this.distance;
		}
		public void setVisited(boolean visited) {
			this.visited = visited;
		}
		
		public boolean isVisited() {
			return this.visited;
		}
		
		public EfType getType() {
			return this.type;
		}
		
		public Node getNodeA() {
			return this.n1;
		}
		public Node getNodeB() {
			return this.n2;
		}
		
		public void setNodeA(Node newnode) {
			if (coordinates.get(0).equals2D( n1.getCoordinate())) {
				coordinates.remove(0);
				coordinates.add(0, newnode.getCoordinate());
			}else if (coordinates.get(coordinates.size() - 1).equals2D( n1.getCoordinate() ) ){
				coordinates.remove(coordinates.size() - 1);
				coordinates.add(newnode.getCoordinate());
			}else {
				throw new IllegalStateException("edge coordinates does not match node coordinate");
			}
			this.n1 = newnode;
			this.distance = null;
		}
		
		public void setNodeB(Node newnode) {
			if (coordinates.get(0).equals2D( n2.getCoordinate())) {
				coordinates.remove(0);
				coordinates.add(0, newnode.getCoordinate());
			}else if (coordinates.get(coordinates.size() - 1).equals2D( n2.getCoordinate() ) ){
				coordinates.remove(coordinates.size() - 1);
				coordinates.add(newnode.getCoordinate());
			}else {
				throw new IllegalStateException("edge coordinates does not match node coordinate");
			}
			this.n2 = newnode;
			this.distance = null;
		}
		
		public Coordinate getStartCoordinate() {
			return this.coordinates.get(0);
		}
		public Coordinate getEndCoordinate() {
			return this.coordinates.get(this.coordinates.size() - 1);
		}
		
		public List<Coordinate> getCoordinates(){
			return this.coordinates;
		}
		
		public void print() {
			System.out.print("LINESTRING( ");
			Coordinate c = coordinates.get(0);
			System.out.print(c.x + " " + c.y);
			for (int i = 1; i < coordinates.size(); i++) {
				c = coordinates.get(i);
				System.out.print("," + c.x + " " + c.y);
			}
			System.out.println(")");
		}
	}

	class Node {
		private Coordinate c;
		private List<Edge> edges = new ArrayList<>();

		public Node(Coordinate c) {
			this.c = c;
		}
		
		public Coordinate getCoordinate() {
			return this.c;
		}
		
		public int getDegree() {
			return edges.size();
		}

		public void addEdge(Edge e) {
			this.edges.add(e);
		}
		public void removeEdge(Edge e) {
			this.edges.remove(e);
		}
		public List<Edge> getEdges(){
			return this.edges;
		}
		public void print() {
			System.out.println("POINT( " + c.x + " " + c.y + ")");
		}
	}
}
