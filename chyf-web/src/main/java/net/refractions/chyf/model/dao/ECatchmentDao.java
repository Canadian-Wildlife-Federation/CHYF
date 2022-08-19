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
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import net.refractions.chyf.controller.NameSearchParameters;
import net.refractions.chyf.model.DataSourceTable;
import net.refractions.chyf.model.ECatchment;
import net.refractions.chyf.model.HydroFeature;
import net.refractions.chyf.model.MatchType;
import net.refractions.chyf.model.NamedFeature;

/**
 * Data access object for querying catchments
 * 
 * @author Emily
 *
 */
@Component
public class ECatchmentDao {

	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	public static enum Field{
		ID ("id", "id"),
		EC_TYPE("ec_type", "ec_type"),
		EC_SUBTYPE("ec_subtype", "ec_subtype"),
		AREA("area", "area"),
		RESERVOIR("is_reservoir", "is_reservoir"),
		RIVERNAME1_ID("rivernameid1", "rivernameid1"),
		RIVERNAME2_ID("rivernameid2", "rivernameid2"),
		LAKENAME1_ID("lakenameid1", "lakenameid1"),
		LAKENAME2_ID("lakenameid2", "lakenameid2"),
		AOI_ID("aoi_id", "aoi_id"),
		
		GEOMETRY("geometry", "geometry"),
		
		EC_TYPE_NAME(null, "ec_type_name"),
		EC_SUBTYPE_NAME(null, "ec_subtype_name"),
		LAKENAME1_EN(null, "lakename1_en"),
		LAKENAME1_FR(null, "lakename1_fr"),
		LAKENAME2_EN(null, "lakename2_en"),
		LAKENAME2_FR(null, "lakename2_fr"),
		RIVERNAME1_EN(null, "rivername1_en"),
		RIVERNAME1_FR(null, "rivername1_fr"),
		RIVERNAME2_EN(null, "rivername2_en"),
		RIVERNAME2_FR(null, "rivername2_fr"),
		AOI_NAME(null, "aoi_name");
		
		public String columnname;
		public String jsonname;
		
		Field(String columnname, String jsonname){
			this.columnname = columnname;
			this.jsonname = jsonname;
		}
		
		public Object getValue(ECatchment catchment) {
			
			switch(this) {
			case AOI_NAME: return catchment.getAoiName();
			case AOI_ID: return catchment.getAoi();
			case EC_SUBTYPE: return catchment.getSubType();
			case EC_SUBTYPE_NAME: return catchment.getSubTypeName();
			case EC_TYPE: return catchment.getType();
			case EC_TYPE_NAME: return catchment.getTypeName();
			case RESERVOIR: return catchment.getIsReservoir();
			case GEOMETRY: return catchment.getGeometry();
			case ID: return catchment.getId();
			case AREA: return catchment.getArea();
			case RIVERNAME1_EN: return catchment.getNameEn1();
			case RIVERNAME1_FR: return catchment.getNameFr1();
			case RIVERNAME1_ID: return catchment.getNameId1();
			case RIVERNAME2_EN: return catchment.getNameEn2();
			case RIVERNAME2_FR: return catchment.getNameFr2();
			case RIVERNAME2_ID: return catchment.getNameId2();
			case LAKENAME1_EN: return catchment.getLakeNameEn1();
			case LAKENAME1_FR: return catchment.getLakeNameFr1();
			case LAKENAME1_ID: return catchment.getLakeNameId1();
			case LAKENAME2_EN: return catchment.getLakeNameEn2();
			case LAKENAME2_FR: return catchment.getLakeNameFr2();
			case LAKENAME2_ID: return catchment.getLakeNameId2();
			
			}
			
			return null;
				
		}	
	}
	

	private RowMapper<ECatchment> ecatchmentMapper = new RowMapper<ECatchment>() {

		private WKBReader reader = new WKBReader();
		
		@Override
		public ECatchment mapRow(ResultSet rs, int rowNum) throws SQLException {
			
			ECatchment path = new ECatchment();
			
			path.setId((UUID)rs.getObject(Field.ID.columnname));
			path.setAoi((UUID)rs.getObject(Field.AOI_ID.columnname));
			path.setAoiName(rs.getString("aoi_name"));
			
			path.setSubType((Integer)rs.getObject(Field.EC_SUBTYPE.columnname));
			path.setType(rs.getInt(Field.EC_TYPE.columnname));
			path.setArea(rs.getDouble(Field.AREA.columnname));
			path.setNameId1((UUID)rs.getObject(Field.RIVERNAME1_ID.columnname));
			path.setNameId2((UUID)rs.getObject(Field.RIVERNAME2_ID.columnname));
			path.setLakeNameId1((UUID)rs.getObject(Field.LAKENAME1_ID.columnname));
			path.setLakeNameId2((UUID)rs.getObject(Field.LAKENAME2_ID.columnname));
			path.setName1(rs.getString("rivername1_en"), rs.getString("rivername1_fr"));
			path.setName2(rs.getString("rivername2_en"), rs.getString("rivername2_fr"));
			path.setLakeName1(rs.getString("lakename1_en"), rs.getString("lakename1_fr"));
			path.setLakeName2(rs.getString("lakename2_en"), rs.getString("lakename2_fr"));
			path.setSubTypeName(rs.getString("ec_subtypename"));
			path.setTypeName(rs.getString("ec_typename"));
			path.setIsReservoir((Boolean)rs.getObject(Field.RESERVOIR.columnname));
			
			byte[] data = rs.getBytes(Field.GEOMETRY.columnname);
			
			if (data != null) {
				Geometry geom;
				try {
					geom = reader.read(data);
				} catch (ParseException e) {
					throw new SQLException(e);
				}
				path.setGeometry((Polygon)geom);
			}
			return path;
		}
	};
	
