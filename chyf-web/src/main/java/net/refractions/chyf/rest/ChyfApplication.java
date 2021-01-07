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
package net.refractions.chyf.rest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

//import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;

import net.refractions.chyf.ChyfDataReader;
import net.refractions.chyf.ChyfDatastore;
import net.refractions.chyf.FileVectorTileCache;
import net.refractions.chyf.hygraph.HyGraph;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class ChyfApplication {
	
	static final Logger logger = LoggerFactory.getLogger(ChyfDataReader.class.getCanonicalName());

	private ChyfDatastore chyfDatastore;
	
	private FileVectorTileCache tileCache;
	
    @Autowired
	public ChyfApplication(ServletContext servletContext) {
    	String dataStore = servletContext.getInitParameter("chyfDataStore");
		String dir = null;
		
    	if (dataStore.equals("filestore")) {
    		String[] dirs = {
	                 servletContext.getInitParameter("chyfDataDir"), 
	                 "C:\\projects\\chyf-pilot\\data\\",
	                 "/data/chyf/"
			};

			for(String d : dirs) {
				if(new File(d).isDirectory()) {
					dir = d;
					break;
				}
			}

			chyfDatastore = new ChyfDatastore(dir);
    		
    	} else if (dataStore.equals("database")) {
    		
    		chyfDatastore = new ChyfDatastore();
    		
    	}
    
    	//configure cache
    	tileCache = new FileVectorTileCache();
    	String cache = servletContext.getInitParameter("chyfVectorTileCacheDirectory");
    	if (cache != null) {
    		Path p = Paths.get(cache);
    		if (!Files.exists(p)) {
    			try {
					Files.createDirectories(p);
				} catch (IOException e) {
					logger.debug(e.getMessage(), e);
				}
    		}
    		if (Files.exists(p)) tileCache.setCacheLocation(p);
    	}
    	
	}
    
	@Bean 
	public FileVectorTileCache getTileCache() {
		return tileCache;
	}
	
	@Bean
	public HyGraph getHyGraph() {
		return chyfDatastore.getHyGraph();
	}
	
	@Bean
	public ChyfDatastore getDatastore() {
		return chyfDatastore;
	}
	
//	@PreDestroy
//	public void preDestroy() {
//		//chyfDatastore.close();
//	}
	
}
