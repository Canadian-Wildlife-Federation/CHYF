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
import java.util.ArrayList;
import java.util.List;
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

import net.refractions.chyf.controller.NameSearchParameters;
import net.refractions.chyf.model.DataSourceTable;
import net.refractions.chyf.model.EFlowpath;
import net.refractions.chyf.model.HydroFeature;
import net.refractions.chyf.model.MatchType;
import net.refractions.chyf.model.NamedFeature;

/**
 * Data access object for querying flowpaths
 * 
 * @author Emily
 *
 */
@Component
public class EFlowpathDao {

    
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	public static enum Field{
		ID ("id", "id"),
		EF_TYPE("ef_type", "ef_type"),
		EF_SUBTYPE("ef_subtype", "ef_subtype"),
		RANK("rank", "rank"),
		LENGTH("length", "length"),
		NAME1_ID("rivernameid1", "name1_id"),
		NAME2_ID("rivernameid2", "name2_id"),
		AOI_ID("aoi_id", "aoi_id"),
		ECATCHMENT_ID("ecatchment_id", "ecachment_id"),
		GEOMETRY("geometry", "geometry"),
		
		EF_TYPE_NAME(null, "ef_type_name"),
		EF_SUBTYPE_NAME(null, "ef_subtype_name"),
		NAME1_EN(null, "name1_en"),
		NAME1_FR(null, "name1_fr"),
		NAME2_EN(null, "name2_en"),
		NAME2_FR(null, "name2_fr"),
		AOI_NAME(null, "aoi_name");
		
		public String columnname;
		public String jsonname;
		
		Field(String columnname, String jsonname){
			this.columnname = columnname;
			this.jsonname = jsonname;
		}
		
		public Object getValue(EFlowpath flowpath) {
			
			switch(this) {
			case AOI_NAME: return flowpath.getAoiName();
			case AOI_ID: return flowpath.getAoi();
			case ECATCHMENT_ID: return flowpath.getEcatchmentId();
			case EF_SUBTYPE: return flowpath.getSubType();
			case EF_SUBTYPE_NAME: return flowpath.getSubTypeName();
			case EF_TYPE: return flowpath.getType();
			case EF_TYPE_NAME: return flowpath.getTypeName();
			case GEOMETRY: return flowpath.getGeometry();
			case ID: return flowpath.getId();
			case LENGTH: return flowpath.getLength();
			case NAME1_EN: return flowpath.getNameEn1();
			case NAME1_FR: return flowpath.getNameFr1();
			case NAME1_ID: return flowpath.getNameId1();
			case NAME2_EN: return flowpath.getNameEn2();
			case NAME2_FR: return flowpath.getNameFr2();
			case NAME2_ID: return flowpath.getNameId2();
			case RANK: return flowpath.getRank();
			}
			return null;
		}
	}


