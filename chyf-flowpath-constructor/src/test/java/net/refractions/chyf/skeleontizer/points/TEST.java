package net.refractions.chyf.skeleontizer.points;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.filter.Filter;

import net.refractions.chyf.datasource.ChyfDataSource;

public class TEST {

	public static void main(String args[]) throws Exception {
		
//		WKTReader reader = new WKTReader();
//		
//		
//		LineString ls = (LineString) reader.read("LINESTRING(0 0 0, 1 1 0, 2 2 2)");
//		System.out.println(ls.toText());
//		
////		WKBWriter writer = new WKBWriter(3);
////		byte[] b = writer.write(ls);
////		
////		WKBReader reader2 = new WKBReader();
////		LineString ls2 = (LineString) reader2.read(b);
////		System.out.println(ls2);
//		
//		Path p = Paths.get("C:\\temp\\out.gpkg");
//		if (Files.exists(p)) {
//			Files.delete(p);
//		}
//		GeoPackage geopkg = new GeoPackage(p.toFile());
//		geopkg.init();
//		
//		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
//		builder.add("the_geom", LineString.class, 4326);
//		builder.setName("test");
//		SimpleFeatureType pntType = builder.buildFeatureType();
//	
//		
//		FeatureEntry entry = new FeatureEntry();
//		entry.setTableName("test");
//		entry.setZ(true);
//		entry.setBounds(new ReferencedEnvelope(ls.getEnvelopeInternal(), builder.getCRS()));
////		geopkg.create(entry, pntType);
//		
//		SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(pntType);
//		featureBuilder.set("the_geom", ls);
//		SimpleFeature sf = featureBuilder.buildFeature(String.valueOf(1));
//		
//		geopkg.add(entry, DataUtilities.collection(Collections.singletonList(sf)));
//		
//		geopkg.close();
		Path p = Paths.get("C:\\temp\\chyf\\test\\dataZM.gpkg");
		GeoPackage geopkg = new GeoPackage(p.toFile());
		
		try(SimpleFeatureReader rreader = geopkg.reader(geopkg.feature("EFlowpaths"), Filter.INCLUDE, null)){

			while(rreader.hasNext()) {
				SimpleFeature ssf = rreader.next();
				LineString ls3 = ChyfDataSource.getLineString(ssf);
				for (Coordinate c : ls3.getCoordinates()) System.out.println(c.x +":" + c.y +":"+c.z);
//				System.out.println(ChyfDataSource.getLineString(ssf).toText());
			}
		}
		geopkg.close();
	}
}
