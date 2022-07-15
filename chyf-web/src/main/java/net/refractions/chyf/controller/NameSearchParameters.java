/*
 * Copyright 2021 Canadian Wildlife Federation
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
package net.refractions.chyf.controller;

import java.beans.ConstructorProperties;
import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.Parameter;
import net.refractions.chyf.ChyfWebApplication;
import net.refractions.chyf.exceptions.InvalidParameterException;
import net.refractions.chyf.model.HydroFeature;
import net.refractions.chyf.model.MatchType;

/**
 * Class to represent query parameters for querying
 * features by name.
 * 
 * @author Emily
 *
 */
public class NameSearchParameters {

	public enum GroupByType{
		NONE,
		TYPE,
		NAME
	}
	
	@Parameter(required = true, description = "The name to match")
	private String name;
	
	@Parameter(name="groupby", required = false, description = "How to group the results. None returns all features, typename group by type and feature name, name groups by feature name. Default is name.")
	private String groupBy;
	private GroupByType parsedGroupBy;
	
	@Parameter(name="feature-type", required = false, description = "The feature type to search. Must be one of flowpath or waterbody ")
	private List<String> featuretype;
	private List<HydroFeature.Type> parsedFeatureType;
	
	@Parameter(name="match-type", required = false, description = "The type of match to perform. Options include: 'contains' (default) and 'exact'. ")
	private String matchtype;
	private MatchType parsedType;
	
	@Parameter(name="max-results", required = false, description = "The maximum number of search results to return.  If not provided a system defined maximum is used.")
	private Integer maxresults;
	
	
	//this is the only way I could figure out
	//how to provide names for query parameters and
	//use a POJO to represent these parameters
	//I needed custom name for max-results
	//https://stackoverflow.com/questions/56468760/how-to-collect-all-fields-annotated-with-requestparam-into-one-object
	@ConstructorProperties({"name", "groupby", "feature-type", "match-type", "max-results"})
	public NameSearchParameters(String name, String groupby, List<String> featuretype,
			String matchtype, Integer maxresults) {
		this.name = name;
		this.groupBy = groupby;
		this.featuretype = featuretype;
		this.matchtype = matchtype;
	    this.maxresults = maxresults;
	}
	
	public String getName() { return this.name; }
	public String getEscapedName() { return this.name.replaceAll("\\%", "\\\\%").replaceAll("\\_", "\\\\_"); }
	public MatchType getMatchType() { return this.parsedType; }
	public Integer getMaxresults() { return maxresults;	}
	public List<HydroFeature.Type> getFeatureType(){ return this.parsedFeatureType; }
	public GroupByType getGroupBy() {return this.parsedGroupBy; }
	
	/**
	 * Parses parameters into data types and validates values.
	 * Should be called before calling get functions
	 * 
	 */
	public void parseAndValidate() {
		if (groupBy == null) {
			parsedGroupBy = GroupByType.NAME;
		}else {
			try {
				parsedGroupBy = GroupByType.valueOf(groupBy.toUpperCase());
			}catch (Exception ex) {
				throw new InvalidParameterException("The groupby parameter is invalid. Must be one of '" + GroupByType.NAME.name() + "', '" + GroupByType.TYPE.name() + "', or '" + GroupByType.NONE.name()+  "'");
				
			}
		}
		if (matchtype == null) {
			parsedType = MatchType.CONTAINS;
		}else {
			try {
				parsedType = MatchType.valueOf(matchtype.toUpperCase());
			}catch (Exception ex) {
				throw new InvalidParameterException("The match-type parameter is invalid. Must be one of '" + MatchType.CONTAINS.name() + "' or '" + MatchType.EXACT.name() + "'");
			}
		}
		
		if (featuretype != null) {
			parsedFeatureType = new ArrayList<>();
			try {
				for (String s : featuretype) {
					parsedFeatureType.add(HydroFeature.Type.valueOf(s.toUpperCase()));
				}
			}catch (Exception ex) {
				ex.printStackTrace();
				throw new InvalidParameterException("The feature-type parameter is invalid. Must be one of 'flowpath' or 'waterbody'");
			}
		}else {
			parsedFeatureType = null;
		}
		
		if (maxresults == null) {
			maxresults = ChyfWebApplication.MAX_RESULTS;
		}
		if (maxresults < 0) {
			throw new InvalidParameterException("The 'max-results' parameter must be larger than 0");
		}
		
		if (parsedGroupBy == GroupByType.NAME) {
			if (featuretype != null) {
				throw new InvalidParameterException("The feature-type is not supported when groupby type is '" + GroupByType.NAME.name() + "'");
			}
		}
	}
	
	/**
	 * update the maximum results values
	 * @param value
	 */
	public void setMaxResults(int value) {
		if (value < 0) return;
		this.maxresults = value;
	}
	
}
