/*
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
 */
package net.refractions.chyf.rest.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;

import org.apache.commons.io.IOUtils;
import org.locationtech.jts.geom.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import net.refractions.chyf.ChyfDataReader;
import net.refractions.chyf.ChyfDatastore;
import net.refractions.chyf.ChyfPostgresqlReader;
import net.refractions.chyf.FileVectorTileCache;
import net.refractions.chyf.VectorTileLayer;

@RestController
@RequestMapping("/tiles")
@CrossOrigin(allowCredentials="true",allowedHeaders="Autorization")
public class VectorTileController {
	
	static final Logger logger = LoggerFactory.getLogger(VectorTileController.class.getCanonicalName());
	
	@Autowired
	private ChyfDatastore datastore;
	
	@Autowired
	private FileVectorTileCache cache;

	//mvt media type
	public static MediaType MVT_MEDIATYPE = new MediaType("application", "vnd.mapbox-vector-tile");

	//tile format
	public static final String MVT_FORMAT = "mvt";
	
	//tile spec bounds and epsg code
	public static Envelope BOUNDS = new Envelope(-20026376.39, 20026376.39, -20048966.10, 20048966.10);
	public static int SRID = 3857;

	@RequestMapping(value = "/water/{z}/{x}/{y}.{format}", 
			method = {RequestMethod.GET},
			produces = "application/vnd.mapbox-vector-tile")
	public ResponseEntity<byte[]> getWaterTile(
			@PathVariable("z") int z,
			@PathVariable("x") int x,
			@PathVariable("y") int y,
			@PathVariable("format") String format) {
		
		return getTile(z, x, y, format, VectorTileLayer.WATER);
	}
	
	@RequestMapping(value = "/ecatchment/{z}/{x}/{y}.{format}", 
			method = {RequestMethod.GET},
			produces = "application/vnd.mapbox-vector-tile")
	public ResponseEntity<byte[]> getCatchmentTile(
			@PathVariable("z") int z,
			@PathVariable("x") int x,
			@PathVariable("y") int y,
			@PathVariable("format") String format) {
		return getTile(z, x, y, format, VectorTileLayer.CATCHMENT);
		
	}
	
	public ResponseEntity<byte[]> getTile(
			int z, int x, int y,
			String format, VectorTileLayer layer) {
		
		
		ChyfDataReader source = datastore.getReader();
		if (!(source instanceof ChyfPostgresqlReader)) {
			//error not supported
			throw new RuntimeException("Tile server is not supported for non-postgreSQL back end data stores");
		}
		
		//1. ensure format is "mvt"
		if (!format.equalsIgnoreCase(MVT_FORMAT)) {
			//throw invalid format exception
			throw new IllegalArgumentException(MessageFormat.format("The tile format {0} is not supported.  Only {1} is supported", format, MVT_FORMAT));
		}
		
		//2. ensure the x,y is valid for the given z
		int numtiles = (int) Math.pow(2, z);
		if (x < 0 || x > numtiles || y < 0 || y > numtiles) {
			//throw invalid tile exception
			throw new IllegalArgumentException(MessageFormat.format("The tile ({0}, {1}) is not valid for zoom level {2}", x,y,z));
		}
		
		//3. build query to get features		
		ChyfPostgresqlReader rr = (ChyfPostgresqlReader)source;
		
		Path cached = cache.getVectorTile(z, x, y, layer);
		if (cached != null) {
			//return cached
			try {
				HttpHeaders headers = new HttpHeaders();
			    headers.setContentType(MVT_MEDIATYPE);
			    headers.setContentLength(Files.size(cached));
			    byte[] data = null;
			    try(InputStream is = Files.newInputStream(cached)){
			    	 data = IOUtils.toByteArray(is);
			    }
			    return new ResponseEntity<byte[]>(data, headers, HttpStatus.OK);
			}catch (IOException ex) {
				logger.warn("Unable to return file from tilecache: " + ex.getMessage(), ex);
			}
		}
		
		byte[] tile = rr.getVectorTile(z, x, y, layer);
		cache.writeVectorTile(z, x, y, layer, tile);
		
		HttpHeaders headers = new HttpHeaders();
	    headers.setContentType(MVT_MEDIATYPE);
	    headers.setContentLength(tile.length);
		return new ResponseEntity<byte[]>(tile, headers, HttpStatus.OK);
		
	}
}
