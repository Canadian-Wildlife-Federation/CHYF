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
package net.refractions.chyf.model;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.locationtech.jts.geom.Envelope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import net.refractions.chyf.controller.VectorTileController;

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
		int srid = VectorTileController.SRID;

		String stenv = "ST_MakeEnvelope(" + env.getMinX() + "," + env.getMinY() + "," + env.getMaxX() + ","
				+ env.getMaxY() + "," + srid + ")";

		StringBuilder sb = new StringBuilder();
		sb.append("	WITH");
		sb.append("	bounds AS (");
		sb.append("	SELECT st_transform(" + stenv + ", " + VectorTileController.SRID + ") AS geom, ");
		sb.append(stenv + "::box2d AS b2d  ");
		sb.append(" ), ");
		sb.append("	mvtgeom AS (");

		if (layer.isWorkUnit()) {
			// not combinable with other layers
			sb.append("	SELECT ST_AsMVTGeom(ST_Transform(t.geometry, " + srid + "), bounds.b2d) AS geom, ");
			sb.append(" id, major_drainage_area, sub_drainage_area, sub_sub_drainage_area ");
			sb.append("	FROM ");
			sb.append(DataSourceTable.WORK_UNIT.tableName);
			sb.append(" t, bounds ");
			sb.append("	WHERE st_intersects(st_transform(t.geometry, " + srid + "),  bounds.geom) ");
		}else if (layer.isShoreline()) {
			sb.append("	SELECT ST_AsMVTGeom(ST_Transform(t.geometry, " + srid + "), bounds.b2d) AS geom ");
			sb.append("	FROM ");
			sb.append(DataSourceTable.SHORELINE.tableName);
			sb.append(" t, bounds ");
			sb.append("	WHERE st_intersects(st_transform(t.geometry, " + srid + "),  bounds.geom) ");
		} else {
			boolean union = false;
			if (layer.isWaterbody()) {
				union = true;
				// waterbodies
				sb.append("	SELECT ST_AsMVTGeom(ST_Transform(t.geometry, " + srid + "), bounds.b2d) AS geom, ");
				sb.append(" 'waterbody' as feature_type, ");
				sb.append(" case when t.ec_type = 1 then 'Reach' ");
				sb.append(" when t.ec_type = 2 then 'Bank' ");
				sb.append(" when t.ec_type = 3 then 'Empty' ");
				sb.append(" when t.ec_type = 4 then 'Water' ");
				sb.append(" when t.ec_type = 5 then 'Built-up Area' ");
				sb.append(" else 'Unknown' end as type_name,");
				sb.append(" t.ec_type as type, ");
				sb.append(" case when t.ec_subtype = 10 then 'Lake' ");
				sb.append(" when t.ec_subtype = 11 then 'Great Lake' ");
				sb.append(" when t.ec_subtype = 12 then 'Wastewater Pond' ");
				sb.append(" when t.ec_subtype = 20 then 'Estuary' ");
				sb.append(" when t.ec_subtype = 30 then 'Nearshore Zone' ");
				sb.append(" when t.ec_subtype = 40 then 'River' ");
				sb.append(" when t.ec_subtype = 41 then 'Canal' ");
				sb.append(" when t.ec_subtype = 50 then 'Buried Infrastructure' ");
				sb.append(" when t.ec_subtype = 90 then 'Ocean' ");
				sb.append(" when t.ec_subtype = 99 then 'Unknown' ");
				sb.append(" else 'Unknown' end as subtype_name,");
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
				sb.append(", bounds ");
				sb.append("	WHERE t.ec_type IN (4) ");
				sb.append(" AND st_intersects(st_transform(t.geometry, " + srid + "),  bounds.geom) ");
			}

			if (layer.isFlowpath()) {
				if (union)
					sb.append(" UNION ");
				union = true;
				// flowpath
				sb.append("	SELECT ST_AsMVTGeom(ST_Transform(t.geometry, " + srid + "), bounds.b2d) AS geom, ");
				sb.append(" 'flowpath' as feature_type, ");
				sb.append(" CASE WHEN t.ef_type = 1 then 'Reach' ");
				sb.append(" WHEN t.ef_type = 2 then 'Bank' ");
				sb.append(" WHEN t.ef_type = 3 then 'Skeleton' ");
				sb.append(" WHEN t.ef_type = 4 then 'Infrasturcture' ");
				sb.append(" ELSE 'Unknown' END as type_name, ");
				sb.append(" t.ef_type as type, ");
				sb.append(" CASE WHEN t.ef_subtype = 10 then 'Observed' ");
				sb.append(" WHEN t.ef_subtype = 20 then 'Inferred' ");
				sb.append(" WHEN t.ef_subtype = 99 then 'Unspecified' ");
				sb.append(" ELSE 'Unknown' END as subtype_name, ");
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
				sb.append(", bounds ");
				sb.append("	WHERE st_intersects(st_transform(t.geometry, " + srid + "),  bounds.geom) ");
			}

			// elementary catchments
			if (layer.isCatchment()) {
				if (union)
					sb.append(" UNION ");
				union = true;
				sb.append("	SELECT ST_AsMVTGeom(ST_Transform(t.geometry, " + srid + "), bounds.b2d) AS geom, ");
				sb.append(" 'waterbody' as feature_type, ");
				sb.append(" case when t.ec_type = 1 then 'Reach' ");
				sb.append(" when t.ec_type = 2 then 'Bank' ");
				sb.append(" when t.ec_type = 3 then 'Empty' ");
				sb.append(" when t.ec_type = 4 then 'Water' ");
				sb.append(" when t.ec_type = 5 then 'Built-up Area' ");
				sb.append(" else 'Unknown' end as type_name,");
				sb.append(" t.ec_type as type, ");
				sb.append(" case when t.ec_subtype = 10 then 'Lake' ");
				sb.append(" when t.ec_subtype = 11 then 'Great Lake' ");
				sb.append(" when t.ec_subtype = 12 then 'Wastewater Pond' ");
				sb.append(" when t.ec_subtype = 20 then 'Estuary' ");
				sb.append(" when t.ec_subtype = 30 then 'Nearshore Zone' ");
				sb.append(" when t.ec_subtype = 40 then 'River' ");
				sb.append(" when t.ec_subtype = 41 then 'Canal' ");
				sb.append(" when t.ec_subtype = 50 then 'Buried Infrastructure' ");
				sb.append(" when t.ec_subtype = 90 then 'Ocean' ");
				sb.append(" when t.ec_subtype = 99 then 'Unknown' ");
				sb.append(" else 'Unknown' end as subtype_name,");
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
				sb.append(", bounds ");
				sb.append("	WHERE t.ec_type in (1, 2, 3, 5) ");
				sb.append(" AND st_intersects(st_transform(t.geometry, " + srid + "),  bounds.geom) ");
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
