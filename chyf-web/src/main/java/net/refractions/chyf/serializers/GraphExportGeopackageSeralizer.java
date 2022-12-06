package net.refractions.chyf.serializers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureWriter;
import org.geotools.data.Transaction;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.Entry.DataType;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

import net.refractions.chyf.ChyfWebApplication;
import net.refractions.chyf.model.GraphExport;

/**
 * 
 * REST api for exporting hydro features in a graph network format
 * 
 * @author Emily
 *
 */
@Component
public class GraphExportGeopackageSeralizer extends AbstractHttpMessageConverter<GraphExport>{
	
	public static String getNowAsString() {
		return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now());
	}	
		
	@Autowired
	private JdbcTemplate jdbcTemplate;

	public GraphExportGeopackageSeralizer() {
		super(ChyfWebApplication.GEOPKG_MEDIA_TYPE);
	}

	@Override
	protected boolean supports(Class<?> clazz) {
		return GraphExport.class.isAssignableFrom(clazz);
	}

	@Override
	protected GraphExport readInternal(Class<? extends GraphExport> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {
		return null;
	}

	@Override
	protected void writeInternal(GraphExport gExport, HttpOutputMessage outputMessage)
			throws IOException, HttpMessageNotWritableException {
	
		Path temp = Files.createTempFile("chyf-graph-export", ".gpkg");
	
		try {
			try(GeoPackage geopkg = new GeoPackage(temp.toFile())){
				CoordinateReferenceSystem crs = gExport.getBounds().getCoordinateReferenceSystem();
				
				//eflowpath
				try(final DefaultTransaction tx = new DefaultTransaction()){
					jdbcTemplate.query(gExport.getFlowpathQuery(), new FeatureResultTypeExtractor("eflowpath", geopkg, tx, crs, gExport.getBounds()) );
					tx.commit();
				}
				
				//nexus
				try(final DefaultTransaction tx = new DefaultTransaction()){
					jdbcTemplate.query(gExport.getNexusQuery(), new FeatureResultTypeExtractor("nexus", geopkg, tx, crs, gExport.getBounds()) );
					tx.commit();
				}
				
				//catchments
				try(final DefaultTransaction tx = new DefaultTransaction()){
					jdbcTemplate.query(gExport.getCatchmentQuery(), new FeatureResultTypeExtractor("ecatchment", geopkg, tx, crs, gExport.getBounds()) );
					tx.commit();
				}
	
				//metadata
				//metadata table 
				SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
				builder.add("geometry", Geometry.class, crs);
				builder.add("key", String.class);
				builder.add("value", String.class);
				builder.setName("Metadata");
				builder.setCRS(crs);
				SimpleFeatureType ftypemetadata = builder.buildFeatureType();
					
				FeatureEntry metadataentry = new FeatureEntry();
				metadataentry.setTableName("Metadata");
				metadataentry.setDataType(DataType.Feature);
				metadataentry.setBounds(gExport.getBounds());
				geopkg.create(metadataentry, ftypemetadata);
					
				try (DefaultTransaction tx = new DefaultTransaction()) {
	
					// populate metadata table
					try (FeatureWriter<SimpleFeatureType, SimpleFeature> mwriter = geopkg.writer(metadataentry, true, Filter.INCLUDE, tx)) {
						SimpleFeature feature = mwriter.next();
						//TODO:
	//					feature.setAttribute("key", FeatureListUtil.DATA_LICENSE_KEY);
	//					feature.setAttribute("value", CabdApplication.DATA_LICENCE_URL);
	//					mwriter.write();
	//
						feature = mwriter.next();
						feature.setAttribute("key",ChyfWebApplication.DOWNLOAD_DATETIME_KEY);
						feature.setAttribute("value", getNowAsString());
						mwriter.write();
						
						feature = mwriter.next();
						feature.setAttribute("key","Area of Interest");
						feature.setAttribute("value", gExport.getAoi());
						mwriter.write();
					}
					tx.commit();
				}
			}
			
			String filename = "chyfgraphexport-" + DateTimeFormatter.ofPattern("YYYYMMddHHmmss").format(LocalDateTime.now()) + ".gpkg";
			outputMessage.getHeaders().set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
			outputMessage.getHeaders().set(HttpHeaders.CONTENT_TYPE, ChyfWebApplication.GEOPKG_MEDIA_TYPE_STR);
			Files.copy(temp, outputMessage.getBody());
			outputMessage.getBody().flush();
		}finally {
			try {
				Files.delete(temp);
			}catch (Exception ex) {
				logger.warn("Unable to delete temporary file: " + temp.toString(), ex);
			}
		}
	
	}
	
		
	class FeatureResultTypeExtractor implements ResultSetExtractor<Boolean>{
		private String tablename;
		private GeoPackage geopkg;
		private Transaction tx;
		private CoordinateReferenceSystem crs;
		private ReferencedEnvelope env;
		
		public FeatureResultTypeExtractor(String tablename, GeoPackage geopkg, Transaction tx, CoordinateReferenceSystem crs, ReferencedEnvelope env) {
			this.tablename = tablename;
			this.geopkg = geopkg;
			this.tx = tx;
			this.crs = crs;
			this.env = env;
		}
		
		@Override
		public Boolean extractData(ResultSet rs) throws SQLException, DataAccessException {
			try {
				SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
						
				ResultSetMetaData md = rs.getMetaData();
				int geomcol = -1;
				Set<Integer> uuidCols = new HashSet<>();
				
				for (int i = 1; i <= md.getColumnCount(); i ++) {
					if (md.getColumnName(i).equals("geometry")) {
						geomcol = i;
						builder.add("geometry", Geometry.class, crs);
						builder.setDefaultGeometry("geometry");
					}else {
						if (md.getColumnClassName(i).equals(UUID.class.getName())){
							uuidCols.add(i);
							builder.add(md.getColumnName(i), String.class);
						}else {
							builder.add(md.getColumnName(i), Class.forName(md.getColumnClassName(i)));
						}
					}
				}
				builder.setName(this.tablename);
				builder.setCRS(this.crs);
				SimpleFeatureType eflowpathtype = builder.buildFeatureType();
	
				FeatureEntry entry = new FeatureEntry();
				entry.setTableName(this.tablename);
				entry.setDataType(DataType.Feature);
				entry.setGeometryColumn("geometry");
				entry.setBounds(this.env);
				geopkg.create(entry, eflowpathtype);

				//populate metadata table
				final WKBReader reader = new WKBReader();
				
				try(FeatureWriter<SimpleFeatureType, SimpleFeature> mwriter = geopkg.writer(entry, true, Filter.INCLUDE, tx)){
					while(rs.next()) {
						SimpleFeature feature = mwriter.next();
						
						for (int i = 1; i <= md.getColumnCount(); i ++) {
							if (i == geomcol) {
								byte[] data = rs.getBytes(i);
								if (data != null) {
									try {
										Geometry geom = reader.read(data);
										feature.setDefaultGeometry(geom);
									} catch (ParseException e) {
										throw new SQLException(e);
									}
								}
							}else {
								Object x = rs.getObject(i);
								if (uuidCols.contains(i) && x != null) {
									x = ((UUID)x).toString();
								}
								feature.setAttribute(md.getColumnName(i), x);
							}	
						}
						
						mwriter.write();
					}
					
				}
				return true;
			}catch (Exception ex) {
				logger.error(ex.getMessage(), ex);
				throw new SQLException(ex);
			}
		}
				
	}
	
		
}
