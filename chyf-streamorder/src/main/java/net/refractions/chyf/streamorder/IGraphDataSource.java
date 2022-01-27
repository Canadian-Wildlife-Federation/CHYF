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
package net.refractions.chyf.streamorder;

import java.sql.SQLException;
import java.util.List;

public interface IGraphDataSource extends AutoCloseable{

	/**
	 * Nexus properties
	 * 
	 * @author Emily
	 *
	 */
	public enum NexusProperty{
		UPSTREAMLENGTH("upstreamlength"),
		NEXUS_TYPE("nexus_type"),
		SORDER("sorder"),
		SHORDER("shorder"),
		HTORDER("htorder"),
		HKORDER("hkorder"),
		MAINSTEMID("mainstemid"),
		MAINSTEMID_SEQ("mainstemseq"),
		ID("id"),
		COMPONENTID("componentId");
		
		public String key;
		NexusProperty(String key){
			this.key = key;
		}
	}
	
	/**
	 * Flowpath properties
	 * @author Emily
	 *
	 */
	public enum FlowpathProperty{
		NAMEID("nameid"),
		EF_TYPE("ef_type"),
		EF_SUBTYPE("ef_subtype"),
		RANK("rank"),
		ID("id"),
		LENGTH("length");
		
		public String key;
		FlowpathProperty(String key){
			this.key = key;
		}
	}

	/**
	 * Connect to the data source
	 * @throws SQLException
	 */
	public void connect() throws SQLException ;
	
	/**
	 * Close the datasource 
	 * @throws SQLException
	 */
	@Override
	public void close() throws SQLException ;
	
	/**
	 * Compute aoi groups. Should return at least one group
	 * @return
	 * @throws SQLException
	 */
	public List<AoiGroup> computeAoiGraphs() throws SQLException ;
	
	/**
	 * Loads the graph for the given aoi group.
	 * 
	 * @param datasource
	 * @param group
	 * @throws SQLException
	 */
	public void loadGraph(Neo4JDatastore datasource, AoiGroup group) throws SQLException ;
	
	/**
	 * Save the results of the processing
	 * 
	 * @param graph
	 * @throws SQLException
	 */
	public void saveData(Neo4JDatastore graph) throws SQLException;

}
