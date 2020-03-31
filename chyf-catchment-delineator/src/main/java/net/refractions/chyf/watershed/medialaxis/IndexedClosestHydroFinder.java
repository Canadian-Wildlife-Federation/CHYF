package net.refractions.chyf.watershed.medialaxis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.triangulate.quadedge.QuadEdge;
import org.locationtech.jts.util.Assert;

import net.refractions.chyf.util.KDTree;
import net.refractions.chyf.watershed.model.HydroEdge;
import net.refractions.chyf.watershed.model.WatershedVertex;

public class IndexedClosestHydroFinder {
	private KDTree<Coordinate> index;

	public IndexedClosestHydroFinder(List<HydroEdge> hydroEdges) {
		Set<Coordinate> hydroCoords = new HashSet<Coordinate>(hydroEdges.size());
		for(HydroEdge he : hydroEdges) {
			LineString ls = he.getLine();
			for(int i = 0; i < ls.getNumPoints(); i++) {
				hydroCoords.add(ls.getCoordinateN(i));
			}
		}
		index = new KDTree<Coordinate>(new ArrayList<Coordinate>(hydroCoords), Function.identity());
	}

//	public void assignClosest(Collection<QuadEdge> quadEdges) {
//		if (index == null)
//			return;
//		for (QuadEdge qe : quadEdges) {
//			assignClosest((WatershedVertex) qe.orig());
//			assignClosest((WatershedVertex) qe.dest());
//		}
//	}
//
//	public void assignClosest(WatershedVertex v) {
//		// check if nothing to do
//		if (index == null)
//			return;
//		if (v.getClosestCoordinate() != null)
//			return;
//
//		Coordinate pt = v.getCoordinate();
//		Coordinate closestVert = findClosest(pt);
//
//		// assert: closestVert != null
//		Assert.isTrue(closestVert != null, "Can't find a closest constraint vertex");
//		v.setClosestCoordinate(closestVert);
//	}

	private Coordinate findClosest(Coordinate pt) {
		List<Coordinate> closest = index.query(pt, 1, null);
		return closest.get(0);
//		Collection<KdNode> kdNodes = findNearKdNodes(pt);
//
//		double minDist = Double.MAX_VALUE;
//		Vertex closestVert = null;
//		for (KdNode node : kdNodes) {
//			Coordinate candidateClosest = node.getCoordinate();
//			double dist = pt.distance(candidateClosest);
//			if (dist < minDist) {
//				minDist = dist;
//				closestVert = (Vertex) node.getData();
//			}
//		}
//		return closestVert;
	}

}
