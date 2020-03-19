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
package net.refractions.chyf.watershed.builder;

public enum BlockState {
    /** The variable indicating the block is disabled. */
    DISABLED(0),
    /** The variable indicating the block is ready to be processed. */
    READY(1),
    /** The variable indicating the block has been assigned */
    ASSIGNED(2),
    /** The variable indicating the block is in stage one (extracting) of processing. */
    EXTRACT(3),
    /** The variable indicating the block is in stage two (building) of processing. */
    BUILD(4),
    /** The variable indicating the block is in stage three (QA) of processing. */
    QA(5),
    /** The variable indicating the block completed successfully. */
    COMPLETE(6),
    /** The variable indicating the block failed with an exception. */
    ERROR(-1),
    /** The variable indicating the block didn't pass quality assurance. */
    FAILEDQA(-2);

	public final int id;
	
	BlockState(int id) {
		this.id = id;
	}
	
	public static BlockState fromId(Integer id) {
		if(id != null) {
			for(BlockState state : BlockState.values()) {
				if(id == state.id ) return state;
			}
		}
		throw new RuntimeException("Unknown state value '" + id + "'");
	}
}
