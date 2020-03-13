/*
 * Copyright 2019 Government of Canada
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
package net.refractions.chyf.flowpathconstructor;

import net.refractions.chyf.Args;

/**
 * Class for parsing and validation input arguments for various tools
 * 
 * @author Emily
 *
 */
public class FlowpathArgs extends Args{

	public FlowpathArgs(String mainClass) {
		super(mainClass);
	}

	public ChyfProperties getPropertiesFile() throws Exception {
		if (this.prop == null) return null;
		return ChyfProperties.getProperties(this.prop);
	}

}
