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

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URI;

import javax.imageio.ImageIO;
import javax.imageio.metadata.IIOMetadata;

import org.geotools.api.referencing.operation.MathTransform;
import org.geotools.coverage.grid.io.imageio.geotiff.GeoTiffIIOMetadataDecoder;
import org.geotools.coverage.grid.io.imageio.geotiff.PixelScale;
import org.geotools.coverage.grid.io.imageio.geotiff.TiePoint;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.referencing.CRS;
import org.geotools.referencing.operation.transform.ProjectiveTransform;

import it.geosolutions.imageio.core.BasicAuthURI;
import it.geosolutions.imageio.plugins.cog.CogImageReadParam;
import it.geosolutions.imageioimpl.plugins.cog.CogImageInputStream;
import it.geosolutions.imageioimpl.plugins.cog.CogImageInputStreamSpi;
import it.geosolutions.imageioimpl.plugins.cog.CogImageReader;
import it.geosolutions.imageioimpl.plugins.cog.CogImageReaderSpi;
import it.geosolutions.imageioimpl.plugins.cog.HttpRangeReader;

/**
 * Standalone scratch/example for reading a region of a cloud-optimized
 * GeoTIFF (COG) DEM and writing it out to a local file.
 *
 * @author Emily
 *
 */
public class ElevationReader {

	    public static void main(String[] args) throws Exception {

	        // Path to the COG
	        // Chemin vers le fichier COG
	        String cogPath = "https://datacube-prod-data-public.s3.ca-central-1.amazonaws.com/store/elevation/mrdem/mrdem-30/mrdem-30-dtm.tif";

	        cogPath = "https://canelevation-dem.s3.ca-central-1.amazonaws.com/mrdem-30/mrdem-30-dtm.tif";
	        
	        BasicAuthURI cogUri = new BasicAuthURI(URI.create(cogPath), false);

	        //# AOI bounds in EPSG:3979 (Canada Atlas Lambert): (min_x, min_y, max_x, max_y)
	        //aoi_bounds = (1774874, -89162, 1818832, -52305)
	        
	        CogImageReadParam param = new CogImageReadParam();
	        param.setRangeReaderClass(HttpRangeReader.class);
//	        param.setSourceRegion(new Rectangle(0, 0, 10, 10));
	        


	        CogImageInputStream cogStream = (CogImageInputStream) new CogImageInputStreamSpi().createInputStreamInstance(cogUri, true, null);
	        cogStream.init(param);
	        CogImageReader reader = new CogImageReader(new CogImageReaderSpi());
	        
	        reader.setInput(cogStream);
	        
	        IIOMetadata metadata = reader.getImageMetadata(0);
	        GeoTiffIIOMetadataDecoder decoder = new GeoTiffIIOMetadataDecoder(metadata);
	        
	        String projectedCode = "EPSG:"+ decoder.getGeoKey(3072);  // ProjectedCRSGeoKey

	        
	        PixelScale pixelScale = decoder.getModelPixelScales();
	        TiePoint[] tiePoints  = decoder.getModelTiePoints();
	        double scaleX  =  pixelScale.getScaleX();
	        double scaleY  = -pixelScale.getScaleY(); // negative — Y flips from image to geo
	        double originX =  tiePoints[0].getValueAt(3); // geo X of top-left pixel
	        double originY =  tiePoints[0].getValueAt(4); // geo Y of top-left pixel
	        double tpPixelI = tiePoints[0].getValueAt(0);
	        double tpPixelJ = tiePoints[0].getValueAt(1);
	        
	        AffineTransform gridToWorld = new AffineTransform(scaleX, 0, 0, scaleY,originX - tpPixelI * scaleX, originY - tpPixelJ * scaleY);
	        MathTransform worldToGrid = ProjectiveTransform.create(gridToWorld).inverse();
	        	        
	        ReferencedEnvelope re = new ReferencedEnvelope(1774875,1818832,-89162,-52305,CRS.decode("EPSG:3979"));
	        re = re.transform(CRS.decode(projectedCode), false);      

	        double[] worldCoords = {re.getMinX(), re.getMinY(),re.getMaxX(), re.getMaxY()};
	        double[] pixelCoords = new double[4];
	        worldToGrid.transform(worldCoords, 0, pixelCoords, 0, 2);

	        // GeoTIFF Y-axis is inverted vs image row order, so min/max need sorting
	        int pixelMinX = (int) Math.floor(Math.min(pixelCoords[0], pixelCoords[2]));
	        int pixelMinY = (int) Math.floor(Math.min(pixelCoords[1], pixelCoords[3]));
	        int pixelMaxX = (int)  Math.ceil(Math.max(pixelCoords[0], pixelCoords[2]));
	        int pixelMaxY = (int)  Math.ceil(Math.max(pixelCoords[1], pixelCoords[3]));

	        Rectangle r = new Rectangle(
	            pixelMinX, pixelMinY,
	            pixelMaxX - pixelMinX,
	            pixelMaxY - pixelMinY
	        );
	       
	        param.setSourceRegion(r);
      
	        
	        BufferedImage cogImage = reader.read(0, param);
	        
	        reader.dispose();
	        
	        File output = new File("C:\\temp\\java_output.tif");
	        ImageIO.write(cogImage, "tiff", output);

	        System.out.println("Written to: " + output.getAbsolutePath());
	    }
	
}