package nrcan.cccmeo.chyf.db;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Component;

//Inspired by http://www.topjavatutorial.com/frameworks/spring/spring-jdbc/spring-jdbc-using-annotation-based-configuration/
@Component
@PropertySources({@PropertySource("classpath:queries.properties")})
public class SpringJdbcConfiguration {
	
	//database connection variables
	private static final String DB_URL_ENV = "CHYF_DB_URL";
	private static final String DB_USER_ENV = "CHYF_DB_USER";
	private static final String DB_PASS_ENV = "CHYF_DB_PASSWORD";
		
	@Autowired
	Environment env;

	@Bean
	public DataSource dataSource() {
		String url = System.getProperty(DB_URL_ENV);
    	if (url == null) url = System.getenv(DB_URL_ENV);
    	
    	String user = System.getProperty(DB_USER_ENV);
    	if (user == null) user = System.getenv(DB_USER_ENV);
    	
    	String password = System.getProperty(DB_PASS_ENV);
    	if (password == null) password= System.getenv(DB_PASS_ENV);
    	
    	if (url == null || user == null || password == null) {
    		throw new IllegalStateException("The db connection environment variables (" + DB_URL_ENV + ", " + DB_USER_ENV + ", " + DB_PASS_ENV + ") are not configured.");
    	}
    	
		DriverManagerDataSource driverManagerDataSource = new DriverManagerDataSource();
		driverManagerDataSource.setDriverClassName("org.postgresql.Driver");
		driverManagerDataSource.setUrl(url);
		driverManagerDataSource.setUsername(user);
		driverManagerDataSource.setPassword(password);
		return driverManagerDataSource;
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
