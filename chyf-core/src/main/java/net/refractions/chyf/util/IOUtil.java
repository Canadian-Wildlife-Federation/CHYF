/*******************************************************************************
 * Copyright 2020 Government of Canada
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
 *******************************************************************************/

package net.refractions.chyf.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;
import java.util.function.Function;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTWriter;

public class IOUtil {

    public static <T> void saveGeometriesToTable(Collection<T> things, Function<T, ? extends Geometry> toGeom, String tableName, int srid, boolean truncateFirst) throws ClassNotFoundException, SQLException {
    	try {
	    	Class.forName("org.postgresql.Driver");
			Connection conn = DriverManager.getConnection(
					"jdbc:postgresql://hummingbird/chyf",
					"postgres", "postgres");
			WKTWriter wktw = new WKTWriter();
			
			if(truncateFirst) {
				Statement stmt = conn.createStatement();
				stmt.executeUpdate("TRUNCATE " + tableName + ";");
				stmt.close();
			}
			conn.setAutoCommit(false);
			PreparedStatement pStmt = conn.prepareStatement("INSERT INTO " + tableName + "(geom) VALUES(st_setSRID(st_geomFromText(?)," + srid +"))");
			int count = 0;
			for(T thing : things) {
				pStmt.setString(1, wktw.write(toGeom.apply(thing)));
				pStmt.addBatch();
				if(count % 100 == 0) {
					pStmt.executeBatch();
					conn.commit();
				}
			}
			pStmt.executeBatch();
			conn.commit();
			conn.close();
    	} catch(Exception e) {
    		System.err.println("Error saving to table");
    		e.printStackTrace();
    	}
	}
    
}