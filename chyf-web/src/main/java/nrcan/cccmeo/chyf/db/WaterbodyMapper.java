package nrcan.cccmeo.chyf.db;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.springframework.jdbc.core.RowMapper;

import net.refractions.chyf.ChyfPostgresqlReader;

public class WaterbodyMapper implements RowMapper<Waterbody> {
	
	@Override
	public Waterbody mapRow(ResultSet resultSet, int i) throws SQLException {
		Waterbody waterbody = new Waterbody();
		waterbody.setDefinition(resultSet.getInt("definition"));
		waterbody.setArea(resultSet.getDouble("area"));
		try {
			waterbody.setPolygon( (Polygon)ChyfPostgresqlReader.READER.read(resultSet.getBytes("geometry")));
		}catch (ParseException ex) {
			throw new SQLException(ex);
		}
		
		return waterbody;
	}

}