	private RowMapper<EFlowpath> eflowpathMapper = new RowMapper<EFlowpath>() {

		private WKBReader reader = new WKBReader();
		
		@Override
		public EFlowpath mapRow(ResultSet rs, int rowNum) throws SQLException {
			
			EFlowpath path = new EFlowpath();
			
			path.setId((UUID)rs.getObject(Field.ID.columnname));
			path.setAoi((UUID)rs.getObject(Field.AOI_ID.columnname));
			path.setAoiName(rs.getString("aoi_name"));
			path.setEcatchmentId((UUID)rs.getObject(Field.ECATCHMENT_ID.columnname));
			
			path.setSubType((Integer)rs.getObject(Field.EF_SUBTYPE.columnname));
			path.setType(rs.getInt(Field.EF_TYPE.columnname));
			path.setLength(rs.getDouble(Field.LENGTH.columnname));
			path.setNameId1((UUID)rs.getObject(Field.NAME1_ID.columnname));
			path.setName1(rs.getString("name1_en"), rs.getString("name1_fr"));
			path.setNameId2((UUID)rs.getObject(Field.NAME2_ID.columnname));
			path.setName2(rs.getString("name2_en"), rs.getString("name2_fr"));
			path.setRank(rs.getInt(Field.RANK.columnname));
			path.setSubTypeName(rs.getString("ef_subtypename"));
			path.setTypeName(rs.getString("ef_typename"));
			
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
	
	private RowMapper<NamedFeature> namedFeatureMapper = new NamedFeatureMapper(HydroFeature.Type.FLOWPATH);
	
	/**
	 * Finds the feature with the given uuid.  Will return null
	 * if no feature is found.
	 * 
	 * @param uuid
	 * @return
	 */
	public EFlowpath getEFlowpath(UUID uuid) {
		
		StringBuilder sb = new StringBuilder();
		sb.append(buildFeatureSelect());
		sb.append(" WHERE e.id = ?");
		
		try {
			return (EFlowpath) jdbcTemplate.queryForObject(sb.toString(), eflowpathMapper, uuid);
		}catch (EmptyResultDataAccessException ex) {
			return null;
		}
	}
	
	/**
	 * Searches flowpaths by name returning individual flowpaths
	 * 
	 * @param search search parameters
	 * @return list of flowpaths matching search parameters
	 */
	public List<EFlowpath> getFeaturesByName(NameSearchParameters search){
		StringBuilder sb = new StringBuilder();
		sb.append(buildFeatureSelect());
		sb.append(" WHERE ");
		sb.append(" ( names1.name_en " );
		sb.append(search.getMatchType().getSql());
		sb.append(" ? ");
		sb.append(" or  names1.name_fr " );
		sb.append(search.getMatchType().getSql());
		sb.append(" ? ");
		sb.append(" or  names2.name_en " );
		sb.append(search.getMatchType().getSql());
		sb.append(" ? ");
		sb.append(" or  names2.name_fr " );
		sb.append(search.getMatchType().getSql());
		sb.append(" ? ");
		sb.append(")");
		sb.append(" limit " + search.getMaxresults());
		
		String name = search.getEscapedName();
		if (search.getMatchType() == MatchType.CONTAINS) {
			name = "%" + name+ "%";
		}
		
		try {
			return jdbcTemplate.query(sb.toString(), eflowpathMapper, name, name, name, name);
		}catch (EmptyResultDataAccessException ex) {
			return null;
		}
	}
	
	/**
	 * Searches flowpaths by name, merging features with the same name id into a single result item
	 * 
	 * @param search search parameters
	 * @return list of named features matching the search parameters
	 */
	public List<NamedFeature> getFeaturesByNameMerged(NameSearchParameters search){
		
		List<NamedFeature> namedfeatures = new ArrayList<>();
		
		for (Field f : new Field[] {Field.NAME1_ID, Field.NAME2_ID}) {
			StringBuilder sb = new StringBuilder();
			sb.append("SELECT e.");
			sb.append(f.columnname + " as name_id,");
			sb.append("names.name_en as name_en, ");
			sb.append("names.name_fr as name_fr, ");
			sb.append("st_asbinary(st_union(" + Field.GEOMETRY.columnname + ")) as " + Field.GEOMETRY.columnname);
			sb.append(" FROM ");
			sb.append(DataSourceTable.EFLOWPATH.tableName + " e");
			sb.append(" LEFT JOIN ");
			sb.append(DataSourceTable.NAMES.tableName + " names ");
			sb.append(" ON e." + f.columnname + " = names.name_id");
			
			sb.append(" WHERE ");
			sb.append(" ( names.name_en " );
			sb.append(search.getMatchType().getSql());
			sb.append(" ? ");
			sb.append(" or  name_fr " );
			sb.append(search.getMatchType().getSql());
			sb.append(" ? ) ");
			sb.append(" GROUP BY e.");
			sb.append(f.columnname);
			sb.append(", name_en, name_fr");
	
			sb.append(" limit " + search.getMaxresults());
			
			String name = search.getEscapedName();
			if (search.getMatchType() == MatchType.CONTAINS) {
				name = "%" + name+ "%";
			}
			
			try {
				namedfeatures.addAll( jdbcTemplate.query(sb.toString(), namedFeatureMapper, name, name));
			}catch (EmptyResultDataAccessException ex) {
			}
			if (namedfeatures.size() > search.getMaxresults()) {
				return namedfeatures.subList(0, search.getMaxresults());
			}
		}
		if (namedfeatures.isEmpty()) return null;
		return namedfeatures;
	}
	
	
	private String buildFeatureSelect() {
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
		sb.append("eftype.name as ef_typename, ");
		sb.append("efsubtype.name as ef_subtypename, ");
		sb.append("names1.name_en as name1_en, ");
		sb.append("names1.name_fr as name1_fr, ");
		sb.append("names2.name_en as name2_en, ");
		sb.append("names2.name_fr as name2_fr, ");
		sb.append("aoi.short_name as aoi_name");
		
		sb.append(" FROM ");
		sb.append(DataSourceTable.EFLOWPATH.tableName + " e");
		sb.append(" LEFT JOIN ");
		sb.append(DataSourceTable.EF_TYPE.tableName + " eftype ");
		sb.append(" ON e.ef_type = eftype.code");
		sb.append(" LEFT JOIN ");
		sb.append(DataSourceTable.EF_SUBTYPE.tableName + " efsubtype ");
		sb.append(" ON e.ef_subtype = efsubtype.code");
		sb.append(" LEFT JOIN ");
		sb.append(DataSourceTable.NAMES.tableName + " names1 ");
		sb.append(" ON e." + Field.NAME1_ID.columnname + " = names1.name_id");
		sb.append(" LEFT JOIN ");
		sb.append(DataSourceTable.NAMES.tableName + " names2 ");
		sb.append(" ON e." + Field.NAME2_ID.columnname + " = names2.name_id");
		sb.append(" LEFT JOIN ");
		sb.append(DataSourceTable.AOI.tableName + " aoi ");
		sb.append(" ON e.aoi_id = aoi.id");
		
		return sb.toString();
	}

}