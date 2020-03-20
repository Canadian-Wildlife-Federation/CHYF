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
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.simple.SimpleFeatureReader;
import org.geotools.data.simple.SimpleFeatureWriter;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Polygon;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.geometry.BoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.util.ReprojectionUtils;

/**
 * Reads a geopackage input dataset.  
 * 
 * @author Emily
 *
 */
public class ChyfGeoPackageDataSource implements ChyfDataSource{

	static final Logger logger = LoggerFactory.getLogger(ChyfGeoPackageDataSource.class.getCanonicalName());
	
	public static FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();

	protected Path geopackageFile;
	protected GeoPackage geopkg;
	protected CoordinateReferenceSystem crs;
	
	public ChyfGeoPackageDataSource(Path geopackageFile) throws IOException {
		this.geopackageFile = geopackageFile;
		read();
	}
	
	/**
	 * @return the coordinate reference system associated with the geopackage layers
	 */
	public CoordinateReferenceSystem getCoordinateReferenceSystem() {
		return crs;
	}
	
	protected void read() throws IOException {
		geopkg = new GeoPackage(geopackageFile.toFile());
		geopkg.init();
		
		for (Layer l : Layer.values()) {
			SimpleFeatureType ft = getFeatureType(l);
			if (ft == null) continue;
			if (crs == null) {
				crs = ft.getCoordinateReferenceSystem();
			}else if (!CRS.equalsIgnoreMetadata(crs,  ft.getCoordinateReferenceSystem())) {
				throw new RuntimeException("All layers must have the same projection.");
			}
		}
	}
	
	/**
	 * 
	 * @param layer
	 * @return the feature type associated with a given layer
	 * @throws IOException
	 */
	public SimpleFeatureType getFeatureType(Layer layer) throws IOException{
		return getFeatureType(getEntry(layer));
	}

    /**
     * 
     * @param layer
     * @return the feature type associated with a given feature entry
     * @throws IOException
     */
	protected SimpleFeatureType getFeatureType(FeatureEntry layer) throws IOException{
		if (layer == null) return null;
		
		try(SimpleFeatureReader reader = geopkg.reader(layer, Filter.EXCLUDE, null)){
			return reader.getFeatureType();
		}
	}
	
	/**
	 * @return a feature reader for the given layer
	 */
    protected SimpleFeatureReader getFeatureReader(Layer layer, Filter filter, Transaction tx) throws IOException {
    	FeatureEntry fe = getEntry(layer);
    	if (fe == null) return null;
    	return geopkg.reader(fe, filter, tx);
    }
   
	
    /**
     * 
     * @return all catchments of water type
     * 
     * @throws IOException
     */
    public SimpleFeatureReader getWaterbodies() throws IOException{
		return getFeatureReader(Layer.ECATCHMENTS, getWbTypeFilter(), null);
	}
    
    /**
     * The feature entry associated with the given layer
     * @param layer
     * @return
     * @throws IOException
     */
	protected FeatureEntry getEntry(Layer layer) throws IOException {
		return geopkg.feature(layer.getLayerName());
	}

	public int getSrid(Layer layer) throws IOException {
		return geopkg.feature(layer.getLayerName()).getSrid();
	}