	private NamedFeatureMapper namedFeatureMapper = new NamedFeatureMapper() {
		@Override
		public HydroFeature.Type getType(ResultSet rs) throws SQLException{
			Integer ectype = rs.getInt(Field.EC_TYPE.columnname);
			return ECatchment.EcType.toType(ectype);
		}
	};
		
	/**
	 * Finds the feature with the given uuid.  Will return null
	 * if no feature is found.
	 * 
	 * @param uuid
	 * @return
	 */
	public ECatchment getECatchment(UUID uuid) {
		
		StringBuilder sb = new StringBuilder();
		sb.append(buildFeatureSelect());
		sb.append(" WHERE e.id = ?");
		
		try {
			return (ECatchment) jdbcTemplate.queryForObject(sb.toString(), ecatchmentMapper, uuid);
		}catch (EmptyResultDataAccessException ex) {
			return null;
		}
	}
	
	/**
	 * Searches catchments by name returning individual catchments
	 * 
	 * @param search search parameters
	 * @return list of catchments matching search parameters
	 */
	public List<ECatchment> getFeaturesByName(NameSearchParameters search){
		StringBuilder sb = new StringBuilder();
		sb.append(buildFeatureSelect());
		sb.append(" WHERE ");
		
		boolean isCatchment = search.getFeatureType() == null || search.getFeatureType().contains(HydroFeature.Type.CATCHMENT);
		boolean isWaterbody = search.getFeatureType() == null || search.getFeatureType().contains(HydroFeature.Type.WATERBODY);
		
		if (isCatchment && !isWaterbody) {
			sb.append(Field.EC_TYPE + " != " + ECatchment.EcType.WATER.code);
			sb.append("AND");
		}else if (!isCatchment && isWaterbody) {
			sb.append(Field.EC_TYPE + " = " + ECatchment.EcType.WATER.code);
			sb.append("AND");
		}
		
		sb.append(" ( rivernames1.name_en " );
		sb.append(search.getMatchType().getSql());
		sb.append(" ? ");
		sb.append(" or  rivernames1.name_fr " );
		sb.append(search.getMatchType().getSql());
		sb.append(" ? ");
		sb.append(" or  rivernames2.name_en " );
		sb.append(search.getMatchType().getSql());
		sb.append(" ? ");
		sb.append(" or  rivernames2.name_fr " );
		sb.append(search.getMatchType().getSql());
		sb.append(" ? ");
		sb.append(" or  lakenames1.name_en " );
		sb.append(search.getMatchType().getSql());
		sb.append(" ? ");
		sb.append(" or  lakenames1.name_fr " );
		sb.append(search.getMatchType().getSql());
		sb.append(" ? ");
		sb.append(" or  lakenames2.name_en " );
		sb.append(search.getMatchType().getSql());
		sb.append(" ? ");
		sb.append(" or  lakenames2.name_fr " );
		sb.append(search.getMatchType().getSql());
		sb.append(" ? ");
		sb.append(" ) ");
		sb.append(" limit " + search.getMaxresults());
		
		String name = search.getEscapedName();
		if (search.getMatchType() == MatchType.CONTAINS) {
			name = "%" + name + "%";
		}
		
		try {
			return jdbcTemplate.query(sb.toString(), ecatchmentMapper, name, name, name, name, name, name, name, name);
		}catch (EmptyResultDataAccessException ex) {
			return null;
		}
	}
	
