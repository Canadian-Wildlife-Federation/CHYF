/*******************************************************************************
 * Copyright 2020 Government of Canada
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
 *******************************************************************************/
package net.refractions.chyf.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.Function;
import java.util.function.Predicate;

import org.locationtech.jts.geom.Coordinate;

public class KDTree<T> {

	private KDNode<T> root;
	private Function<T,Coordinate> toCoord;
	
	public KDTree(List<? extends T> items, Function<T,Coordinate> toCoord) {
		this.toCoord = toCoord;
		root = buildNode(items, Axis.X);
	}

	private KDNode<T> buildNode(List<? extends T> items, Axis axis) {
		KDNode<T> node = new KDNode<T>();
		if(items.size() == 0) {
			return node;
		}
		// sorting the entire list is unnecessary
		// Collections.sort(items, new PointLocationComparator(axis));
		// int middle = items.size() >> 1;
		// faster approach is to partition the list using QuickSelect to find the median
		int middle = select(items, 0, items.size() - 1, items.size() >> 1,
				new CoordinateAxisComparator<T>(axis, toCoord));
		node.item = items.get(middle);
		node.splitAxis = axis;
		if(middle > 0) {
			node.left = buildNode(items.subList(0, middle), axis.nextAxis());
		}
		if(items.size() > middle + 1) {
			node.right = buildNode(items.subList(middle + 1, items.size()), axis.nextAxis());
		}
		return node;
	}
	
	private int select(
			List<? extends T> list, int left, int right, int n, Comparator<T> comparator) {
		// If the list contains only one element
		if(left == right) {
			return left;
		}
		// pick a random pivot
		int pivotIndex = left + (int)(Math.floor(Math.random() * (right - left + 1)));
		pivotIndex = partition(list, left, right, pivotIndex, comparator);
		// The pivot is in its final sorted position
		if(n == pivotIndex) {
			return n;
		} else if(n < pivotIndex) {
			return select(list, left, pivotIndex - 1, n, comparator);
		} else {
			return select(list, pivotIndex + 1, right, n, comparator);
		}
	}
	
	private <K extends T> int partition(
			List<K> list, int left, int right, int pivotIndex, Comparator<T> comparator) {
		K pivotValue = list.get(pivotIndex);
		K temp = null;
		list.set(pivotIndex, list.get(right));
		list.set(right, pivotValue);
		int storeIndex = left;
		for(int i = left; i < right; i++) {
			if(comparator.compare(list.get(i), pivotValue) < 0) {
				temp = list.get(storeIndex);
				list.set(storeIndex, list.get(i));
				list.set(i, temp);
				storeIndex++;
			}
		}
		// Move pivot to its final place
		temp = list.get(storeIndex);
		list.set(storeIndex, list.get(right));
		list.set(right, temp);
		return storeIndex;
	}

	public List<T> query(Coordinate query, int nResults) {
		return queueToOrderedList(search(root, query, nResults, null, x -> true));
	}

	public List<T> query(Coordinate query, int nResults, Double maxDistance) {
		return queueToOrderedList(search(root, query, nResults, maxDistance, x -> true));
	}

	public List<T> query(Coordinate query, int nResults, Double maxDistance, Predicate<T> filter) {
		return queueToOrderedList(search(root, query, nResults, maxDistance, filter));
	}

	private Queue<PrioNode<T>> search(KDNode<T> node, Coordinate query, 
			Integer nResults, Double maxDistance, Predicate<T> filter) {
		double maxDistSq;
		if(maxDistance == null) {
			maxDistSq = Double.POSITIVE_INFINITY;
		} else {
			maxDistSq = maxDistance * maxDistance;
		}
		
		final Queue<PrioNode<T>> results = new PriorityQueue<PrioNode<T>>(nResults,
				new Comparator<PrioNode<T>>() {
					// Java's Priority queue is a min-heap, in that the
					// first item in the sort order is at the top of the heap
					// so this comparator puts the larger distances first,
					// essentially implementing a max-heap
					@Override
					public int compare(PrioNode<T> o1, PrioNode<T> o2) {
						return o1.priority == o2.priority ? 0
								: o1.priority > o2.priority ? -1
										: 1;
					}
				});
		if(node.item == null) {
			return results;
		}
		final Deque<KDNode<T>> stack = new ArrayDeque<KDNode<T>>();
		stack.addLast(node);
		while(!stack.isEmpty()) {
			node = stack.removeLast();
			
			if(node.left != null || node.right != null) {
				// Guess nearest tree to query point
				KDNode<T> nearTree = node.left, farTree = node.right;
				if(CoordinateAxisComparator.compareAxis(query, toCoord.apply(node.item),
						node.splitAxis) > 0) {
					nearTree = node.right;
					farTree = node.left;
				}
				
				// Only search far tree if our search sphere might
				// overlap with splitting plane
				double dist = sq(CoordinateAxisComparator.diffAxis(query, toCoord.apply(node.item),
						node.splitAxis));
				if(farTree != null && dist <= maxDistSq && (results.size() < nResults
						|| dist <= results.peek().priority)) {
					stack.addLast(farTree);
				}
				
				// Always search the nearest branch
				if(nearTree != null) {
					stack.addLast(nearTree);
				}
			}
			final double dSq = distanceSq(query, toCoord.apply(node.item));
			// if this node is closer than the max distance
			// AND either we don't have the max results OR this node is closer than the current
			// worst result
			// AND the item passes whatever filter parameters we have
			if(dSq < maxDistSq && (results.size() < nResults || dSq < results.peek().priority)
					&& filter.test(node.item)) {
				while(results.size() >= nResults) {
					results.poll();
				}
				results.offer(new PrioNode<T>(dSq, node.item));
			}
		}
		return results;
	}
	
	private static double distanceSq(Coordinate p1, Coordinate p2) {
		return sq(p1.getX() - p2.getX()) + sq(p1.getY() - p2.getY());
	}
	
	private static double sq(double n) {
		return n * n;
	}
	
	private static <T> List<T> queueToOrderedList(Queue<PrioNode<T>> queue) {
		List<T> list = new ArrayList<T>(queue.size());
		while(!queue.isEmpty()) {
			list.add(queue.remove().item);
		}
		Collections.reverse(list);
		return list;
	}
}
