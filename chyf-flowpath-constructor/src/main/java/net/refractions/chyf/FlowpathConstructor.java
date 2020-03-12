package net.refractions.chyf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.FlowpathGeoPackageDataSource;
import net.refractions.chyf.directionalize.DirectionalizeEngine;
import net.refractions.chyf.rank.RankEngine;
import net.refractions.chyf.skeletonizer.points.PointEngine;
import net.refractions.chyf.skeletonizer.voronoi.SkeletonEngine;

public class FlowpathConstructor {

	static final Logger logger = LoggerFactory.getLogger(FlowpathConstructor.class.getCanonicalName());

	
	public static void main(String[] args) throws Exception {		
		FlowpathArgs runtime = new FlowpathArgs("FlowpathConstructor");
		if (!runtime.parseArguments(args)) return;
		
		runtime.prepareOutput();
		
		long now = System.nanoTime();
		try(FlowpathGeoPackageDataSource dataSource = new FlowpathGeoPackageDataSource(runtime.getOutput())){
			ChyfProperties prop = runtime.getPropertiesFile();
			if (prop == null) prop = ChyfProperties.getProperties(dataSource.getCoordinateReferenceSystem());
			logger.info("Generating Constructions Points");
			PointEngine.doWork(dataSource, prop);
			logger.info("Generating Skeletons");
			SkeletonEngine.doWork(dataSource, prop, runtime.getCores());
			logger.info("Directionalizing Dataset");
			DirectionalizeEngine.doWork(dataSource, prop);
			logger.info("Computing Rank");
			RankEngine.doWork(dataSource, prop);
		}
		long then = System.nanoTime();
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
	
}
