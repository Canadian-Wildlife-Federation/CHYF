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
package net.refractions.chyf.datasource;

import java.io.IOException;

import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureReader;
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


/**
 * CHyF data source reader that provides general functions for reading input data
 * from geotools feature readers.
 * 
 * @author Emily
 *
 */
public interface ChyfDataSource extends AutoCloseable {
	
	public static FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

	/**
	 * Get a feature reader for the given layer
	 * @param layer
	 * @param filter
	 * @param tx
	 * @return
	 * @throws IOException
	 */
	public SimpleFeatureReader getFeatureReader(Layer layer, Filter filter, Transaction tx) throws IOException;
    
	/**
	 * queries the given layer using a bounding box and filter
	 * @param bounds
	 * @param layer
	 * @param filter
	 * @return
	 * @throws IOException
	 */
	public SimpleFeatureReader query(ReferencedEnvelope bounds, Layer layer, Filter filter) throws IOException;

		
	/**
	 * 
	 * @return a filter that will filter out water catchments based on the ec type
	 */
	public default Filter getWbTypeFilter() {
		return ff.equals(ff.property(ChyfAttribute.ECTYPE.getFieldName()), ff.literal(EcType.WATER.getChyfValue()));
	}
	
	public static LineString getLineString(SimpleFeature feature) throws Exception{
		Geometry g = (Geometry)feature.getDefaultGeometry();
		if (g instanceof LineString) {
			return (LineString)g;
		}else if (g instanceof MultiLineString && ((MultiLineString)g).getNumGeometries() == 1  ) {
			return (LineString)g.getGeometryN(0);
		}else {
			throw new Exception("Geometry type " + g.getGeometryType() + "  not supported for linestring.");
		}
	}
	
	public static Polygon getPolygon(SimpleFeature feature) throws Exception{
		Geometry g = (Geometry) feature.getDefaultGeometry();
		if (g instanceof Polygon) {
			return (Polygon) g;
		}else if (g instanceof MultiPolygon && ((MultiPolygon)g).getNumGeometries() == 1) {
			return (Polygon) ((MultiPolygon)g).getGeometryN(0);
		}else {
			throw new Exception("Geometry type " + g.getGeometryType() + " not supported for polygon");
		}
	}
	
	public static Point getPoint(SimpleFeature feature) throws Exception{
		Geometry g = (Geometry) feature.getDefaultGeometry();
		if (g instanceof Point) {
			return (Point) g;
		}else if (g instanceof MultiPoint && ((MultiPoint)g).getNumGeometries() == 1) {
			return (Point) ((MultiPoint)g).getGeometryN(0);
		}else {
			throw new Exception("Geometry type " + g.getGeometryType() + " not supported for point");
		}
	}
	
	public static Name findAttribute(SimpleFeatureType ft, ChyfAttribute attribute) {
		for (AttributeDescriptor ad : ft.getAttributeDescriptors()) {
			if (ad.getLocalName().equalsIgnoreCase(attribute.getFieldName())) return ad.getName();
		}
		return null;
	}
}
