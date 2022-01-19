package net.refractions.chyf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.refractions.chyf.model.VectorTileCache;

@Component
@EnableScheduling
public class Scheduler {

    private Logger logger = LoggerFactory.getLogger(Scheduler.class);

	@Autowired
	VectorTileCache cache;
	
	@Scheduled(fixedDelayString = "${chyf.cachecleanupdelay}000")
	public void cleanupCache() {
		logger.debug("cleaning chyf vector tile cache");
		cache.cleanUpCache();
	}
}
