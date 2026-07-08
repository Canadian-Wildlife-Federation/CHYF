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
package net.refractions.chyf.elevation;

import java.util.UUID;

import org.locationtech.jts.geom.LineString;

/**
 * Represents a flowpath edge with its id and geometry, used to
 * carry elevation values back to the data source.
 *
 * @author Emily
 *
 */
public class EFlowpath {

	private UUID id;
	private LineString ls;
	
	public EFlowpath(UUID id, LineString ls) {
		this.id = id;
		this.ls = ls;
	}
	
	public UUID getId() {
		return this.id;
	}
	
	public LineString getLineString() {
		return this.ls;
	}
	public void setLineString(LineString ls) {
		this.ls = ls;
	}
}
