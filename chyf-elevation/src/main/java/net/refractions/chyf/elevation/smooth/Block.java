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

/**
 * Represents a unit of work for smoothing processing.  A block
 * consists of one of more connected units of stream networks (graph_id)
 * 
 * @author Emily
 *
 */
public class Block {

	
	public Integer blockId;
	public List<Integer> graphIds;
	
	public Block (Integer blockId) {
		this.blockId = blockId;
		this.graphIds =  new ArrayList<>();
	}
	
	public Integer getBlockId() {
		return this.blockId;
	}
	
	public void addGraphId(Integer id) {
		this.graphIds.add(id);
	}
	public List<Integer> getGroupIds() {
		return this.graphIds;
	}
	
}
