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
import java.net.URISyntaxException;

//import javax.annotation.PreDestroy;
import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.cache.jcache.JCacheManagerFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import net.refractions.chyf.ChyfDataReader;
import net.refractions.chyf.ChyfDatastore;
import net.refractions.chyf.hygraph.HyGraph;

@Component
@EnableCaching
public class ChyfApplication {
	
	static final Logger logger = LoggerFactory.getLogger(ChyfDataReader.class.getCanonicalName());

	private ChyfDatastore chyfDatastore;
	
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
    
    }
    
	@Bean
	public HyGraph getHyGraph() {
		return chyfDatastore.getHyGraph();
	}
	
	@Bean
	public ChyfDatastore getDatastore() {
		return chyfDatastore;
	}
	
    @Bean
    public JCacheCacheManager jCacheCacheManager(JCacheManagerFactoryBean jCacheManagerFactoryBean){
        JCacheCacheManager jCacheCacheManager = new JCacheCacheManager();
        jCacheCacheManager.setCacheManager(jCacheManagerFactoryBean.getObject());
        return jCacheCacheManager;
    }

    @Bean
    public JCacheManagerFactoryBean jCacheManagerFactoryBean() throws URISyntaxException {
        JCacheManagerFactoryBean jCacheManagerFactoryBean = new JCacheManagerFactoryBean();
        jCacheManagerFactoryBean.setCacheManagerUri(getClass().getResource("/ehcache.xml").toURI());
        return jCacheManagerFactoryBean;
    }
    
//	@PreDestroy
//	public void preDestroy() {
//		//chyfDatastore.close();
//	}
	
}
