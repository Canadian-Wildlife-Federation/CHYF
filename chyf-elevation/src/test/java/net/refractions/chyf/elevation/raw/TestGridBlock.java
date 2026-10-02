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
package net.refractions.chyf.elevation.raw;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;

import net.refractions.chyf.elevation.Utils;

/**
 * Tests the elevation lookups of a {@link GridBlock} against a 4x4 grid of
 * one unit cells covering (0,0) to (4,4).  The cell in column c and row r
 * (row 0 is the top row) has its center at (c + 0.5, 3.5 - r) and an
 * elevation of 100 + 10c + r:
 *
 * <pre>
 *        x=0.5  x=1.5  x=2.5  x=3.5
 * y=3.5   100    110    120    130
 * y=2.5   101    111    121    131
 * y=1.5   102    112    122    132
 * y=0.5   103    113    123    133
 * </pre>
 *
 * @author Emily
 *
 */
class TestGridBlock {

	private static final int SIZE = 4;

	/**
	 * A coordinate exactly on a cell center gets the elevation of that cell.
	 */
	@Test
	void testValueOnCellCenter() throws Exception {

		GridBlock grid = createGrid(-1, -1);

		Assertions.assertEquals(111, grid.getValue(new Coordinate(1.5, 2.5)), 0.00001);
		Assertions.assertEquals(122, grid.getValue(new Coordinate(2.5, 1.5)), 0.00001);
	}

	/**
	 * A coordinate between cell centers gets the bilinear interpolation of
	 * the four cells around it.
	 */
	@Test
	void testValueInterpolatedBetweenCells() throws Exception {

		GridBlock grid = createGrid(-1, -1);

		//the middle of cells 111, 121, 112 and 122
		Assertions.assertEquals(116.5, grid.getValue(new Coordinate(2.0, 2.0)), 0.00001);

		//a quarter of the way from cell 111 to cell 121
		Assertions.assertEquals(113.5, grid.getValue(new Coordinate(1.75, 2.5)), 0.00001);
	}

	/**
	 * A coordinate that does not have four cell centers around it is
	 * outside of the grid and has no data.  This includes the outer half
	 * cell around the edge of the grid.
	 */
	@Test
	void testValueOutsideGridIsNoData() throws Exception {

		GridBlock grid = createGrid(-1, -1);

		Assertions.assertEquals(Utils.NO_DATA, grid.getValue(new Coordinate(-1.0, 2.0)), 0);
		Assertions.assertEquals(Utils.NO_DATA, grid.getValue(new Coordinate(5.0, 2.0)), 0);
		Assertions.assertEquals(Utils.NO_DATA, grid.getValue(new Coordinate(2.0, -1.0)), 0);
		Assertions.assertEquals(Utils.NO_DATA, grid.getValue(new Coordinate(2.0, 5.0)), 0);

		//inside the grid but in the outer half cell
		Assertions.assertEquals(Utils.NO_DATA, grid.getValue(new Coordinate(0.2, 2.0)), 0);
		Assertions.assertEquals(Utils.NO_DATA, grid.getValue(new Coordinate(2.0, 3.8)), 0);
	}

	/**
	 * A coordinate whose elevation is interpolated from a NaN cell has no
	 * data; the NaN must not be rounded to an elevation of 0.  Coordinates
	 * that do not use the NaN cell are not affected by it.
	 */
	@Test
	void testNaNCellIsNoData() throws Exception {

		//the cell in column 2, row 2 (122) is NaN
		GridBlock grid = createGrid(2, 2);

		//the middle of cells 111, 121, 112 and NaN
		Assertions.assertEquals(Utils.NO_DATA, grid.getValue(new Coordinate(2.0, 2.0)), 0);

		//on the center of the NaN cell
		Assertions.assertEquals(Utils.NO_DATA, grid.getValue(new Coordinate(2.5, 1.5)), 0);

		//the middle of cells 100, 110, 101 and 111
		Assertions.assertEquals(105.5, grid.getValue(new Coordinate(1.0, 3.0)), 0.00001);
	}

	/**
	 * Builds the 4x4 grid described in the class comment.
	 *
	 * @param nanCol column of the cell to set to NaN, or -1 for none
	 * @param nanRow row of the cell to set to NaN, or -1 for none
	 */
	private GridBlock createGrid(int nanCol, int nanRow) throws Exception {

		WritableRaster raster = Raster.createWritableRaster(
				new BandedSampleModel(DataBuffer.TYPE_FLOAT, SIZE, SIZE, 1), null);

		for (int col = 0; col < SIZE; col++) {
			for (int row = 0; row < SIZE; row++) {
				float z = 100 + 10 * col + row;
				if (col == nanCol && row == nanRow) z = Float.NaN;
				raster.setSample(col, row, 0, z);
			}
		}

		ColorModel colorModel = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY),
				false, false, Transparency.OPAQUE, DataBuffer.TYPE_FLOAT);
		BufferedImage image = new BufferedImage(colorModel, raster, false, null);

		//the block and the grid are in the same crs so no transform is applied
		CoordinateReferenceSystem crs = DefaultGeographicCRS.WGS84;
		ReferencedEnvelope gridEnv = new ReferencedEnvelope(0, SIZE, 0, SIZE, crs);

		return new GridBlock(image, gridEnv, crs);
	}

}