	/**
	 * Ensures that the eflowpath, ecatchments, and ecoastline layers
	 * all have a internal_uuid attribute and that is is unique and populated
	 * for each feature.  If it doesn't exist a new field is added.  Any empty values
	 * are populated.  If not unique then an exception is thrown
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws Exception 
	 */
	public void addInternalIdAttribute() throws IOException {
		
		//layers which need internal_ids
		Layer[] layers = new Layer[] {Layer.SHORELINES, Layer.EFLOWPATHS, Layer.ECATCHMENTS};
		
		List<FeatureEntry> featureEntries = new ArrayList<>();
		for (Layer l : layers) {
			FeatureEntry fe = getEntry(l);
			if (fe != null) featureEntries.add(fe);
		}
		
		//determine which layers need internal ids added
		List<FeatureEntry> addId = new ArrayList<>();
		for (FeatureEntry fe : featureEntries) {
			Name name = ChyfDataSource.findAttribute(getFeatureType(fe), ChyfAttribute.INTERNAL_ID);
			if (name == null) addId.add(fe);
		}
		
		try {
			//add the id
			Connection c = geopkg.getDataSource().getConnection();
			for (FeatureEntry layer : addId) {
				String query = "ALTER TABLE " + layer.getTableName() + " add column " + ChyfAttribute.INTERNAL_ID.getFieldName()  + " text ";
				c.createStatement().execute(query);
			}
			
			//ensure unique
			for (FeatureEntry layer : featureEntries) {
				
				StringBuilder sb = new StringBuilder();
				sb.append("SELECT count(*), ");
				sb.append(ChyfAttribute.INTERNAL_ID.getFieldName());
				sb.append(" FROM ");
				sb.append(layer.getTableName());
				sb.append(" WHERE ");
				sb.append(ChyfAttribute.INTERNAL_ID.getFieldName());
				sb.append(" is not null ");
				sb.append(" GROUP BY ");
				sb.append(ChyfAttribute.INTERNAL_ID.getFieldName());
				sb.append(" HAVING count(*) > 1");
				
				try(ResultSet rs = c.createStatement().executeQuery(sb.toString())){
					if (rs.next()) throw new RuntimeException("Provided internal ids are not unique.");
				}
			}
		} catch (SQLException sqle) {
			throw new IOException(sqle);
		}
		
		geopkg.close();
		read();
		
		//populate internal_id with uuids
		for (FeatureEntry layer : featureEntries) {
			try(DefaultTransaction tx = new DefaultTransaction()) {
				try {
					Filter blankFilter = ff.isNull(ff.property(ChyfAttribute.INTERNAL_ID.getFieldName()));
		
					try(SimpleFeatureWriter writer = geopkg.writer(layer, false, blankFilter, tx)){
						Name name = ChyfDataSource.findAttribute(writer.getFeatureType(), ChyfAttribute.INTERNAL_ID);
						
						while(writer.hasNext()) {
							SimpleFeature sf = writer.next();
							sf.setAttribute(name, UUID.randomUUID().toString());
							writer.write();
						}
					}
					tx.commit();
				} catch (IOException ioe) {
					tx.rollback();
					throw ioe;
				}
			}
		}
		
	}

	@Override
	public SimpleFeatureReader query(Layer layer) throws IOException {
		return query(layer, null, null);
	}

	@Override
	public SimpleFeatureReader query(Layer layer, ReferencedEnvelope bounds) throws IOException {
		return query(layer, bounds, null);
	}

	@Override
	public SimpleFeatureReader query(Layer layer, Filter filter) throws IOException {
		return query(layer, null, filter);
	}

	@Override
	public SimpleFeatureReader query(Layer layer, ReferencedEnvelope bounds, Filter filter) throws IOException {
		if (layer == null) throw new IOException("Required dataset not found.");
		FeatureEntry fe = getEntry(layer);
		return query(fe, bounds, filter);
	}
	
	// Internal query interface allows subclasses to specify the featureEntry instead of using the fixed Layer Enum
	protected SimpleFeatureReader query(FeatureEntry fe, ReferencedEnvelope bounds, Filter filter) throws IOException {
		if(fe == null) return null;
		
		Filter netFilter = null;
		if(bounds != null) {
			netFilter = filterFromEnvelope(bounds, fe);
		}
		if(filter != null) {
			netFilter = ff.and(netFilter, filter);
		}
    	return geopkg.reader(fe, netFilter, null);
	}
	
	protected Filter filterFromEnvelope(ReferencedEnvelope env, FeatureEntry source) {
		BoundingBox bounds = ReprojectionUtils.reproject(env, ReprojectionUtils.srsCodeToCRS(source.getSrid()));
		String geom = source.getGeometryColumn();
		Filter filter1 = ff.bbox(ff.property(geom), bounds);
		Filter filter2 = ff.intersects(ff.property(geom), ff.literal(JTS.toGeometry(bounds)));
		Filter filter = ff.and(filter1, filter2);
		return filter;
	}

	/**
	 * Gets aoi layer as a list of polygons
	 * @return
	 * @throws IOException 
	 * @throws NoSuchElementException 
	 * @throws IllegalArgumentException 
	 * @throws Exception
	 */
	public List<Polygon> getAoi() throws IOException  {
		List<Polygon> aois = new ArrayList<>();
		try(SimpleFeatureReader reader = getFeatureReader(Layer.AOI, Filter.INCLUDE, null)){
			while(reader.hasNext()) {
				SimpleFeature fs = reader.next();
				Polygon p = ChyfDataSource.getPolygon(fs);
				aois.add(p);
			}
		}
		return aois;
	}
	
	@Override
	public void close() {
		geopkg.close();
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
