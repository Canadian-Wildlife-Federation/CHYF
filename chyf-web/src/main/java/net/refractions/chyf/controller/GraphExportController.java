package net.refractions.chyf.controller;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import net.refractions.chyf.ChyfWebApplication;
import net.refractions.chyf.exceptions.InvalidParameterException;
import net.refractions.chyf.model.DataSourceTable;
import net.refractions.chyf.model.GraphExport;

/**
 * 
 * REST api for exporting hydro features in a graph network format
 * 
 * @author Emily
 *
 */
@RestController
@RequestMapping("/" + GraphExportController.PATH)
public class GraphExportController {

	public static final String PATH = "graph";
	
	@Autowired
	private JdbcTemplate jdbcTemplate;
	
	
	private static RowMapper<UUID> UUID_ROW_MAPPER = new RowMapper<UUID>() {
		@Override
		public UUID mapRow(ResultSet rs, int rowNum) throws SQLException {
			return (UUID) rs.getObject(1);
		}};
		
	/**
	 * Gets an individual feature by identifier
	 * 
	 * @param id
	 * @return
	 */
	@Operation(summary = "Export graph for given filter")
	@ApiResponses(value = { 
			@ApiResponse(responseCode = "200",
						description = "Export a set of features with graph structure information",
						content = {@Content(mediaType = "application/gpkg")})
			})			
	@GetMapping(produces = {ChyfWebApplication.GEOPKG_MEDIA_TYPE_STR})
	public ResponseEntity<GraphExport> exportNetwork(
			@ParameterObject GraphExportParameters params,
			HttpServletRequest request) throws Exception{
		
		params.parseAndValidate();
		
		StringBuilder flowpathWhere = new StringBuilder();
		
		if (params.getAois() != null) {
			//find aoi ids
			StringBuilder sb = new StringBuilder();
			sb.append("SELECT id FROM ");
			sb.append(DataSourceTable.AOI.tableName);
			sb.append(" WHERE upper(short_name) in (");
			for (String a : params.getAois()) {
				sb.append("?,");
			}
			sb.deleteCharAt(sb.length() - 1);
			sb.append(")");
			
			List<UUID> aoiuuids = null;
			try {
				aoiuuids = jdbcTemplate.query(sb.toString(),UUID_ROW_MAPPER, params.getAois().toArray());
			}catch (EmptyResultDataAccessException ex) {
				throw new InvalidParameterException("Not aois found");
			}
			if (aoiuuids == null || aoiuuids.size() != params.getAois().size()) {
				throw new InvalidParameterException("At least one aoi is not valid");
			}
			
			flowpathWhere.append("a.aoi_id IN (");
			for (UUID a : aoiuuids) {
				flowpathWhere.append("'" + a.toString() + "',");
			}	
			flowpathWhere.deleteCharAt(flowpathWhere.length() - 1);
			flowpathWhere.append(")");
			
		}else if (params.getBbox() != null) {
			Envelope env = params.getBbox();
			
			flowpathWhere.append(" st_intersects(a.geometry, ");
			flowpathWhere.append("ST_MakeEnvelope(");
			flowpathWhere.append(env.getMinX());
			flowpathWhere.append(",");
			flowpathWhere.append(env.getMinY());
			flowpathWhere.append(",");
			flowpathWhere.append(env.getMaxX());
			flowpathWhere.append(",");
			flowpathWhere.append(env.getMaxY());
			flowpathWhere.append(", ");
			flowpathWhere.append(DataSourceTable.DATA_SRID);
			flowpathWhere.append(" ) )" );
		}else {
			throw new Exception("Invalid parameters");
		}
		
		//flowpath query
		StringBuilder fp = new StringBuilder();
		fp.append("SELECT a.id, a.nid, a.ef_type as ef_type_code, eftype.name as ef_type, ");
		fp.append(" a.ef_subtype as ef_subtype_code, efsubtype.name as ef_subtype,");
		fp.append(" a.rank as rank, a.length as length, ");
		fp.append(" n1.name_en as rivername1_en, n1.name_fr as rivername1_fr, ");
		fp.append(" n1.geodbname as rivername1_src, ");
		fp.append(" n2.name_en as rivername2_en, n2.name_fr as rivername2_fr, ");
		fp.append(" n2.geodbname as rivername2_src,");
		fp.append(" a.from_nexus_id, a.to_nexus_id, a.ecatchment_id, ");
		fp.append(" st_asbinary(a.geometry) as geometry ");
		fp.append(" FROM ");
		fp.append(DataSourceTable.EFLOWPATH.tableName + " a ");
		fp.append(" LEFT JOIN ");
		fp.append(DataSourceTable.NAMES.tableName + "  as n1 on a.rivernameid1 = n1.name_id ");
		fp.append(" LEFT JOIN ");
		fp.append(DataSourceTable.NAMES.tableName + "  as n2 on a.rivernameid2 = n2.name_id ");
		fp.append(" LEFT JOIN ");
		fp.append(DataSourceTable.EF_TYPE.tableName + "  as eftype on eftype.code = a.ef_type ");
		fp.append(" LEFT JOIN ");
		fp.append(DataSourceTable.EF_SUBTYPE.tableName + " as efsubtype on efsubtype.code = a.ef_subtype ");
		fp.append(" WHERE ");
		fp.append(flowpathWhere.toString());
 
		
		//nexus query
		StringBuilder nx = new StringBuilder();
		nx.append("SELECT a.id, a.nexus_type as nexus_type_code, nt.name as nexus_type, st_asbinary(a.geometry) as geometry ");
		nx.append("FROM ");
		nx.append(DataSourceTable.NEXUS.tableName + " a LEFT JOIN ");
		nx.append(DataSourceTable.NEXUS_TYPE.tableName + "  nt ON a.nexus_type = nt.code");
		nx.append(" AND a.id IN (");
		nx.append(" SELECT a.from_nexus_id FROM " + DataSourceTable.EFLOWPATH.tableName + " a WHERE ");
		nx.append(flowpathWhere.toString());
		nx.append(" UNION ");
		nx.append(" SELECT a.to_nexus_id FROM " + DataSourceTable.EFLOWPATH.tableName + " a WHERE ");
		nx.append(flowpathWhere.toString());
		nx.append(" )");
		
		//catchment query
		StringBuilder ct = new StringBuilder();

		ct.append("SELECT a.id, a.nid, a.ec_type as cf_type_code, ectype.name as ec_type,"); 
		ct.append("a.ec_subtype as ec_subtype_code, ecsubtype.name as ec_subtype, ");
		ct.append("a.area as area, ");
		ct.append("n1.name_en as lakename1_en, n1.name_fr as lakename1_fr, "); 
		ct.append("n1.geodbname as lakename1_src, ");
		ct.append("n2.name_en as lakename2_en, n2.name_fr as lakename2_fr, "); 
		ct.append("n2.geodbname as lakename2_src, ");
		ct.append("n3.name_en as rivername1_en, n3.name_fr as rivername1_fr, "); 
		ct.append("n3.geodbname as rivername1_src, ");
		ct.append("n4.name_en as rivername2_en, n4.name_fr as rivername2_fr, "); 
		ct.append("n4.geodbname as rivername2_src, ");
		ct.append("st_asbinary(a.geometry)  as geometry "); 
		ct.append("FROM ");
		ct.append(DataSourceTable.ECATCHMENT.tableName + " a "); 
		ct.append("LEFT JOIN ");
		ct.append(DataSourceTable.NAMES.tableName + " n1 on a.lakenameid1 = n1.name_id "); 
		ct.append("LEFT JOIN ");
		ct.append(DataSourceTable.NAMES.tableName + " n2 on a.lakenameid2 = n2.name_id "); 
		ct.append("LEFT JOIN ");
		ct.append(DataSourceTable.NAMES.tableName + " n3 on a.rivernameid1 = n3.name_id ");
		ct.append("LEFT JOIN ");
		ct.append(DataSourceTable.NAMES.tableName + " as n4 on a.rivernameid2 = n4.name_id ");
		ct.append("LEFT JOIN ");
		ct.append(DataSourceTable.EC_TYPE.tableName + " as ectype on ectype.code = a.ec_type "); 
		ct.append("LEFT JOIN ");
		ct.append(DataSourceTable.EC_SUBTYPE.tableName + " as ecsubtype on ecsubtype.code = a.ec_subtype ");
		ct.append(" WHERE ");
		ct.append(flowpathWhere.toString());

		CoordinateReferenceSystem crs = CRS.decode("EPSG:" + DataSourceTable.DATA_SRID);

		
		ReferencedEnvelope re = null;
		for (String table : new String[] {DataSourceTable.EFLOWPATH.tableName, DataSourceTable.ECATCHMENT.tableName}) {
			StringBuilder extent = new StringBuilder();
			extent.append("WITH bounds AS ( SELECT st_extent(geometry) as bnd ");
			extent.append(" FROM ");
			extent.append(table);
			extent.append(" a WHERE ");
			extent.append(flowpathWhere.toString());
			extent.append(") SELECT st_xmin(bnd), st_ymin(bnd), st_xmax(bnd), st_ymax(bnd) ");
			extent.append("FROM bounds ");
			
			List<ReferencedEnvelope> evs = jdbcTemplate.query(extent.toString(), new RowMapper<ReferencedEnvelope>() {
				@Override
				public ReferencedEnvelope mapRow(ResultSet rs, int rowNum) throws SQLException {
					double xmin = rs.getDouble(1);
					double ymin = rs.getDouble(2);
					double xmax = rs.getDouble(3);
					double ymax = rs.getDouble(4);
					
					return new ReferencedEnvelope(xmin, xmax, ymin, ymax, crs);
				}});
			for (ReferencedEnvelope r: evs) {
				if (re == null) re = r;
				re.expandToInclude(r);
			}
		}
		return ResponseEntity.ok(new GraphExport(fp.toString(), nx.toString(), ct.toString(), re, params));
		
	}
}
