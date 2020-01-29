package net.refractions.chyf.directionalize;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.geotools.data.simple.SimpleFeatureReader;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.Args;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfDataSource.EfType;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.directionalize.graph.DGraph;
import net.refractions.chyf.directionalize.graph.EdgeInfo;

public class DirectionalizeEngine {
	
	static final Logger logger = LoggerFactory.getLogger(DirectionalizeEngine.class.getCanonicalName());

	public static void doWork(Path output) throws Exception {
		
		//identify sinks as
		//points on boundary that are out points
		//where flow edges intersect the coastline
		//known direction sink nodes
		//isolated groups with no know direction edges
		
		//TODO: dir bank edges
		
		Coordinate[] rch_sinks = new Coordinate[] {
				new Coordinate(-73.1197856, 46.04755),
		};
		
		Coordinate[] ns_sinks = new Coordinate[] {
				new Coordinate(-63.6169776, 44.4682273),
				new Coordinate(-63.7154359, 44.4738089),
				new Coordinate(-63.6622693, 44.730293),
				new Coordinate(-63.7422337, 44.4755759),
				new Coordinate(-63.9176512, 44.5723356),
				new Coordinate(-63.9269989, 44.6135756),
				new Coordinate(-63.7908933, 44.5359841),
				new Coordinate(-63.5236386, 44.5100644),
				new Coordinate(-63.6509696, 44.4705516),
				new Coordinate(-63.6000874, 44.6310746),
				new Coordinate(-63.9033525, 44.4981376),
				new Coordinate(-63.8489455, 44.4913351),
				new Coordinate(-63.788117, 44.527467),
				new Coordinate(-63.5507959, 44.4927473),
				new Coordinate(-63.5545175, 44.5507035),
				new Coordinate(-63.5457419, 44.5402316),
				new Coordinate(-63.9235355, 44.598274),
				new Coordinate(-63.8866043, 44.5039833),
				new Coordinate(-63.9091183, 44.5002626),
				new Coordinate(-63.8356721, 44.5256904),
				new Coordinate(-63.6189685, 44.4599689),
				new Coordinate(-63.5817221, 44.6210168),
				new Coordinate(-63.8512169, 44.5266291),
				new Coordinate(-63.5667197, 44.609058),
				new Coordinate(-63.6403868, 44.4717386),
				new Coordinate(-63.8783026, 44.5061484),
				new Coordinate(-63.8472241, 44.4969476),
				new Coordinate(-63.6283675, 44.4756572),
				new Coordinate(-63.6061015, 44.6946811),
				new Coordinate(-63.7372036, 44.4791821),
				new Coordinate(-63.6564389, 44.7245524),
				new Coordinate(-63.4550154, 44.5927255),
				new Coordinate(-63.4928857, 44.6101535),
				new Coordinate(-63.8256175, 44.5240438),
				new Coordinate(-63.6081129, 44.4937358),
				new Coordinate(-63.5274822, 44.4982034),
				new Coordinate(-63.6057403, 44.471212),
				new Coordinate(-63.7846455, 44.4912234),
				new Coordinate(-63.5438818, 44.6438562),
				new Coordinate(-63.7639197, 44.525799),
				new Coordinate(-63.5069836, 44.6193753),
				new Coordinate(-63.6189634, 44.6402193),
				new Coordinate(-63.7873957, 44.4803311),
				new Coordinate(-63.8646065, 44.5107159),
				new Coordinate(-63.8309607, 44.5255029),
				new Coordinate(-63.9136217, 44.5835804),
				new Coordinate(-63.6097847, 44.4790512),
				new Coordinate(-63.6196391, 44.7050887),
				new Coordinate(-63.6718515, 44.4672442),
				new Coordinate(-63.918142, 44.5938259),
				new Coordinate(-63.5567425, 44.5734791),
				new Coordinate(-63.8699959, 44.5065917),
				new Coordinate(-63.7127943, 44.4760274),
				new Coordinate(-63.716844, 44.4728822),
				new Coordinate(-63.5470179, 44.4886531),
				new Coordinate(-63.7410595, 44.4801194),
				new Coordinate(-63.7091261, 44.475661),
				new Coordinate(-63.9321307, 44.5041713),
				new Coordinate(-63.8679179, 44.5108285),
				new Coordinate(-63.6737816, 44.7141979),
				new Coordinate(-63.837251, 44.7749446),
		};
		
		Coordinate[] kotl_sinks = new Coordinate[] {
				new Coordinate(-1525013.4876424193, 256939.90300969686),
				new Coordinate(-1610422.6727822297, 324897.4816691745),
				new Coordinate(-1556096.086392175, 267852.8597435551),
				new Coordinate(-1557379.3778216145, 268309.9421718884),
				new Coordinate(-1551327.158801591, 266155.09274093714),
				new Coordinate(-1481859.0223353594, 242209.77449878491),
				new Coordinate(-1481485.80928242, 242084.46961320844),
				new Coordinate(-1492129.5920241098, 245676.8253733581),
				new Coordinate(-1477709.8756824702, 240820.08259002678),
				new Coordinate(-1533600.178737173, 259928.05372288916),
				new Coordinate(-1484287.9535111622, 243027.6726342803),
				new Coordinate(-1484028.302458791, 242943.28363426123),
				new Coordinate(-1543358.345962427, 263355.86887115147),
				new Coordinate(-1549119.0604792095, 265374.0990905566),
				new Coordinate(-1476120.6519855375, 255255.91425246466),
				new Coordinate(-1545457.9849015153, 264090.73921487574),
				new Coordinate(-1526805.5106103648, 257561.7400287371),
				new Coordinate(-1549886.6195941463, 265645.11931585986),
				new Coordinate(-1531592.8900582874, 259233.7780586984),
				new Coordinate(-1521253.3737629957, 255638.06378934253),
				
				//sink in middle of group
				new Coordinate(-1558424.5666795203, 339378.88986521866),
		};

		Coordinate c3 = new Coordinate(-1521253.37376299570314586, 255638.06378934253007174);
		Coordinate mainsink = new Coordinate( -1610422.6727822297, 324897.4816691745);


		List<Coordinate> out = new ArrayList<>();
		
//		for (Coordinate c : kotl_sinks)  out.add(c);			
//		for (Coordinate c : rch_sinks)  out.add(c);
		for (Coordinate c : ns_sinks)  out.add(c);
//		out.add(c3);
//		out.add(mainsink);
		
		//TODO make sure sinks are not udplicated as this cases infinite loop
		try(ChyfGeoPackageDataSource dataSource = new ChyfGeoPackageDataSource(output)){
			List<EdgeInfo> edges = new ArrayList<>();
			
			try(SimpleFeatureReader reader = dataSource.getFlowpaths(null)){
				while(reader.hasNext()) {
					SimpleFeature sf = reader.next();
					
					LineString ls = ChyfDataSource.getLineString(sf);

					EfType eftype = EfType.parseType((Integer)sf.getAttribute("ef_type"));
					if (eftype == EfType.BANK) continue; //exclude these for now
					
					DirectionType dtype = DirectionType.UNKNOWN;
					if (eftype == EfType.SKELETON) {
						dtype = DirectionType.UNKNOWN;
					}
					EdgeInfo ei = new EdgeInfo(ls.getCoordinateN(0), ls.getCoordinateN(ls.getCoordinates().length - 1),
							eftype,
							sf.getIdentifier(), ls.getLength(), 
							dtype);
					
					edges.add(ei);
					
				}
			}
			
			DGraph graph = DGraph.buildGraphLines(edges);
			
			Directionalizer dd = new Directionalizer();
			Set<FeatureId> fids = dd.directionalize(graph, out);
			dataSource.flipFlowEdges(fids);

		}
			
			
		System.out.println("checking cycles");
		CycleChecker checker = new CycleChecker();
		if (checker.checkCycles(output)) {
			System.out.println("output still has cycles");
		}
		

	}
	


	
	public static void main(String[] args) throws Exception {		
		Args runtime = Args.parseArguments(args);
		if (runtime == null) {
			Args.printUsage("DirectionalizeEngine");
			return;
		}
		runtime.prepareOutput();
		
//		Path file = Paths.get("C:\\temp\\chyf\\input2\\KOTL.out.gpkg");
		
		long now = System.nanoTime();
		DirectionalizeEngine.doWork(runtime.getOutput());
		long then = System.nanoTime();
		
		logger.info("Processing Time: " + ( (then - now) / Math.pow(10, 9) ) + " seconds" );
	}
}