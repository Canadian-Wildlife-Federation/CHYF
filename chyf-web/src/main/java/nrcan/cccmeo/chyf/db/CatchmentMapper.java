package nrcan.cccmeo.chyf.db;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.RowMapper;

import net.refractions.chyf.ChyfDataReader;
import net.refractions.chyf.ChyfPostgresqlReader;

public class CatchmentMapper implements RowMapper<Catchment> {
	
	static final Logger logger = LoggerFactory.getLogger(ChyfDataReader.class.getCanonicalName());

	private int id = 0;
	@Override
	public Catchment mapRow(ResultSet resultSet, int x) {
		Catchment catchment = new Catchment();
		try {
			catchment.setId( id++ );
			
			try {
				catchment.setCatchment( (Polygon)ChyfPostgresqlReader.READER.read(resultSet.getBytes("geometry")));
			}catch (ParseException ex) {
				throw new SQLException(ex);
			}
			
			
			List <String> fieldName = new ArrayList<>();
			Field[] fields = catchment.getFieldsName();
			for (Field f : fields) {
				fieldName.add(f.getName());
			}
			
			// Set attributes of type double
			// Area attribute is of type numeric (double)
			ResultSetMetaData rsm = resultSet.getMetaData();
			for (int i = 0; i < rsm.getColumnCount(); i++) {
				String label = rsm.getColumnLabel(i+1);
				String type = rsm.getColumnTypeName(i+1);
				if (fieldName.contains(label) && type == "numeric") {
					catchment.setAttribute(label, resultSet.getDouble(label));
				}
				
			}
			
		} catch (Exception e) {
			logger.warn("Problem while mapping cathcment varialbles", e);
		} 
		return catchment;
		}
		
}
