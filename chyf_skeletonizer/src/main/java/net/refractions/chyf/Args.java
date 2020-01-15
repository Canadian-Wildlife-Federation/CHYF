/*
 * Copyright 2019 Government of Canada
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;

/**
 * class for parsing and validation input arguments for various tools
 * @author Emily
 *
 */
public class Args {

	/**
	 * Parses arguments of the form [-p prop.properties] input output
	 * @param args
	 * @return
	 */
	public static Args parseArguments(String[] args) {
		if (args.length < 2) return null;
		if (args.length > 3) return null;
		
		Args item = null;
		if (args[0].startsWith("-p=")) {
			if (args.length != 3) return null;
			String props = args[0].substring(3);
			
			String in = args[1];
			String out = args[2];
			item = new Args(in, out, props);
		}else {
			if (args.length != 2) return null;
			String in = args[0];
			String out = args[1];
			item = new Args(in, out, null);
		}
		if (!item.validate()) return null;
		return item;
	}

	private Path inFile = null;
	private Path outFile = null;
	private Path prop = null;
	
	private Args(String inFile, String outFile, String prop) {
		this.inFile = Paths.get(inFile);
		this.outFile = Paths.get(outFile);
		if (prop  != null) this.prop = Paths.get(prop);
	}
	
	private boolean validate() {
		if (!Files.exists(inFile)) {
			System.err.println("Input file not found:" + inFile.toString());
			return false;
		}
		
		if (prop != null) {
			if (!Files.exists(prop)) {
				System.err.println("Properties file not found:" + inFile.toString());
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Deletes any existing output files and copies the input
	 * file to the output file
	 * @throws Exception
	 */
	public void prepareOutput() throws Exception{
		ChyfGeoPackageDataSource.deleteOutputFile(getOutput());
		Files.copy(getInput(), getOutput(), StandardCopyOption.REPLACE_EXISTING);
	}
	
	public Path getInput() {
		return this.inFile;
	}
	public Path getOutput() {
		return this.outFile;
	}
	public ChyfProperties getPropertiesFile() throws Exception {
		if (this.prop == null) return null;
		return ChyfProperties.getProperties(this.prop);
	}
	
	public static void printUsage(String main) {
		System.err.println("usage: " + main + " [-p=prop.properties] infile outfile");
		System.err.println("   infile               the input geopackage file");
		System.err.println("   outfile              the output geopackage file, will be overwritten");
		System.err.println("   -p=prop.properties   a properties file describing the parameters to use for the program");
	}

}
