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
package net.refractions.chyf.watershed.builder;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridEnvelope2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.coverage.grid.io.AbstractGridFormat;
import org.geotools.coverage.grid.io.OverviewPolicy;
import org.geotools.data.DataSourceException;
import org.geotools.gce.geotiff.GeoTiffReader;
import org.geotools.geometry.Envelope2D;
import org.geotools.geometry.GeneralEnvelope;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.index.strtree.STRtree;
import org.opengis.coverage.grid.GridCoordinates;
import org.opengis.coverage.grid.GridEnvelope;
import org.opengis.geometry.DirectPosition;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValue;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.util.ReprojectionUtils;

public class GeoTiffDirReader {
	static final Logger logger = LoggerFactory.getLogger(GeoTiffDirReader.class);

	STRtree index;
	CoordinateReferenceSystem crs;

	public GeoTiffDirReader(String path, CoordinateReferenceSystem crs) {
		this.crs = crs;
		File dir = new File(path);
		if (!dir.isDirectory()) {
			throw new RuntimeException("Path '" + path + "' is not a directory");
		}
		File[] files = dir.listFiles(new FilenameFilter() {
			Pattern p = Pattern.compile(".*\\.(g(eo)?)?tiff?$");

			@Override
			public boolean accept(File dir, String name) {
				if (p.matcher(name).matches())
					return true;
				return false;
			}
		});
		index = new STRtree();
		for (File f : files) {
			try {
				GeoTiffReader reader = new GeoTiffReader(f);
				GeneralEnvelope env = reader.getOriginalEnvelope();
				ReferencedEnvelope rEnv = ReprojectionUtils.reproject(new ReferencedEnvelope(env), crs);
				index.insert(rEnv, reader);
			} catch (DataSourceException e) {
				logger.warn("File '" + f.getName() + "' looked like a GeoTiff but wasn't, ignoring.");
			}
		}
		index.build();
	}

	List<Coordinate> getDEM(Envelope env) {
		@SuppressWarnings("unchecked")
		List<GeoTiffReader> readers = index.query(env);
		List<Coordinate> coords = new ArrayList<Coordinate>();
		for (GeoTiffReader r : readers) {
			getDEM(r, env, coords);
		}
		return coords;
	}

	private void getDEM(GeoTiffReader reader, Envelope env, List<Coordinate> coords) {
		ParameterValue<OverviewPolicy> policy = AbstractGridFormat.OVERVIEW_POLICY.createValue();
		policy.setValue(OverviewPolicy.IGNORE);

		// this will basically read 4 tiles worth of data at once from the disk...
		ParameterValue<String> tileSize = AbstractGridFormat.SUGGESTED_TILE_SIZE.createValue();

		// Setting read type: use JAI ImageRead (true) or ImageReaders read methods
		// (false)
		ParameterValue<Boolean> useJaiRead = AbstractGridFormat.USE_JAI_IMAGEREAD.createValue();
		useJaiRead.setValue(true);
		GridEnvelope dimensions = reader.getOriginalGridRange();
		GridCoordinates maxDimensions = dimensions.getHigh();
		int w = maxDimensions.getCoordinateValue(0);
		int h = maxDimensions.getCoordinateValue(1);

		try {
			GridCoverage2D coverage = reader.read(new GeneralParameterValue[] { policy, tileSize, useJaiRead });
			GridGeometry2D gridGeometry = coverage.getGridGeometry();
			
			ReferencedEnvelope rEnv = ReprojectionUtils.reproject(new ReferencedEnvelope(env, crs), coverage.getCoordinateReferenceSystem2D());
			GridEnvelope2D gridEnv = gridGeometry.worldToGrid(new Envelope2D(rEnv));

			int minX = Math.max(0, (int) Math.round(Math.floor(gridEnv.getMinX())));
			int minY = Math.max(0, (int) Math.round(Math.floor(gridEnv.getMinY())));
			int maxX = Math.min(w, (int) Math.round(Math.ceil(gridEnv.getMaxX())));
			int maxY = Math.min(h, (int) Math.round(Math.ceil(gridEnv.getMaxY())));
			
			double[] vals = new double[1];
			for (int i = minX; i <= maxX; i++) {
				for (int j = minY; j <= maxY; j++) {
					DirectPosition dp = gridGeometry.gridToWorld(new GridCoordinates2D(i, j));
					Point p = ReprojectionUtils.reproject(JTS.toGeometry(dp), dp.getCoordinateReferenceSystem(), crs);
					if (env.contains(p.getCoordinate())) {
						coverage.evaluate(dp, vals);
						Coordinate c = new Coordinate(p.getX(), p.getY(), vals[0]);
						coords.add(c);
					}
				}
			}
		} catch (IOException | TransformException e) {
			throw new RuntimeException(e);
		}
	}
}
