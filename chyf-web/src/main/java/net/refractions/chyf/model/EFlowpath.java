/*
 * Copyright 2022 Canadian Wildlife Federation
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
package net.refractions.chyf.model;

import java.util.UUID;

import org.locationtech.jts.geom.LineString;

/**
 * Class to represent a flowpath object.
 * 
 * @author Emily
 *
 */
public class EFlowpath extends HydroFeature {

	private int rank;
	private double length;
	private UUID ecatchmentid;
	private LineString geometry;

	public int getRank() {
		return rank;
	}

	public void setRank(int rank) {
		this.rank = rank;
	}

	public double getLength() {
		return length;
	}

	public void setLength(double length) {
		this.length = length;
	}

	public UUID getEcatchmentId() {
		return ecatchmentid;
	}

	public void setEcatchmentId(UUID ecatchmentid) {
		this.ecatchmentid = ecatchmentid;
	}
	
	public LineString getGeometry() {
		return this.geometry;
	}
	public void setGeometry(LineString geometry) {
		this.geometry = geometry;
	}
	
}
