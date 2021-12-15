/*
 * Copyright 2021 Canadian Wildlife Federation.
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

/**
 * Represents the various vector tile layers provided by the system
 * 
 * @author Emily
 *
 */
public enum VectorTileLayer {

	WATER, 
	CATCHMENT, 
	NHNWORKUNIT,
	SHORELINE;

	public boolean isShoreline() {
		return this == SHORELINE;
	}

	
	public boolean isWaterbody() {
		return this == WATER;
	}

	public boolean isCatchment() {
		return this == CATCHMENT;
	}

	public boolean isFlowpath() {
		return this == WATER;
	}

	public boolean isWorkUnit() {
		return this == NHNWORKUNIT;
	}
}