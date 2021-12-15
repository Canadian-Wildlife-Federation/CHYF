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
import java.sql.Types;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Data access interface for tile cache
 * 
 * @author Emily
 *
 */
public class VectorTileCacheDao {

	private JdbcTemplate jdbcTemplate;

	public void setJdbcTemplate(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public byte[] getTile(String key) {
		List<byte[]> data = jdbcTemplate.query(
				"select tile from " + DataSourceTable.VECTOR_TILE_CACHE.tableName + " where key = ?",
				new Object[] { key }, new int[] { Types.VARCHAR }, new RowMapper<byte[]>() {
					@Override
					public byte[] mapRow(ResultSet rs, int rowNum) throws SQLException {
						return rs.getBytes("tile");
					}
				});
		if (data.size() > 0)
			return data.get(0);
		return null;
	}

	public boolean containsKey(String key) {
		Integer cnt = jdbcTemplate.queryForObject(
				"select count(*) from " + DataSourceTable.VECTOR_TILE_CACHE.tableName + " where key = ?",
				new Object[] { key }, new int[] { Types.VARCHAR }, Integer.class);
		return cnt > 0;
	}

	public void setTile(String key, byte[] data) {
		jdbcTemplate.update("insert into " + DataSourceTable.VECTOR_TILE_CACHE.tableName + "(key, tile) values (?,?)",
				key, data);
	}

	public void removeTile(String key) {
		jdbcTemplate.update("delete from " + DataSourceTable.VECTOR_TILE_CACHE.tableName + " set tile = ? ", key);
	}

	public void clear() {
		jdbcTemplate.update("delete from " + DataSourceTable.VECTOR_TILE_CACHE.tableName);
	}
}
