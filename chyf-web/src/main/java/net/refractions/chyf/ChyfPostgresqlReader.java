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
package net.refractions.chyf;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.precision.GeometryPrecisionReducer;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import net.refractions.chyf.enumTypes.CatchmentType;
import net.refractions.chyf.enumTypes.FlowpathType;
import net.refractions.chyf.enumTypes.Rank;
import net.refractions.chyf.hygraph.ECatchment;
import net.refractions.chyf.hygraph.HyGraphBuilder;
import net.refractions.chyf.rest.GeotoolsGeometryReprojector;
import net.refractions.chyf.rest.controllers.VectorTileController;
import net.refractions.util.UuidUtil;
import nrcan.cccmeo.chyf.db.Boundary;
import nrcan.cccmeo.chyf.db.BoundaryDAO;
import nrcan.cccmeo.chyf.db.Catchment;
import nrcan.cccmeo.chyf.db.CatchmentDAO;
import nrcan.cccmeo.chyf.db.Flowpath;
import nrcan.cccmeo.chyf.db.FlowpathDAO;
import nrcan.cccmeo.chyf.db.SpringJdbcConfiguration;
import nrcan.cccmeo.chyf.db.Waterbody;
import nrcan.cccmeo.chyf.db.WaterbodyDAO;

/**
 * Reads source data from database.
 * 
 * @author Emily
 * @author Mark Lague
 *
 */
public class ChyfPostgresqlReader extends ChyfDataReader{

	public static int SRID = 4326;
	public static WKBReader READER = new WKBReader(new GeometryFactory(new PrecisionModel(), SRID));
	
	
	protected List<Geometry> boundaries = new ArrayList<>();
	
	private AnnotationConfigApplicationContext context;
	
	public ChyfPostgresqlReader() {
		context = new AnnotationConfigApplicationContext(SpringJdbcConfiguration.class);

	}
	
	public List<Geometry> getBoundaries(){
		return this.boundaries;
	}
	
	public void read(HyGraphBuilder gb ) throws Exception{
		
		CoordinateReferenceSystem dbCRS = GeotoolsGeometryReprojector.srsCodeToCRS(SRID);

		// read and add Waterbodies
		logger.info("Reading postgresql waterbodies");
		WaterbodyDAO waterbodyDAO = (WaterbodyDAO) context.getBean(WaterbodyDAO.class);

		for (Waterbody wb : waterbodyDAO.getWaterbodies()) {
			Polygon wCatchment = wb.getPolygon();

			wCatchment = (Polygon) GeotoolsGeometryReprojector.reproject(wCatchment, dbCRS, ChyfDatastore.BASE_CRS);
			Object def = wb.getDefinition();
			Integer intValue = -1;
			if (def instanceof Integer) {
				intValue = (Integer) def;
			} else if (def instanceof Long) {
				intValue = ((Long) def).intValue();
			}
			CatchmentType type = findType(intValue);
			double area = wCatchment.getArea();

			wCatchment = (Polygon) GeometryPrecisionReducer.reduce(wCatchment, ChyfDatastore.PRECISION_MODEL);
			gb.addECatchment(type, area, wCatchment);
		}

		// read and add Catchments
		logger.info("Reading postgresql catchments");
		CatchmentDAO catchmentDAO = (CatchmentDAO) context.getBean(CatchmentDAO.class);

		boolean[] hasAttribute = new boolean[ECatchment.ECatchmentStat.values().length];
		for (int i = 0; i < hasAttribute.length; i++)
			hasAttribute[i] = false;

		List<Catchment> catchments = catchmentDAO.getCatchments();
		for (Catchment c : catchments) {
			Polygon cCatchment = c.getCatchment();
			double area = cCatchment.getArea();

			cCatchment = GeotoolsGeometryReprojector.reproject(cCatchment, dbCRS, ChyfDatastore.BASE_CRS);
			cCatchment = (Polygon) GeometryPrecisionReducer.reduce(cCatchment, ChyfDatastore.PRECISION_MODEL);

			ECatchment newCatchment = gb.addECatchment(CatchmentType.UNKNOWN, area, cCatchment);
			if (newCatchment != null) {
				// statistic attributes if applicable
				for (int i = 0; i < hasAttribute.length; i++) {
					ECatchment.ECatchmentStat stat = ECatchment.ECatchmentStat.values()[i];
					Double x = (Double) c.getAttribute(stat.getFieldName().toLowerCase(), c);
					if (x != null && !(Double.isNaN(x))) {
						stat.updateCatchment(newCatchment, x);
					}
				}
			}
		}

		// read and add Flowpaths
		logger.info("Reading postgresql flowpaths");
		FlowpathDAO flow = (FlowpathDAO) context.getBean(FlowpathDAO.class);

		List<Flowpath> flowpaths = flow.getFlowpaths();
		for (Flowpath fp : flowpaths) {
			LineString flowP = fp.getLinestring();
			flowP = GeotoolsGeometryReprojector.reproject(flowP, dbCRS, ChyfDatastore.BASE_CRS);
			FlowpathType type = FlowpathType.convert(fp.getType());
			String rankString = fp.getRank();
			Rank rank = Rank.convert(rankString);
			String name = fp.getName();
			double length = fp.getLength();
			UUID nameId = null;
			if (fp.getNameId() != null) {
				try {
					nameId = UuidUtil.UuidFromString(fp.getNameId());
				} catch (IllegalArgumentException iae) {
					logger.warn("Exception reading UUID: " + iae.getMessage());
				}
			}
			flowP = (LineString) GeometryPrecisionReducer.reduce(flowP, ChyfDatastore.PRECISION_MODEL);
			gb.addEFlowpath(type, rank, name, nameId, length, (LineString) flowP);

		}

		// read and add Boundaries
		logger.info("Reading postgresql boundary");
		BoundaryDAO bound = (BoundaryDAO) context.getBean(BoundaryDAO.class);

		List<Boundary> bounds = bound.getBoundary();
		for (Boundary b : bounds) {
			Polygon boundary = b.getPolygon();
			boundary = GeotoolsGeometryReprojector.reproject(boundary, dbCRS, ChyfDatastore.BASE_CRS);
			boundary = (Polygon) GeometryPrecisionReducer.reduce(boundary, ChyfDatastore.PRECISION_MODEL);
			boundaries.add(boundary);
		}
	}	
	
