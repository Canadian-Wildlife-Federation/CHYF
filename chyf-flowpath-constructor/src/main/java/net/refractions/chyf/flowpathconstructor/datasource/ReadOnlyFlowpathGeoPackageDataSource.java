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
package net.refractions.chyf.flowpathconstructor.datasource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureReader;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.ChyfLogger;
import net.refractions.chyf.datasource.ChyfAttribute;
import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;
import net.refractions.chyf.datasource.FlowDirection;
import net.refractions.chyf.datasource.Layer;
import net.refractions.chyf.datasource.RankType;
import net.refractions.chyf.flowpathconstructor.skeletonizer.points.ConstructionPoint;
import net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkelLineString;

/**
 * Extension of the geopackage data source for flowpath computation that is read-only.
 * (used to support stand alone cycle checker application)
 * 
 * @author Emily
 *
 */
public class ReadOnlyFlowpathGeoPackageDataSource extends ChyfGeoPackageDataSource implements IFlowpathDataSource{
	
	static final Logger logger = LoggerFactory.getLogger(ReadOnlyFlowpathGeoPackageDataSource.class.getCanonicalName());
	
	public ReadOnlyFlowpathGeoPackageDataSource(Path geopackageFile) throws Exception {
		super(geopackageFile);
		ChyfLogger.INSTANCE.setDataSource(this);
	}

	
	/**
	 * Returns all points from the terminal node layer.  The user data associated
	 * with each point is the value of the FlowDirection attribute associated
	 * with the TerminalNode.
	 * 
	 * @return
	 * @throws Exception
	 */
	@Override
	public List<TerminalNode> getTerminalNodes() throws Exception{
		List<TerminalNode> pnt = new ArrayList<>();
		try(SimpleFeatureReader reader = getFeatureReader(Layer.TERMINALNODES, Filter.INCLUDE, null)){		
			Name flowdirAttribute = ChyfDataSource.findAttribute(reader.getFeatureType(), ChyfAttribute.FLOWDIRECTION);
			Map<ChyfAttribute, Name> nameAttributes = findRiverNameAttributes(reader.getFeatureType());
			
			while(reader.hasNext()){
				SimpleFeature point = reader.next();
				Point pg = ChyfDataSource.getPoint(point);
				FlowDirection fd = FlowDirection.parseValue( ((Number)point.getAttribute(flowdirAttribute)).intValue() );
				TerminalNode node = new TerminalNode(pg, fd);
				node.setNames(getRiverNameIds(nameAttributes, point));
				pnt.add(node);
				
			}
		}
		return pnt;
	}


	@Override
	public void updateWaterbodyGeometries(Collection<Polygon> polygons) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");
	}


	@Override
	public void updateCoastline(FeatureId fid, LineString newls, Transaction tx) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");
	}


	@Override
	public void createConstructionsPoints(List<ConstructionPoint> points) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");		
	}


	@Override
	public void writeSkeletons(Collection<SkelLineString> skeletons) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");		
	}


	@Override
	public void removeExistingSkeletons(boolean bankOnly) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");	
	}


	@Override
	public List<ConstructionPoint> getConstructionsPoints(Object catchmentId) throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");
	}


	@Override
	public void addRankAttribute() throws Exception {
		throw new UnsupportedOperationException("Not supported for read only dataset");
	}


	@Override
	public void flipFlowEdges(Collection<FeatureId> pathstoflip, Collection<FeatureId> processed) throws Exception {
		throw new UnsupportedOperationException("Not supported for read only dataset");
	}


	@Override
	public Set<Coordinate> getOutputConstructionPoints() throws Exception {
		throw new UnsupportedOperationException("Not supported for read only dataset");
	}


	@Override
	public void writeRanks(Map<FeatureId, RankType> ranks) throws Exception {
		throw new UnsupportedOperationException("Not supported for read only dataset");	
	}
	
	@Override
	public void writeFlowpathNames(HashMap<FeatureId, String[]> nameids) throws IOException{
		throw new UnsupportedOperationException("Not supported for read only dataset");
	}

	@Override
	public void populateNameIdTable() throws IOException {
		throw new UnsupportedOperationException("Not supported for read only dataset");
	}
}
