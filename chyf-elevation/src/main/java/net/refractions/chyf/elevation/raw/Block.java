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
package net.refractions.chyf.elevation.raw;

import org.geotools.geometry.jts.ReferencedEnvelope;

/**
 * Represents a unit of work for elevation processing: a spatial
 * block with an id and bounding envelope.
 *
 * @author Emily
 *
 */
public class Block {

	public ReferencedEnvelope bounds;
	public Integer blockId;
	
	
	public Block (Integer blockId, ReferencedEnvelope bounds) {
		this.blockId = blockId;
		this.bounds = bounds;
	}
	
	public Integer getBlockId() {
		return this.blockId;
	}
	
	public ReferencedEnvelope getBounds() {
		return this.bounds;
	}
	
}
