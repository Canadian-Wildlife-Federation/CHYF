package nrcan.cccmeo.chyf.db;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.springframework.jdbc.core.RowMapper;

import net.refractions.chyf.ChyfPostgresqlReader;

public class BoundaryMapper implements RowMapper<Boundary> {

	@Override
	public Boundary mapRow(ResultSet resultSet, int i) throws SQLException {
		Boundary boundary = new Boundary();
		try {
			boundary.setPolygon( (Polygon)ChyfPostgresqlReader.READER.read(resultSet.getBytes("geometry")));
		}catch (ParseException ex) {
			throw new SQLException(ex);
		}
		
		return boundary;
	}

}
