/*
 * Copyright 2021 Canadian Wildlife Federation.
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
package net.refractions.chyf;

import java.nio.charset.Charset;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;

@SpringBootApplication
@ComponentScan(basePackages = { "net.refractions.chyf" })
public class ChyfWebApplication extends SpringBootServletInitializer {

	/**
	 * Maximum number of features to return from search results
	 */
	public static final int MAX_RESULTS = 5000;

	public static final MediaType GEOJSON_MEDIA_TYPE = new MediaType("application", "geo+json",Charset.forName("UTF-8"));

	public static final String GEOPKG_MEDIA_TYPE_STR = "application/geopackage+sqlite3";
	public static final MediaType GEOPKG_MEDIA_TYPE = new MediaType("application", "geopackage+sqlite3", Charset.forName("UTF-8"));
	
	public static final String DOWNLOAD_DATETIME_KEY = "download_datetime";
	public static final String DATA_LICENSE_KEY = "data_licence";
	
	public static void main(String[] args) {
		SpringApplication.run(ChyfWebApplication.class, args);
	}

}
