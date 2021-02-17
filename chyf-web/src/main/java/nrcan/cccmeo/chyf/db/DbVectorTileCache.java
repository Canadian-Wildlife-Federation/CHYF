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
package nrcan.cccmeo.chyf.db;

import java.util.concurrent.Callable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.stereotype.Component;

/**
 * Spring style cache for caching tiles to the database.
 * 
 * @author Emily
 *
 */
@Component
public class DbVectorTileCache extends AbstractValueAdaptingCache{
	
	private static final String cacheName = "vectortilecache";
	
	@Autowired TileDAO dao;
	
	public DbVectorTileCache() {
		super(false);
	}
	
	@Override
	public String getName() {
		return cacheName;
	}
	
	@Override
	public Object getNativeCache() {
		return null;
	}
	

	@Override
	protected Object lookup(Object key) {
		return dao.getTile(key.toString());
	}
	
	@Override
	public <T> T get(Object key, Callable<T> valueLoader) {
		if (dao.containsKey(key.toString())) {
			return (T) lookup(key);
		}
		try {
			return valueLoader.call();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
	
	@Override
	public void put(Object key, Object value) {
		dao.setTile(key.toString(), (byte[])value);
	}
	
	@Override
	public ValueWrapper putIfAbsent(Object key, Object value) {
		if (!dao.containsKey(key.toString())) put(key,value);
		return get(key);
	}
	
	@Override
	public void evict(Object key) {
		dao.removeTile(key.toString());
		
	}
	
	@Override
	public void clear() {
		dao.clear();		
	}
	
}
