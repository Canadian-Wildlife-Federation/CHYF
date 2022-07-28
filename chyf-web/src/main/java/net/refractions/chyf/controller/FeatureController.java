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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.springdoc.api.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import net.refractions.chyf.controller.NameSearchParameters.ResultType;
import net.refractions.chyf.exceptions.ApiError;
import net.refractions.chyf.exceptions.NotFoundException;
import net.refractions.chyf.model.HydroFeature;
import net.refractions.chyf.model.HydroFeatureList;
import net.refractions.chyf.model.dao.ECatchmentDao;
import net.refractions.chyf.model.dao.EFlowpathDao;
import net.refractions.chyf.model.dao.NameFeatureSearchDao;
import net.refractions.chyf.model.dao.ShorelineDao;

/**
 * REST api for getting Hydro features from the database.
 * 
 * 
 * @author Emily
 *
 */
@RestController
@RequestMapping("/" + FeatureController.PATH)
public class FeatureController {

	public static final String PATH = "features";
	
	@Autowired
	private EFlowpathDao flowpathDao;
	
	@Autowired
	private ECatchmentDao catchmentDao;
		
	@Autowired
	private NameFeatureSearchDao nameSearchDao;
	
	@Autowired
	private ShorelineDao shorelineDao;
	
	/**
	 * Gets an individual feature by identifier
	 * 
	 * @param id
	 * @return
	 */
	@Operation(summary = "Find an individual feature.")
	@ApiResponses(value = { 
			@ApiResponse(responseCode = "200",
						description = "The feature as a GeoJson feature. Feature attributes will vary by feature type.",
						content = {
						@Content(mediaType = "application/geo+json")}),
			 @ApiResponse(responseCode = "404",
					 	description = "feature not found", 
			 			content = {
						@Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))})})
	@GetMapping(value = "/{id:[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}}",
			produces = {MediaType.APPLICATION_JSON_VALUE, "application/geo+json"})
	public ResponseEntity<HydroFeature> getFeature(
			@Parameter(description = "unique feature identifier") 
			@PathVariable("id") UUID id,
			HttpServletRequest request) {
		
		HydroFeature f = flowpathDao.getEFlowpath(id);
		
		if (f == null) {
			f = catchmentDao.getECatchment(id); 
		}
		
		if (f == null) {
			f = shorelineDao.getShoreline(id);
		}
		
		//TODO: add Nexus
		
		if (f == null) throw new NotFoundException(MessageFormat.format("No feature with id ''{0}'' found.", id));
		return ResponseEntity.ok(f);
	}
	
	
	@Operation(summary = "Searches for features name and type. Supported types include flowpath, waterbody and catchment.")
	@ApiResponses(value = { 
			@ApiResponse(responseCode = "200",
						description = "Return all feature features that match search parameters as a GeoJson feature collection.",
						content = {
						@Content(mediaType = "application/geo+json")}),
			@ApiResponse(responseCode = "400",
					 	description = "When one of the parameters is not in a valid format", 
			 			content = {
						@Content(mediaType = "application/json", schema = @Schema(implementation = ApiError.class))})			 
			 })
	@GetMapping(produces = {MediaType.APPLICATION_JSON_VALUE, "application/geo+json"})
	
	public ResponseEntity<HydroFeatureList> getFeatureByName(@ParameterObject NameSearchParameters params, HttpServletRequest request) {
		
		params.parseAndValidate();
		
		HydroFeatureList results = new HydroFeatureList(new ArrayList<>());
		
		if (params.getResultType() == ResultType.GROUP_BY_NAME || params.getResultType() == ResultType.BBOX) {
			results.getItems().addAll(nameSearchDao.getFeaturesByNameMerged(params));
		
		}else if (params.getResultType() == ResultType.ALL_FEATURES) {
			
			if (params.getFeatureType() == null || params.getFeatureType().contains(HydroFeature.Type.FLOWPATH)) {
				results.getItems().addAll(flowpathDao.getFeaturesByName(params));
			}
			if (params.getFeatureType() == null || params.getFeatureType().contains(HydroFeature.Type.CATCHMENT)
					|| params.getFeatureType().contains(HydroFeature.Type.WATERBODY)) {
				if (results.getItems().size() < params.getMaxresults()) {
					//enfore max results limit between types
					params.setMaxResults(params.getMaxresults() - results.getItems().size());
					results.getItems().addAll(catchmentDao.getFeaturesByName(params));
				}
			}
		}else if (params.getResultType() == ResultType.GROUP_BY_TYPE) {
			if (params.getFeatureType() == null || params.getFeatureType().contains(HydroFeature.Type.FLOWPATH)) {
				results.getItems().addAll(flowpathDao.getFeaturesByNameMerged(params));
			}
			if (params.getFeatureType() == null || params.getFeatureType().contains(HydroFeature.Type.CATCHMENT)
					|| params.getFeatureType().contains(HydroFeature.Type.WATERBODY)) {
				if (results.getItems().size() < params.getMaxresults()) {
					//enfore max results limit between types
					params.setMaxResults(params.getMaxresults() - results.getItems().size());
					results.getItems().addAll(catchmentDao.getFeaturesByNameMerged(params));
				}
			}
		}else if (params.getResultType() == ResultType.BBOX) {
			
		}
		
		return ResponseEntity.ok(results);
	}

		
}
