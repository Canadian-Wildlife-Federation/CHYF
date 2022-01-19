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
package net.refractions.chyf;

import javax.sql.DataSource;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import net.refractions.chyf.model.VectorTileCacheDao;
import net.refractions.chyf.model.VectorTileDao;

/**
 * Configuration for data sources 
 * @author Emily
 *
 */
@Configuration
public class DataSourceCofiguration {
	
	private static final String DB_URL_ENV = "CHYF_DB_URL";
	private static final String DB_USER_ENV = "CHYF_DB_USER";
	private static final String DB_PASS_ENV = "CHYF_DB_PASSWORD";

	@Bean
	public DataSource getDataSource() {
		String url = System.getProperty(DB_URL_ENV);
		if (url == null)
			url = System.getenv(DB_URL_ENV);

		String user = System.getProperty(DB_USER_ENV);
		if (user == null)
			user = System.getenv(DB_USER_ENV);

		String password = System.getProperty(DB_PASS_ENV);
		if (password == null)
			password = System.getenv(DB_PASS_ENV);

		if (url == null || user == null || password == null) {
			throw new IllegalStateException("The db connection environment variables (" + DB_URL_ENV + ", "
					+ DB_USER_ENV + ", " + DB_PASS_ENV + ") are not configured.");
		}

		DataSourceBuilder<?> dataSourceBuilder = DataSourceBuilder.create();
		dataSourceBuilder.driverClassName("org.postgresql.Driver");
		dataSourceBuilder.url(url);
		dataSourceBuilder.username(user);
		dataSourceBuilder.password(password);
		return dataSourceBuilder.build();
	}

	@Bean
	public JdbcTemplate jdbcTemplate() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate();
		jdbcTemplate.setDataSource(getDataSource());
		jdbcTemplate.setResultsMapCaseInsensitive(true);
		return jdbcTemplate;
	}

	@Bean
	public VectorTileDao vectorTileDao() {
		VectorTileDao tileDao = new VectorTileDao();
		tileDao.setJdbcTemplate(jdbcTemplate());
		return tileDao;
	}

	@Bean
	public VectorTileCacheDao vectorTileCacheDao() {
		VectorTileCacheDao cacheDao = new VectorTileCacheDao();
		cacheDao.setJdbcTemplate(jdbcTemplate());
		return cacheDao;
	}

}
