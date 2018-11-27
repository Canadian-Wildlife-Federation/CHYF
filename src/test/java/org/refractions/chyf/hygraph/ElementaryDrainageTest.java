package org.refractions.chyf.hygraph;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import com.google.gson.stream.JsonReader;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

import net.refractions.chyf.ChyfDatastore;
import net.refractions.chyf.hygraph.ECatchment;
import net.refractions.chyf.rest.GeotoolsGeometryReprojector;

/**
 * For for testing upstream and downstream elementary drainages feature.
 * 
 * @author Emily
 *
 */
public class ElementaryDrainageTest {

	/**
	 * Test data and expected results for upstream and downstream 
	 * results.  This is a json file which contains an
	 * array of test data.  Each array contains an object that
	 * contains the point and results which is a array of
	 * polygons representing the upstream/downstream drainages
	 */
	public static final String UPSTREAMELEDRAINAGE_RESULTS = BasicTestSuite.RESULTS_DIR + "/upstreameledrainage_results.json";
	public static final String DOWNSTREAMELEDRAINAGE_RESULTS = BasicTestSuite.RESULTS_DIR + "/downstreameledrainage_results.json";
	
	@Rule
	public TestRule rule = BasicTestSuite.SETUP_RULE;
	
	@Test
	public void test_UpstreamElementaryCatchment() {
		try {
			validateCatchments(UPSTREAMELEDRAINAGE_RESULTS, 
					point-> BasicTestSuite.DATASTORE.getHyGraph().getUpstreamECatchments( BasicTestSuite.DATASTORE.getHyGraph().getECatchment(point), ChyfDatastore.MAX_RESULTS),
					"Upstream");
		}catch (Exception ex) {
			ex.printStackTrace();
			Assert.fail(ex.getMessage());
		}
	}
	
	@Test
	public void test_DownstreamElementaryCatchment() {
		try {
			validateCatchments(DOWNSTREAMELEDRAINAGE_RESULTS, 
					point-> BasicTestSuite.DATASTORE.getHyGraph().getDownstreamECatchments( BasicTestSuite.DATASTORE.getHyGraph().getECatchment(point), ChyfDatastore.MAX_RESULTS),
					"Downstream");
		}catch (Exception ex) {
			ex.printStackTrace();
			Assert.fail(ex.getMessage());
		}
	}
	
	private void validateCatchments(String dataFile, Function<Point, Collection<ECatchment>> function, String type) throws Exception{
		
		HashMap<Coordinate, List<Polygon>> testData = new HashMap<>();
		try(JsonReader reader = new JsonReader(new InputStreamReader(ClassLoader.getSystemResourceAsStream(dataFile)))){
			reader.beginArray();
			while(reader.hasNext()) {
				reader.beginObject();
				Coordinate point = null;
				List<Polygon> cachments = new ArrayList<>();
				
				while(reader.hasNext()) {
					
					String name = reader.nextName();
					if (name.equals("point")) {
						reader.beginArray();
						double x = reader.nextDouble();
						double y = reader.nextDouble();
						reader.endArray();
						point = new Coordinate(x,y);
					}else if (name.equals("results")) {
						reader.beginArray();
						
						while(reader.hasNext()) {
							reader.beginArray();
							
							List<LinearRing> lss = new ArrayList<>();
							while(reader.hasNext()) {
								reader.beginArray();
								List<Coordinate> points = new ArrayList<>();
								while(reader.hasNext()) {
									reader.beginArray();
									double x = reader.nextDouble();
									double y = reader.nextDouble();
									reader.endArray();
									points.add(new Coordinate(x,y));
								}
								LinearRing ls = BasicTestSuite.GF.createLinearRing(points.toArray(new Coordinate[points.size()]));
								lss.add(ls);
								reader.endArray();
							}
							reader.endArray();
							
							cachments.add(BasicTestSuite.GF.createPolygon(lss.get(0), lss.subList(1, lss.size()).toArray(new LinearRing[lss.size() - 1])   ));
						}
						reader.endArray();
					}
				}
				testData.put(point, cachments);
				reader.endObject();
			}
		}
		
		for (Entry<Coordinate, List<Polygon>> result : testData.entrySet()) {
			Point pnt = GeotoolsGeometryReprojector.reproject(BasicTestSuite.GF.createPoint(result.getKey()), ChyfDatastore.BASE_SRS);
			
			Collection<ECatchment> paths = function.apply(pnt);
			
			Assert.assertEquals(type + " elementary catchment at (" + result.getKey().x + ", " +result.getKey().y + ") returned incorrect number of polygons", result.getValue().size(), paths.size());
			
			for (Polygon ls : result.getValue()) {
				Polygon projection = GeotoolsGeometryReprojector.reproject(ls, ChyfDatastore.BASE_SRS);
				//find the same linestring in the actual results
				ECatchment found = null;
				for (ECatchment p : paths) {
					if (p.getPolygon().equalsExact(projection, 0.00001)) {
						found = p;
						break;
					}
				}
				Assert.assertNotNull(type + " elementary catchment at (" + result.getKey().x + ", " +result.getKey().y + ") does not match excepted results", found);
				if (found != null) paths.remove(found);
			}
		}
	}
	 
}
