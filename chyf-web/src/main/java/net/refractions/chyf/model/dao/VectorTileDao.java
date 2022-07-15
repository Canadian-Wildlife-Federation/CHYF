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
			sb.append(" id, major_drainage_area, sub_drainage_area, sub_sub_drainage_area ");
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
				sb.append(" n.name_en as name_en,");
				sb.append(" n.name_fr as name_fr,");
				sb.append(" null as rank ");
				sb.append(" FROM ");
				sb.append(DataSourceTable.ECATCHMENT.tableName);
				sb.append(" t LEFT JOIN ");
				sb.append(DataSourceTable.NAMES.tableName);				
				sb.append(" n ON t.name_id = n.name_id ");
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
				sb.append(" n.name_en as name_en, ");
				sb.append(" n.name_fr as name_fr, ");
				sb.append(" t.rank as rank ");
				sb.append(" FROM ");
				sb.append(DataSourceTable.EFLOWPATH.tableName);
				sb.append(" t LEFT JOIN ");
				sb.append(DataSourceTable.NAMES.tableName);
				sb.append(" n ON t.name_id = n.name_id ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.EC_TYPE.tableName);
				sb.append(" et ON t.ef_type = et.code ");
				sb.append(" LEFT JOIN ");
				sb.append(DataSourceTable.EC_SUBTYPE.tableName);
				sb.append(" est ON t.ef_subtype = est.code ");
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
				sb.append(" n.name_en as name_en,");
				sb.append(" n.name_fr as name_fr,");
				sb.append(" null as rank ");
				sb.append(" FROM ");
				sb.append(DataSourceTable.ECATCHMENT.tableName);
				sb.append(" t LEFT JOIN ");
				sb.append(DataSourceTable.NAMES.tableName);				
				sb.append(" n ON t.name_id = n.name_id ");
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
