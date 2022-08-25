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
package net.refractions.chyf.datasource;

/**
 * The core layers in the Chyf dataset
 * 
 * @author Emily
 *
 */
public enum Layer implements ILayer{
	
	EFLOWPATHS("EFlowpaths"),
	ECATCHMENTS("ECatchments"),
	AOI("AOI"),
	SHORELINES("Shorelines"),
	TERMINALNODES("TerminalNodes"),
	ERRORS("ProcessingErrors"),
	FEATURENAMES("FeatureNames");
	
	private String layerName;
	
	Layer(String layerName){
		this.layerName = layerName;
	}
	
	public String getLayerName() {
		return this.layerName;
	}		
}
