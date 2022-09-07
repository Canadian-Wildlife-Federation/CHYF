/*
 * Copyright 2021 Canadian Wildlife Federation
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.data.DataUtilities;
import org.geotools.data.FeatureReader;
import org.geotools.data.Transaction;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.FlowDirection;
import net.refractions.chyf.datasource.ILayer;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.flowpathconstructor.ChyfProperties.Property;
import net.refractions.chyf.flowpathconstructor.datasource.IFlowpathDataSource;
import net.refractions.chyf.flowpathconstructor.datasource.TerminalNode;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.BoundaryEdge;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.ConstructionPoint;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.PointEngine;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkelLineString;


/**
 * Test cases for PointEngine
 * @author Emily
 *
 */
public class PointEngineTest {

	private ChyfProperties getChyfProperties() {
		ChyfProperties prop = new ChyfProperties();
		prop.setProperty(Property.PNT_VERTEX_DISTANCE, 0.0001);
		return prop;
	}
	
	/**
	 * Test the basic case where waterbody touches aoi on line
	 * @throws Exception
	 */
	@Test
	public void testGetBoundarySingle() throws Exception{
		SingleDataSource ds = new SingleDataSource();
		
		PointEngineInternal pe = new PointEngineInternal(ds, getChyfProperties());
		
		List<BoundaryEdge> edges = pe.getBoundary();
		Assert.assertEquals("invalid number of boundary edges", 1, edges.size());
		
		for (TerminalNode pnt : ds.getTerminalNodes()) {
			boolean found = false;
			for (BoundaryEdge be : edges) {
				if (be.getInOut().getCoordinate().equals2D(pnt.getPoint().getCoordinate())) {
					found = true;
					break;
				}
			}
			if (!found) {
				Assert.fail("no boundary edge found for found: " + pnt.getPoint().toText());
			}
		}
	}
	
	
	/**
	 * Test the case where we need multiple outputs along a waterbody that touches the aoi
	 * boundary.  This could be because of multiple single line outputs in the corresponding
	 * aoi.
	 * 
	 * @throws Exception
	 */
	@Test
	public void testGetBoundaryMulti() throws Exception{
		MultiDataSource ds = new MultiDataSource();
		
		PointEngineInternal pe = new PointEngineInternal(ds, getChyfProperties());

		List<BoundaryEdge> edges = pe.getBoundary();
		Assert.assertEquals("invalid number of boundary edges", 3, edges.size());
		
		for (TerminalNode pnt : ds.getTerminalNodes()) {
			boolean found = false;
			for (BoundaryEdge be : edges) {
				if (be.getInOut().getCoordinate().equals2D(pnt.getPoint().getCoordinate())) {
					found = true;
					break;
				}
			}
			if (!found) {
				Assert.fail("no boundary edge found for found: " + pnt.getPoint().toText());
			}
		}

	}
	
	
	private class MultiDataSource implements IFlowpathDataSource{

		@Override
		public FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer) throws IOException {
			return null;
		}

