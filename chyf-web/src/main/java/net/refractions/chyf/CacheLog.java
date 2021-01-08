package net.refractions.chyf;

import org.ehcache.event.CacheEvent;
import org.ehcache.event.CacheEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EHcache logger for debugging cache events
 * 
 * @author Emily
 *
 */
public class CacheLog implements CacheEventListener<Object, Object>{

	static final Logger logger = LoggerFactory.getLogger(CacheLog.class.getCanonicalName());

    @Override
    public void onEvent(CacheEvent<? extends Object, ? extends Object> cacheEvent) {
        logger.info(cacheEvent.getType().name() + ":" + cacheEvent.getKey().toString());
    }

}
