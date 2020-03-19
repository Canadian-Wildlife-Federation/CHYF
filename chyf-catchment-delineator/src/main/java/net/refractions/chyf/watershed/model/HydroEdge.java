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
package net.refractions.chyf.watershed.model;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateArrays.BidirectionalComparator;
import org.locationtech.jts.geom.LineString;

/**
 * HydroEdge represents a hydro edge water side.
 * 
 */
public class HydroEdge implements Comparable<HydroEdge> {

	// cached comparator
	private static BidirectionalComparator bidiCompare = new BidirectionalComparator();

	private Integer drainageID;
	private LineString line;
	private WaterSide waterSide;

	/**
	 * Constructs a new HydroEdge object.
	 * 
	 * @param line       The {@link LineString} of the hydro edge.
	 * @param drainageID The drainage ID of the hydro edge.
	 * @param waterSide  The side this hydro edge is on.
	 */
	public HydroEdge(LineString line, Integer drainageID, WaterSide waterSide) {
		setLine(line);
		setDrainageID(drainageID);
		setWaterSide(waterSide);
	}

	/**
	 * Topographically compares this HydroEdge to the given HydroEdge.
	 * 
	 * @param _otherObj The HydroEdge to compare this HydroEdge to.
	 * @return A negative number if this HydroEdge is "less" than the given one, a
	 *         positive number if the HydroEdge is "more" than the given one, and
	 *         zero if they are the same.
	 */
	public int compareTo(HydroEdge heTwo) {
		Coordinate[] coordsOne = this.line.getCoordinates();
		Coordinate[] coordsTwo = heTwo.line.getCoordinates();

		return bidiCompare.compare(coordsOne, coordsTwo);
	}

	/**
	 * Gets the drainage ID of the hydro edge.
	 * 
	 * @return The drainage ID of the hydro edge.
	 */
	public Integer getDrainageID() {

		return drainageID;
	}

	/**
	 * Gets the {@link LineString} of the hydro edge.
	 * 
	 * @return The LineString of the hydro edge.
	 */
	public LineString getLine() {

		return line;
	}

	/**
	 * Gets the side attribute of the hydro edge.
	 * 
	 * @return The side of the hydro edge.
	 */
	public WaterSide getWaterSide() {
		return this.waterSide;
	}

	/**
	 * Returns whether or not this hydro edge has water on the left side.
	 * 
	 * @return true if this hydro edge has water on the left side
	 */
	public boolean isWaterLeft() {
		return waterSide == WaterSide.LEFT;
	}

	/**
	 * Returns whether or not this hydro edge has water on the right side.
	 * 
	 * @return true if this hydro edge has water on the right side
	 */
	public boolean isWaterRight() {
		return waterSide == WaterSide.RIGHT;
	}

	/**
	 * Returns whether or not this hydro edge is a single line. Single-line edges
	 * have land on both sides.
	 * 
	 * @return True if this hydro edge is a single line, false otherwise.
	 */
	public boolean isSingleLine() {
		return waterSide == WaterSide.NEITHER;
	}

	/**
	 * Sets the drainage ID of the hydro edge.
	 * 
	 * @param drainageID The new drainage ID to store.
	 */
	public void setDrainageID(Integer drainageID) {

		this.drainageID = drainageID;
	}

	/**
	 * Sets the {@link LineString} of the hydro edge.
	 * 
	 * @param line The new LineString to store.
	 */
	public void setLine(LineString line) {
		this.line = line;
	}

	/**
	 * Sets the side attribute of the hydro edge.
	 * 
	 * @param side The side of the hydro edge.
	 */
	public void setWaterSide(WaterSide side) {
		this.waterSide = side;
	}

	/**
	 * Prints a textual representation of the hydro edge.
	 * 
	 * @return A textual representation of the hydro edge.
	 */
	public String toString() {

		StringBuffer sb = new StringBuffer();

		sb.append("[");
		sb.append(drainageID);
		sb.append("] '");
		sb.append(waterSide);
		sb.append("' ");
		sb.append(line.toText());

		return sb.toString();
	}
}
