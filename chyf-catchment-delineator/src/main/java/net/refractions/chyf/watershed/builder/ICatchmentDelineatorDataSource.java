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
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;

import net.refractions.chyf.datasource.ChyfDataSource;
import net.refractions.chyf.datasource.ILayer;

/**
 * Data source interface for catchment delineator tools
 * 
 * @author Emily
 *
 */
public interface ICatchmentDelineatorDataSource extends ChyfDataSource{

	/**
	 * Data layers required for catchment layer
	 * 
	 * @author Emily
	 *
	 */
	public enum CatchmentLayer implements ILayer{
		HYDRO_EDGE_LAYER("HydroEdges"),
		WATERSHED_BOUNDARY_LAYER("CatchmentConstructionEdges"),
		BLOCK_LAYER("ProcessingBlocks");

		private String layerName;
		
		CatchmentLayer(String layerName){
			this.layerName = layerName;
		}
		
		@Override
		public String getLayerName() {
			return this.layerName;
		}
		
	}
	
	/**
	 * Creates a new layer with the given feature type.
	 * 
	 * @param ft
	 * @param workingExtent
	 * @return
	 */
	public boolean createLayer(SimpleFeatureType ft, ReferencedEnvelope workingExtent);

	/**
	 * Query dataset for features
	 * 
	 * @param layer
	 * @param bounds
	 * @param filter
	 * @return
	 * @throws IOException
	 */
	public FeatureReader<SimpleFeatureType, SimpleFeature> query(ILayer layer, ReferencedEnvelope bounds, Filter filter) throws IOException ;
	
	/**
	 * Delete all features that match the given filter
	 * 
	 * @param layer
	 * @param filter
	 */
	public void deleteFeatures(ILayer layer, Filter filter) ;
	
	/**
	 * Update data block
	 * 
	 * @param dataBlock
	 */
	public void updateBlock(DataBlock dataBlock);
	
	/**
	 * Reprecise all layers in the dataset
	 * 
	 * @throws IOException
	 */
	public void reprecisionAll() throws IOException;

	/**
	 * Write a set of features to the layer
	 * 
	 * @param <T>
	 * @param layer
	 * @param data
	 * @param func
	 */
	public <T> void writeObjects(ILayer layer, Collection<T> data, BiConsumer<T,SimpleFeature> func) ;

	/**
	 * Get hydro edge features type
	 * @return
	 */
	public SimpleFeatureType getHydroEdgeFT();
	
	/**
	 * Get block feature type
	 * @return
	 */
	public SimpleFeatureType getBlockFT();

	/**
	 * Get watershed boundary edges feature type
	 * @return
	 */
	public SimpleFeatureType getWatershedBoundaryEdgeFT();
}
