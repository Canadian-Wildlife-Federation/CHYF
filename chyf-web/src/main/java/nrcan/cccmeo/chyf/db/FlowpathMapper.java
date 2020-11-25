package nrcan.cccmeo.chyf.db;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.ParseException;
import org.springframework.jdbc.core.RowMapper;

import net.refractions.chyf.ChyfPostgresqlReader;

public class FlowpathMapper implements RowMapper<Flowpath> {

	@Override
	public Flowpath mapRow(ResultSet resultSet, int i) throws SQLException {
		Flowpath flowpath = new Flowpath();
		flowpath.setName(resultSet.getString("name"));
		flowpath.setNameId(resultSet.getString("nameId"));
		flowpath.setType(resultSet.getString("type"));
		flowpath.setRank(resultSet.getString("rank"));
		flowpath.setLength(resultSet.getDouble("length"));
		
		try {
			flowpath.setLinestring( (LineString)ChyfPostgresqlReader.READER.read(resultSet.getBytes("geometry")));
		}catch (ParseException ex) {
			throw new SQLException(ex);
		}
		
		return flowpath;
	}

}
