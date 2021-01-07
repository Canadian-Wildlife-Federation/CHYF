/*
 * Copyright 2021 Canadian Wildlife Federation.
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
package net.refractions.chyf;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File based vector tile cache
 * 
 * @author Emily
 *
 */
public class FileVectorTileCache {

	static final Logger logger = LoggerFactory.getLogger(FileVectorTileCache.class.getCanonicalName());

	private Path root;
	
	public FileVectorTileCache() {
		
	}
	
	public void setCacheLocation(Path root) {
		this.root = root;
	}

	public Path getVectorTile(int z, int x, int y, VectorTileLayer layer) {
		if (this.root == null) return null;
		
		Path p = getFilePath(z, x, y, layer);
		if (!Files.exists(p)) return null;
		return p;
	}
	
	public void writeVectorTile(int z, int x, int y, VectorTileLayer layer, byte[] tile) {
		if (this.root == null) return;
		
		Path p = getFilePath(z, x, y, layer);
		try {
			if (!Files.exists(p.getParent())) Files.createDirectories(p.getParent());
			
			try(OutputStream os = Files.newOutputStream(p)){
				os.write(tile);
			}
		}catch (IOException ex) {
			logger.warn("Unable to save tile to tile cache: " + ex.getMessage(), ex);
		}
		
	}
	
	private Path getFilePath(int z, int x, int y, VectorTileLayer layer) {
		return root.resolve(layer.name()).resolve(String.valueOf(z))
				.resolve(String.valueOf(x))
				.resolve(String.valueOf(y) + ".mvt");
	}
}
