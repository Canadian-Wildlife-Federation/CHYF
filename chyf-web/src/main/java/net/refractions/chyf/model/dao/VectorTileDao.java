/*
 * Copyright 2021 Canadian Wildlife Federation.
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
package net.refractions.chyf.model.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.locationtech.jts.geom.Envelope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import net.refractions.chyf.ChyfWebApplication;
import net.refractions.chyf.controller.VectorTileController;
import net.refractions.chyf.model.DataSourceTable;
import net.refractions.chyf.model.ECatchment;
import net.refractions.chyf.model.HydroFeature;
import net.refractions.chyf.model.VectorTileLayer;

/**
 * Data access interface for vector tiles
 * 
 * @author Emily
 *
 */
public class VectorTileDao {

	private JdbcTemplate jdbcTemplate;

	public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public byte[] getTile(int z, int x, int y, VectorTileLayer layer) {

		int numtiles = (int) Math.pow(2, z);

		double xtilesize = (VectorTileController.BOUNDS.getMaxX() - VectorTileController.BOUNDS.getMinX()) / numtiles;
		double tilexmin = xtilesize * x + VectorTileController.BOUNDS.getMinX();

		double ytilesize = (VectorTileController.BOUNDS.getMaxY() - VectorTileController.BOUNDS.getMinY()) / numtiles;
		double tileymax = VectorTileController.BOUNDS.getMaxY() - ytilesize * y;

		Envelope env = new Envelope(tilexmin, tilexmin + xtilesize, tileymax - ytilesize, tileymax);
		int srid = VectorTileController.TILE_SRID;

		String stenv = "ST_MakeEnvelope(" + env.getMinX() + "," + env.getMinY() + "," + env.getMaxX() + ","
				+ env.getMaxY() + "," + srid + ")";

		StringBuilder sb = new StringBuilder();
		sb.append("	WITH");
		sb.append("	bounds AS (");
		sb.append("	SELECT st_transform(" + stenv + ", " + DataSourceTable.DATA_SRID + ") AS geom, ");
		sb.append(stenv + "::box2d AS b2d  ");
		sb.append(" ), ");
		
		if (layer.isWaterbody()) {
			//compute catchment order
			sb.append("catchment_order AS (");
			sb.append("SELECT b.id, max(a.strahler_order) as strahler_order ");
			sb.append("FROM ");
			sb.append(DataSourceTable.ECATCHMENT.tableName);
			sb.append(" b LEFT JOIN ");
			sb.append(DataSourceTable.EFLOWPATH.tableName);
			sb.append(" c on c.ecatchment_id = b.id LEFT JOIN ");
			sb.append(DataSourceTable.EFLOWPATH_PROPERTIES.tableName); 
			sb.append(" a on a.id = c.id, bounds");
			sb.append("	WHERE b.ec_type IN ( " + ECatchment.EcType.WATER.code + ") ");
			sb.append(" AND st_intersects(b.geometry,  bounds.geom) ");
			sb.append("GROUP BY b.id");
			sb.append("), ");				
		}
		
		sb.append("	mvtgeom AS (");

		if (layer.isWorkUnit()) {
			
			//determine if we need to simplify the geometries first; 
			//this substantially improves performance
			//a better option may be to use the postgis wagyu library
			double simplify = -1;
			if (z == 0 || z == 1 || z == 2 || z == 3 ) {
				simplify = 0.05;
			}else if ( z == 4 || z == 5 || z == 6 || z == 7) {
				simplify = 0.01;
			}else if ( z == 8 || z == 9) {
				simplify = 0.001;
			}
			
			sb.append("	SELECT ST_AsMVTGeom(ST_Transform(");
			if (simplify > 0) {
				sb.append("st_simplify(t.geometry, " + simplify + ")");
			}else {
				sb.append("t.geometry");
			}
			sb.append(" , " + srid );
			sb.append("), bounds.b2d) AS geom, ");
			sb.append(" id, ");
			if (ChyfWebApplication.isFrench()) {
				sb.append("major_drainage_area_fr, sub_drainage_area_fr, sub_sub_drainage_area_fr ");
			}else {
				sb.append("major_drainage_area_en, sub_drainage_area_en, sub_sub_drainage_area_en ");
			}
			sb.append("	FROM ");
			sb.append(DataSourceTable.WORK_UNIT.tableName);
			sb.append(" t, bounds ");
			sb.append("	WHERE st_intersects(t.geometry,  bounds.geom) ");
		}else if (layer.isShoreline()) {
			sb.append("	SELECT ST_AsMVTGeom(ST_Transform(t.geometry, " + srid + "), bounds.b2d) AS geom ");
			sb.append("	FROM ");
			sb.append(DataSourceTable.SHORELINE.tableName);
			sb.append(" t, bounds ");
			sb.append("	WHERE st_intersects(t.geometry,  bounds.geom) ");
		} else {
			boolean union = false;
			if (layer.isWaterbody()) {
				union = true;
				// waterbodies
				sb.append("	SELECT ST_AsMVTGeom(ST_Transform(t.geometry, " + srid + "), bounds.b2d) AS geom, ");
				sb.append(" '" + HydroFeature.Type.WATERBODY.typeName + "' as " + HydroFeature.TYPE_FIELD_NAME + ", ");
				sb.append(" et.name as feature_type_name, ");
				sb.append(" t.ec_type as feature_type, ");
				sb.append(" est.name as subtype_name, ");
				sb.append(" t.ec_subtype as subtype, ");
				sb.append(" t.area as size, ");
				sb.append(" t.is_reservoir as is_reservoir, ");
				sb.append(" n1.name_en as rivername1_en,");
				sb.append(" n1.name_fr as rivername1_fr,");
				sb.append(" n2.name_en as rivername2_en,");
				sb.append(" n2.name_fr as rivername2_fr,");
				sb.append(" n3.name_en as lakename1_en,");
				sb.append(" n3.name_fr as lakename1_fr,");
				sb.append(" n4.name_en as lakename2_en,");
				sb.append(" n4.name_fr as lakename2_fr,");
				sb.append(" null as rank, ");
				sb.append(" co.strahler_order as strahler_order ");
				sb.append(" FROM ");
				sb.append(DataSourceTable.ECATCHMENT.tableName);
				sb.append(" t LEFT JOIN ");
				sb.append(" catchment_order co on co.id = t.id ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.NAMES.tableName);				
				sb.append(" n1 ON t.rivernameid1 = n1.name_id ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.NAMES.tableName);				
				sb.append(" n2 ON t.rivernameid2 = n2.name_id ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.NAMES.tableName);				
				sb.append(" n3 ON t.lakenameid1 = n3.name_id ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.NAMES.tableName);				
				sb.append(" n4 ON t.lakenameid2 = n4.name_id ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.EC_TYPE.tableName);
				sb.append(" et ON t.ec_type = et.code ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.EC_SUBTYPE.tableName);
				sb.append(" est ON t.ec_subtype = est.code ");
				sb.append(", bounds ");
				sb.append("	WHERE t.ec_type IN ( " + ECatchment.EcType.WATER.code + ") ");
				sb.append(" AND st_intersects(t.geometry,  bounds.geom) ");
			}

			if (layer.isFlowpath()) {
				if (union)
					sb.append(" UNION ");
				union = true;
				// flowpath
				sb.append("	SELECT ST_AsMVTGeom(ST_Transform(t.geometry, " + srid + "), bounds.b2d) AS geom, ");
				sb.append(" '" + HydroFeature.Type.FLOWPATH.typeName + "' as " + HydroFeature.TYPE_FIELD_NAME + ", ");
				sb.append(" et.name as feature_type_name, ");
				sb.append(" t.ef_type as feature_type, ");
				sb.append(" est.name as subtype_name, ");
				sb.append(" t.ef_subtype as subtype, ");
				sb.append(" t.length as size, ");
				sb.append(" null as is_reservoir, ");
				sb.append(" n1.name_en as rivername1_en, ");
				sb.append(" n1.name_fr as rivername1_fr, ");
				sb.append(" n2.name_en as rivername1_en, ");
				sb.append(" n2.name_fr as rivername1_fr, ");
				sb.append(" null as lakename1_en, ");
				sb.append(" null as lakename1_fr, ");
				sb.append(" null as lakename2_en, ");
				sb.append(" null as lakename2_fr, ");
				sb.append(" t.rank as rank, ");
				sb.append(" ea.strahler_order as strahler_order ");
				sb.append(" FROM ");
				sb.append(DataSourceTable.EFLOWPATH.tableName);
				sb.append(" t LEFT JOIN ");
				sb.append(DataSourceTable.NAMES.tableName);
				sb.append(" n1 ON t.rivernameid1 = n1.name_id ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.NAMES.tableName);
				sb.append(" n2 ON t.rivernameid2 = n2.name_id ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.EF_TYPE.tableName);
				sb.append(" et ON t.ef_type = et.code ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.EF_SUBTYPE.tableName);
				sb.append(" est ON t.ef_subtype = est.code ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.EFLOWPATH_PROPERTIES.tableName);
				sb.append(" ea ON ea.id = t.id ");
				sb.append(", bounds ");
				sb.append("	WHERE st_intersects(t.geometry,  bounds.geom) ");
			}

			// elementary catchments
			if (layer.isCatchment()) {
				if (union)
					sb.append(" UNION ");
				union = true;
				
				sb.append("	SELECT ST_AsMVTGeom(ST_Transform(t.geometry, " + srid + "), bounds.b2d) AS geom, ");
				sb.append(" '" + HydroFeature.Type.CATCHMENT.typeName + "' as " + HydroFeature.TYPE_FIELD_NAME + ", ");
				sb.append(" et.name as feature_type_name, ");
				sb.append(" t.ec_type as feature_type, ");
				sb.append(" est.name as subtype_name, ");
				sb.append(" t.ec_subtype as subtype, ");
				sb.append(" t.area as size, ");
				sb.append(" t.is_reservoir as is_reservoir, ");
				sb.append(" n1.name_en as rivername1_en,");
				sb.append(" n1.name_fr as rivername1_fr,");
				sb.append(" n2.name_en as rivername2_en,");
				sb.append(" n2.name_fr as rivername2_fr,");
				sb.append(" n3.name_en as lakename1_en,");
				sb.append(" n3.name_fr as lakename1_fr,");
				sb.append(" n4.name_en as lakename2_en,");
				sb.append(" n4.name_fr as lakename2_fr,");
				sb.append(" null as rank, ");
				sb.append(" null as strahler_order ");
				sb.append(" FROM ");
				sb.append(DataSourceTable.ECATCHMENT.tableName);
				sb.append(" t LEFT JOIN ");
				sb.append(DataSourceTable.NAMES.tableName);				
				sb.append(" n1 ON t.rivernameid1 = n1.name_id ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.NAMES.tableName);				
				sb.append(" n2 ON t.rivernameid2 = n2.name_id ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.NAMES.tableName);				
				sb.append(" n3 ON t.lakenameid1 = n3.name_id ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.NAMES.tableName);				
				sb.append(" n4 ON t.lakenameid2 = n4.name_id ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.EC_TYPE.tableName);
				sb.append(" et ON t.ec_type = et.code ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.EC_SUBTYPE.tableName);
				sb.append(" est ON t.ec_subtype = est.code ");
				sb.append(", bounds ");
				sb.append("	WHERE t.ec_type not in (" + ECatchment.EcType.WATER.code + ") ");
				sb.append(" AND st_intersects(t.geometry,  bounds.geom) ");
			}
		}

		sb.append(")");
		sb.append("	SELECT ST_AsMVT(mvtgeom.*) as tile FROM mvtgeom");

		List<byte[]> data = jdbcTemplate.query(sb.toString(), new RowMapper<byte[]>() {
			@Override
			public byte[] mapRow(ResultSet rs, int rowNum) throws SQLException {
				return rs.getBytes("tile");
			}
		});
		if (data.size() > 0)
			return data.get(0);
		return null;
	}

}
