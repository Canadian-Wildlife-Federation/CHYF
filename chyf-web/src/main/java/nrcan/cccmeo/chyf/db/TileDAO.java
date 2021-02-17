package nrcan.cccmeo.chyf.db;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public class TileDAO {

	private JdbcTemplate jdbcTemplate;
	private String table;
	
	
	public void setJdbcTemplate(JdbcTemplate jdbcTemplate){
        this.jdbcTemplate = jdbcTemplate;
    }
		
	public void setTable(String table){
        this.table = table;
    }
	
	public byte[] getTile(String key) {
		List<byte[]> data = jdbcTemplate.query("select tile from " + table + " where key = ?", new Object[] {key},
				new RowMapper<byte[]>() {
					@Override
					public byte[] mapRow(ResultSet rs, int rowNum) throws SQLException {
						return rs.getBytes("tile");
					}});
		if (data.size() > 0) return data.get(0);
		return null;
	}
	
	public boolean containsKey(String key) {
		Integer cnt = jdbcTemplate.queryForObject("select count(*) from " + table + " where key = ?", new Object[]{key}, Integer.class);
		return cnt > 0;
	}
	public void setTile(String key, byte[] data) {
		jdbcTemplate.update("insert into " + table + "(key, tile) values (?,?)", key,data);
	}
	
	public void removeTile(String key) {
		jdbcTemplate.update("delete from " + table + " set tile = ? ", key);
	}
	
	public void clear() {
		jdbcTemplate.update("delete from " + table);
	}
}
