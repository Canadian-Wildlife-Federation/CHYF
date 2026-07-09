/*
 * Copyright 2026 Canadian Wildlife Federation
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
package net.refractions.chyf.elevation.smooth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a node in the stream network with an 
 * elevation value and in/out nodes
 */
public class Node {

	private UUID id;

	private List<UUID> inNodes;
	private List<UUID> outNodes;

	private Double rawZ;
	
	private Double maxDownZ = null;
	private Double minUpZ = null;

	public Node(UUID id, Double rawZ) {
		this.id = id;
		this.rawZ = rawZ;
		this.inNodes = new ArrayList<>();
		this.outNodes = new ArrayList<>();
	}

	public void addInNode(UUID nodeid) {
		this.inNodes.add(nodeid);

	}

	public void addOutNode(UUID nodeid) {
		this.outNodes.add( nodeid);

	}
	
	public List<UUID> getOutNodes(){
		return this.outNodes;
	}
	
	public List<UUID> getInNodes(){
		return this.inNodes;
	}
	
	public UUID getId() {
		return this.id;
	}
	
	public double getRawZ() {
		return this.rawZ;
	}
	
	public void setMaxDownZ(double z) {
		this.maxDownZ = z;
	}
	
	public Double getMaxDownZ() {
		return this.maxDownZ;
	}
	
	public void setMinUpZ(double z) {
		this.minUpZ = z;
	}
	
	public Double getMinUpZ() {
		return this.minUpZ;
	}
	
	public Double getSmoothedZ() {
		return (this.minUpZ + this.maxDownZ) / 2.0;
	}
}
