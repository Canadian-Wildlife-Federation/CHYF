package nrcan.cccmeo.chyf.db;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jndi.JndiObjectFactoryBean;
import org.springframework.stereotype.Component;

//Inspired by http://www.topjavatutorial.com/frameworks/spring/spring-jdbc/spring-jdbc-using-annotation-based-configuration/
@Component
@PropertySources({@PropertySource("classpath:queries.properties")})
public class SpringJdbcConfiguration {
	
	@Autowired
	Environment env;

	@Bean
	public DataSource dataSource() {
		try {
			JndiObjectFactoryBean bean = new JndiObjectFactoryBean();
			
			bean.setJndiName("java:comp/env/jdbc/chyf-db");
			bean.setProxyInterface(DataSource.class);
			bean.setLookupOnStartup(false);
			bean.afterPropertiesSet();
			return (DataSource) bean.getObject();
		}catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
	
	@Bean
	public JdbcTemplate jdbcTemplate() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate();
		jdbcTemplate.setDataSource(dataSource());
		jdbcTemplate.setResultsMapCaseInsensitive(true);
		return jdbcTemplate;
	}
	
	@Bean
	public FlowpathDAO flowpathDAO() {
		FlowpathDAOImpl flowDAO = new FlowpathDAOImpl();
		flowDAO.setJdbcTemplate(jdbcTemplate());
		flowDAO.setSqlQuery(env.getProperty("sql.select.flowpath"));
		return flowDAO;
	}
	
	@Bean
	public CatchmentDAO catchmentDAO() {
		CatchmentDAOImpl catchDAO = new CatchmentDAOImpl();
		catchDAO.setJdbcTemplate(jdbcTemplate());
		catchDAO.setSqlQuery(env.getProperty("sql.select.catchment"));
		return catchDAO;
	}
	
	@Bean
	public WaterbodyDAO waterbodyDAO() {
		WaterbodyDAOImpl waterDAO = new WaterbodyDAOImpl();
		waterDAO.setJdbcTemplate(jdbcTemplate());
		waterDAO.setSqlQuery(env.getProperty("sql.select.waterbody"));
		return waterDAO;
	}
	
	@Bean
	public BoundaryDAO boundaryDAO() {
		BoundaryDAOImpl boundaryDAO = new BoundaryDAOImpl();
		boundaryDAO.setJdbcTemplate(jdbcTemplate());
		boundaryDAO.setSqlQuery(env.getProperty("sql.select.workinglimit"));
		return boundaryDAO;
	}
	
	@Bean
	public TileDAO tileDAO() {
		TileDAO dao = new TileDAO();
		dao.setJdbcTemplate(jdbcTemplate());
		dao.setTable(env.getProperty("vectorcache.table"));
		return dao;
	}
	
}
