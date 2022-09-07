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
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.springframework.jdbc.core.RowMapper;

import net.refractions.chyf.model.HydroFeature;
import net.refractions.chyf.model.NamedFeature;
import net.refractions.chyf.model.dao.ECatchmentDao.Field;

/**
 * Row mapper that maps name fields and geometry into new NamedFeature objects.
 * 
 * Resultset requires name_id, name_en, and name_fr fields.
 * 
 * @author Emily
 *
 */
public class NamedFeatureMapper implements RowMapper<NamedFeature> {

	private WKBReader reader = new WKBReader();
	
	private HydroFeature.Type type;
	
	public NamedFeatureMapper(HydroFeature.Type type) {
		this.type = type;
	}

	/**
	 * If using non arg constructor you will need to overwrite the
	 * getType function 
	 */
	public NamedFeatureMapper() {
	}
	
	protected HydroFeature.Type getType(ResultSet rs) throws SQLException{
		return type;
	}
	
	@Override
	public NamedFeature mapRow(ResultSet rs, int rowNum) throws SQLException {
		
		NamedFeature path = new NamedFeature(getType(rs));

		path.setNameId1((UUID)rs.getObject("name_id"));
		path.setName1(rs.getString("name_en"), rs.getString("name_fr"));
		
		byte[] data = rs.getBytes(Field.GEOMETRY.columnname);
		
		if (data != null) {
			Geometry geom;
			try {
				geom = reader.read(data);
			} catch (ParseException e) {
				throw new SQLException(e);
			}
			path.setGeometry(geom);
		}
		return path;
	}

}