	/**
	 * Return the features in the specified envelope as 
	 * a mapbox vector tile.
	 * @param x the x tile
	 * @param y the y tile
	 * @param z the zoom level
	 * @param flowpath if flowpaths are to be included in output
	 * @param waterbody if waterbodies are to be included in output
	 * @param catchment if catchments are to be included in output
	 * @return
	 */
	public byte[] getVectorTile(int z, int x, int y,
			boolean flowpath, boolean waterbody, boolean catchment) {
		
		if (!flowpath && !waterbody && !catchment) return null;
		
		//TODO: update to use st_tileenvelope in postgis 3
		int numtiles = (int) Math.pow(2, z);

		double xtilesize = (VectorTileController.BOUNDS.getMaxX() - VectorTileController.BOUNDS.getMinX()) / numtiles;
		double tilexmin = xtilesize * x + VectorTileController.BOUNDS.getMinX();
		
		double ytilesize = (VectorTileController.BOUNDS.getMaxY() - VectorTileController.BOUNDS.getMinY()) / numtiles;
		double tileymax = VectorTileController.BOUNDS.getMaxY() - ytilesize * y;
		
		Envelope env = new Envelope(tilexmin, tilexmin + xtilesize, tileymax - ytilesize, tileymax );
		int srid = VectorTileController.SRID;
		
		String stenv = "ST_MakeEnvelope(" + env.getMinX() + "," + env.getMinY() + "," + env.getMaxX() + "," + env.getMaxY() + "," + srid + ")";
		
		StringBuilder sb = new StringBuilder();
		sb.append("	WITH");
		sb.append("	bounds AS (");
		sb.append("	SELECT st_transform(" + stenv + ", " + SRID + ") AS geom, ");
		sb.append( stenv + "::box2d AS b2d  ");
		sb.append(" ), ");
		sb.append("	mvtgeom AS (");
		
		if (waterbody) {
			//waterbodies
			sb.append("	SELECT ST_AsMVTGeom(ST_Transform(t.geometry, " + srid + "), bounds.b2d) AS geom, ");
			sb.append(" 'waterbody' as type, ");
			sb.append(" area as size, ");
			sb.append(" case when definition = 4 then 'Lake' when definition = 9 then 'Pond' when definition = 6 then 'River' when definition = 1 then 'Canel' else 'Unknown' end as sub_type,");
			sb.append(" null as name,");
			sb.append(" null as rank");
			sb.append("	FROM chyf.waterbody t, bounds ");
			sb.append("	WHERE st_intersects(t.geometry,  bounds.geom) ");
		}
		
		if (flowpath) {
			if (waterbody) sb.append(" UNION ");
			//flowpath
			sb.append("	SELECT ST_AsMVTGeom(ST_Transform(t.geometry, " + srid + "), bounds.b2d) AS geom, ");
			sb.append(" 'flowpath' as type, ");
			sb.append(" length as size, ");
			sb.append(" type as sub_type,");
			sb.append(" name as name,");
			sb.append(" rank as rank");
			sb.append("	FROM chyf.flowpath t, bounds ");
			sb.append("	WHERE st_intersects(t.geometry,  bounds.geom) ");
		}
		
		//elementary catchments
		if (catchment) {
			if (waterbody || flowpath) sb.append(" UNION ");
			sb.append("	SELECT ST_AsMVTGeom(ST_Transform(t.geometry, " + srid + "), bounds.b2d) AS geom, ");
			sb.append(" 'elementary_catchment' as type, ");
			sb.append(" area as size, ");
			sb.append(" null as sub_type,");
			sb.append(" null as name,");
			sb.append(" null as rank");
			sb.append("	FROM chyf.elementary_catchment t, bounds ");
			sb.append("	WHERE st_intersects(t.geometry,  bounds.geom) ");
		}
			
		sb.append(")");
		sb.append("	SELECT ST_AsMVT(mvtgeom.*) FROM mvtgeom");
		
		try(AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(SpringJdbcConfiguration.class)){
			SpringJdbcConfiguration config = context.getBean(SpringJdbcConfiguration.class);
			byte[] tile = config.jdbcTemplate().query(sb.toString(), new ResultSetExtractor<byte[]>() {
	
				@Override
				public byte[] extractData(ResultSet rs) throws SQLException, DataAccessException {
					if (!rs.next()) return null;
					return rs.getBytes(1);
				}});
		
			return tile;
		}
	}
}
