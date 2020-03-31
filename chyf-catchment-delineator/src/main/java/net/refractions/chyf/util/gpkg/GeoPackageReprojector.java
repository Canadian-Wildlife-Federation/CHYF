package net.refractions.chyf.util.gpkg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.geotools.data.DataUtilities;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.feature.SchemaException;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import net.refractions.chyf.util.ReprojectionUtils;


public class GeoPackageReprojector {

	final static Options options = new Options();
	static {
		options.addOption("s", true, "ESPG code/SRID");
		options.addOption("p", true, "precision to use");
	}

	private static Integer srid = null;
	private static CoordinateReferenceSystem crs = null;
	private static Double precision = null;
	private static PrecisionModel precisionModel = null;
	private static Path inFile = null;
	private static Path outFile = null;
	
	private static GeoPackage inGpkg;
	private static GeoPackage outGpkg;
	
    public static void main(String[] args) throws IOException, SchemaException {
    	try {
			CommandLineParser parser = new DefaultParser();
			CommandLine cmd = parser.parse( options, args);
			
			if (cmd.hasOption("s")) {
				srid = Integer.parseInt(cmd.getOptionValue("s"));
				crs = ReprojectionUtils.srsCodeToCRS(srid);
			}
			if (cmd.hasOption("p")) {
				precision = Double.parseDouble(cmd.getOptionValue("p"));
				precisionModel = new PrecisionModel(precision);
			}
			if (cmd.getArgList().size() == 2) {
				inFile = Paths.get(cmd.getArgList().get(0));
				if(!Files.exists(inFile)) {
					throw new ParseException("Input file not found: " + inFile);
				}
				outFile = Paths.get(cmd.getArgList().get(1));
				deleteOutputFile(outFile);
			} else {
				throw new ParseException("Incorrect number of arguments, must be 2: INFILE, OUTFILE");
			}
		} catch (ParseException | NumberFormatException e) {
			System.err.println(e.getMessage());
			printUsage();
			System.exit(-1);
		}
    	process();
    	System.out.println("Done.");
    }
    
    private static void process() throws IOException, SchemaException {
    	inGpkg = new GeoPackage(inFile.toFile());
    	inGpkg.init();

    	outGpkg = new GeoPackage(outFile.toFile());
    	outGpkg.init();

    	for(FeatureEntry fe : inGpkg.features()) {
    		try(SimpleFeatureReader reader = inGpkg.reader(fe, Filter.INCLUDE, null)) {
    			System.out.println("Copying layer: " + fe.getTableName());
    			CoordinateReferenceSystem inCrs = null;
    			SimpleFeatureType featureType = reader.getFeatureType();
    			if(fe.getSrid() != srid) {
    				fe.setBounds(ReprojectionUtils.reproject(fe.getBounds(), crs));
    				fe.setSrid(srid);
    				inCrs = reader.getFeatureType().getCoordinateReferenceSystem();
        			featureType = DataUtilities.createSubType( reader.getFeatureType(), null, crs );
    			}
    			outGpkg.create(fe, featureType);
    			try(Transaction tx = new DefaultTransaction(); SimpleFeatureWriter writer = outGpkg.writer(fe, true, null, tx)) {
	    			while(reader.hasNext()) {
	    				SimpleFeature in = reader.next();
	    				SimpleFeature out = writer.next();
	    				out.setAttributes(in.getAttributes());
	    				Geometry g = (Geometry) in.getDefaultGeometry();
	    				if(inCrs != null) {
	    					g = ReprojectionUtils.reproject(g, inCrs, crs);
	    				}
	    				if(precisionModel != null) {
	    					g = GeometryPrecisionReducer.reduce(g, precisionModel);
	    				}
	    				out.setDefaultGeometry(g);
	    				writer.write();
	    			}
	    			tx.commit();
    			}
    		}
    	}
    }

	private static void printUsage() {
		new HelpFormatter().printHelp("GeoPackageReprojector [OPTIONS] <INFILE> <OUTFILE>", options);
	}

	public static final void deleteOutputFile(Path outputFile) throws IOException {
		if (Files.exists(outputFile)) {
			Files.delete(outputFile);
			//also delete -shm and -wal if files
			Path p = outputFile.getParent().resolve(outputFile.getFileName().toString() + "-shm");
			if (Files.exists(p)) Files.delete(p);
			
			p = outputFile.getParent().resolve(outputFile.getFileName().toString() + "-wal");
			if (Files.exists(p)) Files.delete(p);
		}
	}
}
