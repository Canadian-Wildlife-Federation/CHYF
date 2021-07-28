/*******************************************************************************
 * Copyright 2020 Government of Canada
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
 *******************************************************************************/
package net.refractions.chyf.datasource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.geotools.data.FeatureReader;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.crs.CoordinateReferenceSystem;


/**
 * CHyF data source reader that provides general functions for reading input data
 * from geotools feature readers.
 * 
 * @author Emily
 *
 */
public interface ChyfDataSource extends AutoCloseable {
	
	public static final FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

	public CoordinateReferenceSystem getCoordinateReferenceSystem() ;

	/**
	 * Queries the specified layer. 
	 * @param layer the Layer to be queried
	 * @return a SimpleFeatureReader with all features from the specified Layer
	 * @throws IOException
	 */
	FeatureReader<SimpleFeatureType, SimpleFeature> query(Layer layer) throws IOException;
	
	/**
	 * Queries the specified layer using the bounds provided. 
	 * The bounds can be null.
	 * @param layer the Layer to be queried
	 * @param bounds limit the returned features to be within the specified bounds (or null for no bounds)
	 * @return a SimpleFeatureReader with the matched features from the specified Layer
	 * @throws IOException
	 */
	FeatureReader<SimpleFeatureType, SimpleFeature> query(Layer layer, ReferencedEnvelope bounds) throws IOException;

	/**
	 * Queries the specified layer using the filter provided. 
	 * The bounds and/or filter can be null.
	 * @param layer the Layer to be queried
	 * @param filter limit the returned features to match the specified filter (or null for no filter)
	 * @return a SimpleFeatureReader with the matched features from the specified Layer
	 * @throws IOException
	 */
	FeatureReader<SimpleFeatureType, SimpleFeature> query(Layer layer, Filter filter) throws IOException;

	/**
	 * Queries the specified layer using the bounds and filter provided. 
	 * The bounds and/or filter can be null.
	 * @param layer the Layer to be queried
	 * @param bounds limit the returned features to be within the specified bounds (or null for no bounds)
	 * @param filter limit the returned features to match the specified filter (or null for no filter)
	 * @return a SimpleFeatureReader with the matched features from the specified Layer
	 * @throws IOException
	 */
	FeatureReader<SimpleFeatureType, SimpleFeature> query(Layer layer, ReferencedEnvelope bounds, Filter filter) throws IOException;
		
    /**
     * 
     * @return all catchments of water type
     * 
     * @throws IOException
     */
	FeatureReader<SimpleFeatureType, SimpleFeature> getWaterbodies() throws IOException;

	public SimpleFeatureType getFeatureType(Layer layer) throws IOException;

		
	/**
	 * 
	 * @return a filter that will filter out water catchments based on the ec type
	 * @deprecated
	 * @see getECatchmentTypeFilter
	 */
	public default Filter getWbTypeFilter() {
		return ff.equals(ff.property(ChyfAttribute.ECTYPE.getFieldName()), ff.literal(EcType.WATER.getChyfValue()));
	}
	
	public default Filter getECatchmentTypeFilter(EcType... ecTypes) {
		List<Filter> filters = new ArrayList<Filter>(ecTypes.length);
		for(EcType type : ecTypes) {
			filters.add(ff.equals(ff.property(ChyfAttribute.ECTYPE.getFieldName()), ff.literal(type.getChyfValue())));
		}
		if(filters.size() == 1) {
			return filters.get(0);
		}
		return ff.or(filters);
	}
	
	public default Filter getEFlowpathTypeFilter(EfType... efTypes) {
		List<Filter> filters = new ArrayList<Filter>(efTypes.length);
		for(EfType type : efTypes) {
			filters.add(ff.equals(ff.property(ChyfAttribute.EFTYPE.getFieldName()), ff.literal(type.getChyfValue())));
		}
		if(filters.size() == 1) {
			return filters.get(0);
		}
		return ff.or(filters);
	}
	
	public static LineString getLineString(SimpleFeature feature) {
		Geometry g = (Geometry)feature.getDefaultGeometry();
		if (g instanceof LineString) {
			return (LineString)g;
		} else if (g instanceof MultiLineString && ((MultiLineString)g).getNumGeometries() == 1  ) {
			return (LineString)g.getGeometryN(0);
		} else {
			throw new RuntimeException("Geometry type " + g.getGeometryType() + "  not supported for linestring.");
		}
	}
	
	public static Polygon getPolygon(SimpleFeature feature) {
		Geometry g = (Geometry) feature.getDefaultGeometry();
		if (g instanceof Polygon) {
			return (Polygon) g;
		} else if (g instanceof MultiPolygon && ((MultiPolygon)g).getNumGeometries() == 1) {
			return (Polygon) ((MultiPolygon)g).getGeometryN(0);
		} else {
			throw new RuntimeException("Geometry type " + g.getGeometryType() + " not supported for polygon");
		}
	}
	
	public static Point getPoint(SimpleFeature feature) {
		Geometry g = (Geometry) feature.getDefaultGeometry();
		if (g instanceof Point) {
			return (Point) g;
		} else if (g instanceof MultiPoint && ((MultiPoint)g).getNumGeometries() == 1) {
			return (Point) ((MultiPoint)g).getGeometryN(0);
		} else {
			throw new RuntimeException("Geometry type " + g.getGeometryType() + " not supported for point");
		}
	}
	
	public static Name findAttribute(SimpleFeatureType ft, ChyfAttribute attribute) {
		for (AttributeDescriptor ad : ft.getAttributeDescriptors()) {
			if (ad.getLocalName().equalsIgnoreCase(attribute.getFieldName())) return ad.getName();
		}
		return null;
	}

}
