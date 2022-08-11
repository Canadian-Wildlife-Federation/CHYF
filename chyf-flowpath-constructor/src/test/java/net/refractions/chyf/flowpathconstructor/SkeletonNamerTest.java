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
package net.refractions.chyf.flowpathconstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.geotools.filter.identity.FeatureIdImpl;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.opengis.filter.identity.FeatureId;

import edu.emory.mathcs.backport.java.util.Arrays;
import net.refractions.chyf.datasource.FlowDirection;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource.NodeType;
import net.refractions.chyf.flowpathconstructor.skeletonizer.names.SkeletonNamer;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.ConstructionPoint;

/**
 * Test cases for bank skeletonizer.  This does not test the output to 
 * ensure it is valid, it just runs tests and ensure a result is returned.
 * 
 * @author Emily
 *
 */
public class SkeletonNamerTest {

	private WKTReader reader = new WKTReader();

	
	//very basic test to ensure names are carried across correctly
	@Test
	public void test1() throws Exception{
		
		String waterbody = "POLYGON (( 0 0, 0 5, 3 5, 5 5, 5 2,  5 0, 0 0))";
		Polygon polygon = (Polygon)reader.read(waterbody);
		
		
		ConstructionPoint c1 = new ConstructionPoint(new Coordinate(0, 0),NodeType.FLOWPATH,FlowDirection.INPUT, null);
		c1.addNames(new String[] {"edge1namea", "edge1nameb", "edge1namec"});
		c1.addNames(new String[] {"edge2namea"});
		ConstructionPoint c2 = new ConstructionPoint(new Coordinate(3, 5),NodeType.FLOWPATH,FlowDirection.INPUT, null);
		c2.addNames(new String[] {null, "edge3nameb"});
		ConstructionPoint c3 = new ConstructionPoint(new Coordinate(5, 2.5),NodeType.WATER,FlowDirection.OUTPUT, null);
		c3.addNames(new String[] {"edge1namea", null, "edge1namec"});
		ConstructionPoint c4 = new ConstructionPoint(new Coordinate(4, 5),NodeType.BANK,FlowDirection.INPUT, null);
		ConstructionPoint c5 = new ConstructionPoint(new Coordinate(0, 5),NodeType.BANK,FlowDirection.INPUT, null);
		ConstructionPoint c6 = new ConstructionPoint(new Coordinate(2.5,0),NodeType.BANK,FlowDirection.INPUT, null);
		
		List<ConstructionPoint> cpoints = Arrays.asList(new ConstructionPoint[] {c1, c2, c3, c4, c5, c6});
	
		String[] skels = new String[] {
				"LINESTRING (2.5 0, 2.5322476701743266 2.532258064516129)",
				"LINESTRING (3 5, 3 2.9998977534562212)",
				"LINESTRING (3 2.9998977534562212, 2.5322476701743266 2.532258064516129)",
				"LINESTRING (4 5, 3.963125 3.963911206317204, 3 2.9998977534562212)",
				"LINESTRING (0 5, 2.5322476701743266 2.532258064516129)",
				"LINESTRING (2.5322476701743266 2.532258064516129, 5 2.5)",
				"LINESTRING (0 0, 2.5322476701743266 2.532258064516129)",
		};
		List<LineString> skeletons = new ArrayList<>();
		for (int i = 0; i < skels.length; i ++) {
			LineString ls = (LineString)reader.read(skels[i]);
			ls.setUserData(new Object[] {new FeatureIdImpl(String.valueOf(i)), RankType.PRIMARY});
			skeletons.add(ls);
		}
		HashMap<FeatureId, String[]> names = SkeletonNamer.INSTANCE.nameFlowpaths(skeletons, cpoints, polygon);
		for (Entry<FeatureId, String[]> item: names.entrySet()) {
			if (item.getKey().toString().equals("0")||
				item.getKey().toString().equals("1") ||
				item.getKey().toString().equals("2") ||
				item.getKey().toString().equals("3") ||
				item.getKey().toString().equals("4")) {
				Assert.assertNull("invalid naming of skeletons", item.getValue());
			}else {
				Assert.assertEquals("invalid naming of skeletons", 3, item.getValue().length);
				Assert.assertEquals("invalid naming of skeletons", "edge1namea", item.getValue()[0]);
				Assert.assertEquals("invalid naming of skeletons", null, item.getValue()[1]);
				Assert.assertEquals("invalid naming of skeletons", "edge1namec", item.getValue()[2]);
			}
		}
	}
}
