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
package net.refractions.chyf.watershed.builder;

import java.io.IOException;
import java.util.Collection;
import java.util.function.BiConsumer;

import org.geotools.data.FeatureReader;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

import net.refractions.chyf.datasource.ChyfDataSource;

/**
 * Data source input for flowpath constructore tools
 * 
 * @author Emily
 *
 */
public interface ICatchmentDelineatorDataSource extends ChyfDataSource{

	public static final String HYDRO_EDGE_LAYER = "HydroEdges"; 
	public static final String WATERSHED_BOUNDARY_LAYER = "CatchmentConstructionEdges";
	public static final String BLOCK_LAYER = "ProcessingBlocks";
	
	public boolean createLayer(SimpleFeatureType ft, ReferencedEnvelope workingExtent);

	public FeatureReader<SimpleFeatureType, SimpleFeature> query(String layerName, ReferencedEnvelope bounds, Filter filter) throws IOException ;

	public void deleteFeatures(String name, Filter filter) ;
	
	
	public void updateBlock(DataBlock dataBlock);
	
	public void reprecisionAll() throws IOException;

	public <T> void writeObjects(String layerName, Collection<T> data, BiConsumer<T,SimpleFeature> func) ;

	public SimpleFeatureType getHydroEdgeFT();
	
	public SimpleFeatureType getBlockFT();

	public SimpleFeatureType getWatershedBoundaryEdgeFT();
}
