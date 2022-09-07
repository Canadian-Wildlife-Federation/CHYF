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

	public enum ResultType{
		BBOX("bbox"),
		ALL_FEATURES("all"),
		GROUP_BY_TYPE("groupbytype"),
		GROUP_BY_NAME("groupbyname");
		
		private String paramValue;
		
		ResultType(String paramValue){
			this.paramValue = paramValue;
		}
	}
	
	@Parameter(required = true, description = "The name to match")
	private String name;
	
	@Parameter(name="result-type", required = false, description = "The type of result to return. (bbox, all, groupbytype, groupbyname)")
	private String resultType;
	private ResultType parsedResultType;
	
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
	@ConstructorProperties({"name", "result-type", "feature-type", "match-type", "max-results"})
	public NameSearchParameters(String name, String resulttype, List<String> featuretype,
			String matchtype, Integer maxresults) {
		this.name = name;
		this.resultType = resulttype;
		this.featuretype = featuretype;
		this.matchtype = matchtype;
	    this.maxresults = maxresults;
	}
	
	public String getName() { return this.name; }
	public String getEscapedName() { return this.name.replaceAll("\\%", "\\\\%").replaceAll("\\_", "\\\\_"); }
	public MatchType getMatchType() { return this.parsedType; }
	public Integer getMaxresults() { return maxresults;	}
	public List<HydroFeature.Type> getFeatureType(){ return this.parsedFeatureType; }
	public ResultType getResultType() {return this.parsedResultType; }
	
	/**
	 * Parses parameters into data types and validates values.
	 * Should be called before calling get functions
	 * 
	 */
	public void parseAndValidate() {
		parsedResultType = ResultType.BBOX;
		if (resultType != null) {
			boolean found = false;
			for (ResultType t : ResultType.values()) {
				if (t.paramValue.equalsIgnoreCase(resultType)) {
					found = true;
					parsedResultType = t;
					break;
				}
			}
			if (!found) {
				throw new InvalidParameterException("The result-type parameter is invalid. Must be one of '" + ResultType.GROUP_BY_NAME.paramValue + "', '" + ResultType.GROUP_BY_TYPE.paramValue + "', or '" + ResultType.ALL_FEATURES.paramValue + "', or '" + ResultType.BBOX.paramValue +  "'");
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
				throw new InvalidParameterException("The feature-type parameter is invalid. Must be one of '" + HydroFeature.Type.FLOWPATH.name() + "' or '" + HydroFeature.Type.WATERBODY.name() + "'");
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
		
		if (parsedResultType == ResultType.GROUP_BY_NAME) {
			if (featuretype != null) {
				throw new InvalidParameterException("The feature-type is not supported when groupby type is '" + ResultType.GROUP_BY_NAME.name() + "'");
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
