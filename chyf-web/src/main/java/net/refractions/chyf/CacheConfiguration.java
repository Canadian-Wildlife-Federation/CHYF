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

import java.util.Collection;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import net.refractions.chyf.model.VectorTileCache;

/**
 * Configuration for tile cache manager
 * 
 * @author Emily
 *
 */
@Configuration
public class CacheConfiguration {

	@Autowired
	VectorTileCache cache;

	@Bean
	public CacheManager cacheManager() {
		return new CacheManager() {
			@Override
			public Collection<String> getCacheNames() {
				return Collections.singleton(cache.getName());
			}

			@Override
			public Cache getCache(String name) {
				if (name.equals(cache.getName()))
					return cache;
				return null;
			}
		};
	}

}
