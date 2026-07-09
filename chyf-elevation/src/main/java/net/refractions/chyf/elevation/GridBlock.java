/*
 * Copyright 2026 Canadian Wildlife Federation
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
 */
package net.refractions.chyf.elevation;

import java.awt.image.BufferedImage;

import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.api.referencing.operation.TransformException;
import org.geotools.geometry.jts.JTS;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Coordinate;

/**
 * Wraps the elevation raster for a single block, providing bilinear
 * interpolated elevation lookups for coordinates within its bounds.
 *
 * @author Emily
 *
 */
public class GridBlock {

	private BufferedImage elevations;

	private ReferencedEnvelope gridEnv;
	private Block block;
	private MathTransform transform;

	public GridBlock(BufferedImage elevations, ReferencedEnvelope gridEnv, Block block)
			throws FactoryException {
		super();
		this.elevations = elevations;
		this.gridEnv = gridEnv;
		this.block = block;
		transform = CRS.findMathTransform(block.getBounds().getCoordinateReferenceSystem(), gridEnv.getCoordinateReferenceSystem());
	}

	public double getValue(Coordinate c) throws TransformException {
		// not within this grid; don't do anything with this coordinate
		if (!block.getBounds().contains(c))
			return c.z;
		
		Coordinate target = new Coordinate();
		JTS.transform(c, target, transform);

		double scalex = (gridEnv.getMaxX() - gridEnv.getMinX()) / elevations.getWidth();
		double scaley = (gridEnv.getMaxY() - gridEnv.getMinY()) / elevations.getHeight();

		// local pixel position, shifted -0.5 so an integer value means
		// "exactly on that cell's center"
		double px = (target.getX() - gridEnv.getMinX()) / scalex - 0.5;
		double py = (gridEnv.getMaxY() - target.getY()) / scaley - 0.5;

		int xindex1 = (int) Math.floor(px);
		int xindex2 = xindex1 + 1;
		int yindex1 = (int) Math.floor(py);
		int yindex2 = yindex1 + 1;

		if (xindex1 < 0 || xindex2 < 0)
			return ElevationEngine.NO_DATA;
		if (yindex1 < 0 || yindex2 < 0)
			return ElevationEngine.NO_DATA;
		if (xindex1 >= elevations.getWidth() || xindex2 >= elevations.getWidth())
			return ElevationEngine.NO_DATA;
		if (yindex1 >= elevations.getHeight() || yindex2 >= elevations.getHeight())
			return ElevationEngine.NO_DATA;

		double zx1y1 = elevations.getRaster().getSampleDouble(xindex1, yindex1, 0);
		double zx1y2 = elevations.getRaster().getSampleDouble(xindex1, yindex2, 0);
		double zx2y1 = elevations.getRaster().getSampleDouble(xindex2, yindex1, 0);
		double zx2y2 = elevations.getRaster().getSampleDouble(xindex2, yindex2, 0);

		double x1 = gridEnv.getMinX() + (xindex1 + 0.5) * scalex;
		double x2 = gridEnv.getMinX() + (xindex2 + 0.5) * scalex;
		double y1 = gridEnv.getMaxY() - (yindex1 + 0.5) * scaley;
		double y2 = gridEnv.getMaxY() - (yindex2 + 0.5) * scaley;

		// bilinear interpolation of elevation
		double fxy1 = ((x2 - target.getX()) / (x2 - x1)) * zx1y1 + ((target.getX() - x1) / (x2 - x1)) * zx2y1;
		double fxy2 = ((x2 - target.getX()) / (x2 - x1)) * zx1y2 + ((target.getX() - x1) / (x2 - x1)) * zx2y2;
		double fxy = ((y2 - target.getY()) / (y2 - y1)) * fxy1 + ((target.getY() - y1) / (y2 - y1)) * fxy2;

		//round to 4 decimal places
		fxy = Math.round(fxy * 10000) / 10000.0;
		return fxy;
	}

}
