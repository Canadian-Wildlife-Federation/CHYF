/*
 * Copyright 2022 Canadian Wildlife Federation
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
import java.util.UUID;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import net.refractions.chyf.model.DataSourceTable;
import net.refractions.chyf.model.Shoreline;

/**
 * DAO for querying shoreline objects
 * @author Emily
 *
 */
@Component
public class ShorelineDao {

    
	@Autowired
	private JdbcTemplate jdbcTemplate;

	public static enum Field{
		ID ("id", "id"),
		AOI_ID("aoi_id", "aoi_id"),
		
		GEOMETRY("geometry", "geometry"),
		AOI_NAME(null, "aoi_name");
		
		public String columnname;
		public String jsonname;
		
		Field(String columnname, String jsonname){
			this.columnname = columnname;
			this.jsonname = jsonname;
		}
		
		public Object getValue(Shoreline flowpath) {
			
			switch(this) {
			case AOI_NAME: return flowpath.getAoiName();
			case AOI_ID: return flowpath.getAoi();
			case GEOMETRY: return flowpath.getGeometry();
			case ID: return flowpath.getId();
			}
			
			return null;
		}
	}

	private RowMapper<Shoreline> shorelineMapper = new RowMapper<Shoreline>() {

		private WKBReader reader = new WKBReader();
		
		@Override
		public Shoreline mapRow(ResultSet rs, int rowNum) throws SQLException {
			
			Shoreline path = new Shoreline();
			
			path.setId((UUID)rs.getObject(Field.ID.columnname));
			path.setAoi((UUID)rs.getObject(Field.AOI_ID.columnname));
			path.setAoiName(rs.getString("aoi_name"));
			
			byte[] data = rs.getBytes(Field.GEOMETRY.columnname);
			
			if (data != null) {
				Geometry geom;
				try {
					geom = reader.read(data);
				} catch (ParseException e) {
					throw new SQLException(e);
				}
				path.setGeometry((LineString)geom);
			}
			return path;
		}
	};
	
	/**
	 * Finds the feature with the given uuid.  Will return null
	 * if no feature is found.
	 * 
	 * @param uuid
	 * @return
	 */
	public Shoreline getShoreline(UUID uuid) {
		
		StringBuilder sb = new StringBuilder();
		sb.append("SELECT ");
		for(Field f : Field.values()) {
			if (f.columnname == null) continue;
			if (f == Field.GEOMETRY) {
				sb.append("st_asbinary(e." + f.columnname + ") as " + f.columnname);
			}else {
				sb.append("e.");
				sb.append(f.columnname);
			}
			sb.append(",");
		}
		sb.append("aoi.short_name as aoi_name");
		
		sb.append(" FROM ");
		sb.append(DataSourceTable.SHORELINE.tableName + " e");
		sb.append(" LEFT JOIN ");
		sb.append(DataSourceTable.AOI.tableName + " aoi ");
		sb.append(" ON e.aoi_id = aoi.id");
		sb.append(" WHERE e.id = ?");
		
		try {
			return (Shoreline) jdbcTemplate.queryForObject(sb.toString(), shorelineMapper, uuid);
		}catch (EmptyResultDataAccessException ex) {
			return null;
		}
	}
	

}