		@Override
		public FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer, ReferencedEnvelope bounds)
				throws IOException {
			return null;
		}

		@Override
		public FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer, Filter filter) throws IOException {
			return null;
		}

		@Override
		public FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer, ReferencedEnvelope bounds,
				Filter filter) throws IOException {
			return null;
		}

		@Override
		public FeatureReader<SimpleFeatureType, SimpleFeature> getWaterbodies() throws IOException {
			
			String text = "POLYGON (( 724 565, 676 653, 492 686, 338 622, 331 610, 326 595, 323 578, 321 563, 331 468, 458 394, 712 381, 724 565 ))";
			Polygon wb = null;
			try {
				WKTReader reader = new WKTReader();
				wb = (Polygon)reader.read(text);
			}catch (Exception ex) {
				throw new IOException(ex);
			}
			
			SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
			builder.add(ChyfAttribute.INTERNAL_ID.getFieldName(),String.class);
			builder.add("geometry", Polygon.class);
			builder.setName("SimpleTest");
			SimpleFeatureType type = builder.buildFeatureType();
			
			SimpleFeatureBuilder fbuilder = new SimpleFeatureBuilder(type);
			fbuilder.set(ChyfAttribute.INTERNAL_ID.getFieldName(), "1");
			fbuilder.set("geometry", wb);

			SimpleFeature sf = fbuilder.buildFeature("1");
			
			return DataUtilities.reader(Collections.singletonList(sf));
		}

		@Override
		public SimpleFeatureType getFeatureType(ILayer layer) throws IOException {
			return null;
		}

		@Override
		public void logError(String message, Geometry location, String process) throws IOException {
			System.out.println(message);
		}

		@Override
		public void logWarning(String message, Geometry location, String process) throws IOException {
			System.out.println(message);			
		}

		@Override
		public void close() throws Exception {
		}

		@Override
		public CoordinateReferenceSystem getCoordinateReferenceSystem() {
			return null;
		}

		@Override
		public List<Polygon> getAoi() throws IOException {
			WKTReader reader = new WKTReader();
			try {
				Polygon p = (Polygon)reader.read("POLYGON (( 724 565, 676 653, 492 686, 338 622, 331 610, 326 595, 323 578, 321 563, 331 468, 458 394, 712 381, 724 565 ))");
				return Collections.singletonList(p);
			}catch (Exception ex) {
				throw new IOException(ex);
			}
		}

		@Override
		public List<TerminalNode> getTerminalNodes() throws Exception {
			WKTReader reader = new WKTReader();
			try {
				List<TerminalNode> points = new ArrayList<>();
				Point p = (Point)reader.read("POINT ( 331 610 )");
				points.add(new TerminalNode(p, FlowDirection.UNKNOWN));
				p = (Point)reader.read("POINT ( 326 595 )");
				points.add(new TerminalNode(p, FlowDirection.UNKNOWN));
				p = (Point)reader.read("POINT ( 323 578 )");
				points.add(new TerminalNode(p, FlowDirection.UNKNOWN));
				return points;
			}catch (Exception ex) {
				throw new IOException(ex);
			}
		}


		@Override
		public void updateWaterbodyGeometries(Collection<Polygon> polygons) throws IOException { }

		@Override
		public void updateCoastline(FeatureId fid, LineString newls, Transaction tx) throws IOException { }

		@Override
		public void createConstructionsPoints(List<ConstructionPoint> points) throws IOException { }

		@Override
		public void writeSkeletons(Collection<SkelLineString> skeletons) throws IOException { }

		@Override
		public void removeExistingSkeletons(boolean bankOnly) throws IOException { }

		@Override
		public void addRankAttribute() throws Exception { }

		@Override
		public void flipFlowEdges(Collection<FeatureId> pathstoflip, Collection<FeatureId> processed) throws Exception { }

		@Override
		public void writeRanks(Map<FeatureId, RankType> ranks) throws Exception { }

		@Override
		public List<ConstructionPoint> getConstructionsPoints(Object catchmentId) throws IOException {
			return null;
		}

		@Override
		public Set<Coordinate> getOutputConstructionPoints() throws Exception {
			return null;
		}

		@Override
		public void writeFlowpathNames(HashMap<FeatureId, String[]> nameids) throws IOException {			
		}

		@Override
		public void populateNameIdTable() throws IOException {
		
		}
	}
	
	private class SingleDataSource extends MultiDataSource {

		
		@Override
		public List<TerminalNode> getTerminalNodes() throws Exception {
			WKTReader reader = new WKTReader();
			try {
				List<TerminalNode> points = new ArrayList<>();
				Point p = (Point)reader.read("POINT ( 331 610 )");
				points.add(new TerminalNode(p, FlowDirection.UNKNOWN));
				return points;
			}catch (Exception ex) {
				throw new IOException(ex);
			}
		}
	}
	
	private class PointEngineInternal extends PointEngine {
		
		public PointEngineInternal(IFlowpathDataSource dataSource, ChyfProperties properties ) {
			super(properties);
			this.dataSource = dataSource;
		}
		
		public List<BoundaryEdge> getBoundary() throws Exception{
			return super.getBoundary();
		}

	}
}