	/**
	 * Searches catchments by name, merging features with the same name id into a single result item
	 * 
	 * @param search search parameters
	 * @return list of named features matching the search parameters
	 */
	public List<NamedFeature> getFeaturesByNameMerged(NameSearchParameters search){
		List<NamedFeature> features = new ArrayList<>();
		
		for (Field f : new Field[] {Field.RIVERNAME1_ID, Field.RIVERNAME2_ID, Field.LAKENAME1_ID, Field.LAKENAME2_ID}) {
			StringBuilder sb = new StringBuilder();
			sb.append("SELECT e.");
			sb.append(f.columnname + " as name_id, e.");
			sb.append(Field.EC_TYPE.columnname + ",");
			sb.append("names.name_en as name_en, ");
			sb.append("names.name_fr as name_fr, ");
			sb.append("st_asbinary(st_union(" + Field.GEOMETRY.columnname + ")) as " + Field.GEOMETRY.columnname);
			sb.append(" FROM ");
			sb.append(DataSourceTable.ECATCHMENT.tableName + " e");
			sb.append(" LEFT JOIN ");
			sb.append(DataSourceTable.NAMES.tableName + " names ");
			sb.append(" ON e." + f.columnname + " = names.name_id");
			
			sb.append(" WHERE ");
			
			boolean isCatchment = search.getFeatureType() == null || search.getFeatureType().contains(HydroFeature.Type.CATCHMENT);
			boolean isWaterbody = search.getFeatureType() == null || search.getFeatureType().contains(HydroFeature.Type.WATERBODY);
			
			if (isCatchment && !isWaterbody) {
				sb.append(Field.EC_TYPE + " != " + ECatchment.EcType.WATER.code);
				sb.append("AND");
			}else if (!isCatchment && isWaterbody) {
				sb.append(Field.EC_TYPE + " = " + ECatchment.EcType.WATER.code);
				sb.append("AND");
			}
			
			sb.append(" ( names.name_en " );
			sb.append(search.getMatchType().getSql());
			sb.append(" ? ");
			sb.append(" or name_fr " );
			sb.append(search.getMatchType().getSql());
			sb.append(" ? ) ");
			sb.append(" GROUP BY e.");
			sb.append(f.columnname);
			sb.append(", name_en, name_fr, e.");
			sb.append(Field.EC_TYPE.columnname);
			
			sb.append(" limit " + search.getMaxresults());
			
			String name = search.getEscapedName();
			if (search.getMatchType() == MatchType.CONTAINS) {
				name = "%" + name+ "%";
			}
			
			try {
				features.addAll(jdbcTemplate.query(sb.toString(), namedFeatureMapper, name, name));
			}catch (EmptyResultDataAccessException ex) {
			}
			if (features.size() > search.getMaxresults()) return features.subList(0, search.getMaxresults());
		}
		if (features.isEmpty()) return null;
		return features;
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
		sb.append("ectype.name as ec_typename, ");
		sb.append("ecsubtype.name as ec_subtypename, ");
		sb.append("rivernames1.name_en as rivername1_en, ");
		sb.append("rivernames1.name_fr as rivername1_fr, ");
		sb.append("rivernames2.name_en as rivername2_en, ");
		sb.append("rivernames2.name_fr as rivername2_fr, ");
		sb.append("lakenames1.name_en as lakename1_en, ");
		sb.append("lakenames1.name_fr as lakename1_fr, ");
		sb.append("lakenames2.name_en as lakename2_en, ");
		sb.append("lakenames2.name_fr as lakename2_fr, ");
		sb.append("aoi.short_name as aoi_name");
		
		sb.append(" FROM ");
		sb.append(DataSourceTable.ECATCHMENT.tableName + " e");
		sb.append(" LEFT JOIN ");
		sb.append(DataSourceTable.EC_TYPE.tableName + " ectype ");
		sb.append(" ON e.ec_type = ectype.code");
		sb.append(" LEFT JOIN ");
		sb.append(DataSourceTable.EC_SUBTYPE.tableName + " ecsubtype ");
		sb.append(" ON e.ec_subtype = ecsubtype.code");
		sb.append(" LEFT JOIN ");
		sb.append(DataSourceTable.NAMES.tableName + " rivernames1 ");
		sb.append(" ON e." + Field.RIVERNAME1_ID.columnname + " = rivernames1.name_id");
		sb.append(" LEFT JOIN ");
		sb.append(DataSourceTable.NAMES.tableName + " rivernames2 ");
		sb.append(" ON e." + Field.RIVERNAME2_ID.columnname + " = rivernames2.name_id");
		sb.append(" LEFT JOIN ");
		sb.append(DataSourceTable.NAMES.tableName + " lakenames1 ");
		sb.append(" ON e." + Field.LAKENAME1_ID.columnname + " = lakenames1.name_id");
		sb.append(" LEFT JOIN ");
		sb.append(DataSourceTable.NAMES.tableName + " lakenames2 ");
		sb.append(" ON e." + Field.LAKENAME2_ID.columnname + " = lakenames2.name_id");
		sb.append(" LEFT JOIN ");
		sb.append(DataSourceTable.AOI.tableName + " aoi ");
		sb.append(" ON e.aoi_id = aoi.id");
		return sb.toString();
	}
}
