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

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import net.refractions.chyf.controller.NameSearchParameters;
import net.refractions.chyf.controller.NameSearchParameters.ResultType;
import net.refractions.chyf.model.DataSourceTable;
import net.refractions.chyf.model.MatchType;
import net.refractions.chyf.model.NamedFeature;

/**
 * DAO for searching across all object types for names
 * 
 * @author Emily
 *
 */
@Component
public class NameFeatureSearchDao {
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	//no type as these can be both flowpaths and waterbodies
	private NamedFeatureMapper namedFeatureMapper = new NamedFeatureMapper();
		
	/**
	 * Search for names across all feature types, merging features with the same name into a single
	 * output object
	 * 
	 * @param search search parameters
	 * @return list of matching features
	 */
	public List<NamedFeature> getFeaturesByNameMerged(NameSearchParameters search){

		StringBuilder sb = new StringBuilder();
		sb.append("WITH nameids AS (");
		sb.append("SELECT name_id ");
		sb.append("FROM ");
		sb.append(DataSourceTable.NAMES.tableName );
		sb.append(" WHERE name_en " + search.getMatchType().getSql() + " ? ");
		sb.append(" OR name_fr " + search.getMatchType().getSql() +" ? ");
		sb.append("),  all_features AS (");
		
		sb.append("SELECT rivernameid1 as name_id, st_union(geometry) as geometry");
		sb.append(" FROM ");
		sb.append(DataSourceTable.ECATCHMENT.tableName );
		sb.append(" WHERE rivernameid1 IN (SELECT name_id FROM nameids) ");
		sb.append(" GROUP BY rivernameid1 ");
		
		sb.append(" UNION ");
		sb.append("SELECT rivernameid2 as name_id, st_union(geometry) as geometry");
		sb.append(" FROM ");
		sb.append(DataSourceTable.ECATCHMENT.tableName );
		sb.append(" WHERE rivernameid2 IN (SELECT name_id FROM nameids) ");
		sb.append(" GROUP BY rivernameid2 ");
		
		sb.append(" UNION ");
		sb.append("SELECT lakenameid1 as name_id, st_union(geometry) as geometry");
		sb.append(" FROM ");
		sb.append(DataSourceTable.ECATCHMENT.tableName );
		sb.append(" WHERE lakenameid1 IN (SELECT name_id FROM nameids) ");
		sb.append(" GROUP BY lakenameid1 ");
		
		sb.append(" UNION ");
		sb.append("SELECT lakenameid2 as name_id, st_union(geometry) as geometry");
		sb.append(" FROM ");
		sb.append(DataSourceTable.ECATCHMENT.tableName );
		sb.append(" WHERE lakenameid2 IN (SELECT name_id FROM nameids) ");
		sb.append(" GROUP BY lakenameid2 ");
		
		sb.append(" UNION ");
		sb.append("SELECT rivernameid1, st_union(geometry) as geometry");
		sb.append(" FROM ");
		sb.append(DataSourceTable.EFLOWPATH.tableName );
		sb.append(" WHERE rivernameid1 IN (SELECT name_id FROM nameids) ");
		sb.append(" GROUP BY rivernameid1 ");
		
		sb.append(" UNION ");
		sb.append("SELECT rivernameid2, st_union(geometry) as geometry");
		sb.append(" FROM ");
		sb.append(DataSourceTable.EFLOWPATH.tableName );
		sb.append(" WHERE rivernameid2 IN (SELECT name_id FROM nameids) ");
		sb.append(" GROUP BY rivernameid2 ");
		sb.append(") ");
		sb.append("SELECT a.name_id, a.name_en, a.name_fr,");
		if (search.getResultType() == ResultType.BBOX) {
			sb.append(" st_asbinary(st_extent(b.geometry)) as geometry");
		}else {
			sb.append(" st_asbinary(st_union(b.geometry)) as geometry");
		}
		sb.append(" FROM ");
		sb.append(" all_features b, ");
		sb.append(DataSourceTable.NAMES.tableName );
		sb.append(" a WHERE a.name_id = b.name_id ");
		sb.append(" GROUP BY a.name_id, a.name_en, a.name_fr ");
		sb.append(" limit " + search.getMaxresults());
		
		String name = search.getEscapedName();
		if (search.getMatchType() == MatchType.CONTAINS) {
			name = "%" + name+ "%";
		}
		
		try {
			return jdbcTemplate.query(sb.toString(), namedFeatureMapper, name, name);
		}catch (EmptyResultDataAccessException ex) {
			return null;
		}
	}
	
}
