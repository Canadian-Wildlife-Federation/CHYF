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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;

/**
 * class for parsing and validation input arguments for various tools
 * @author Emily
 *
 */
public class Args {

	final static Options options = new Options();
	static {
		options.addOption("p", true, "custom properties file");
		options.addOption("c", true, "number of cores to use for multi-core processing (default 4)");
	}
	
	/**
	 * Parses arguments of the form [-p prop.properties] input output
	 * @param args
	 * @return
	 * @throws ParseException 
	 */
	public static Args parseArguments(String[] args, String main) {
	
		try {
			CommandLineParser parser = new DefaultParser();
			CommandLine cmd = parser.parse( options, args);
					
			String props = null;
			String cores = null;
			if (cmd.hasOption("p")) props = cmd.getOptionValue("p");
			if (cmd.hasOption("c")) cores = cmd.getOptionValue("c");
			
			String infile = null;
			String outfile = null;
			
			if (cmd.getArgList().size() == 2) {
				infile = cmd.getArgList().get(0);
				outfile = cmd.getArgList().get(1);
			}else {
				printUsage(main);
				return null;
				//THROW EXCEPTION
			}
			Args item = new Args(infile, outfile, props, cores);
			
			if (!item.validate()) return null;
			return item;
		}catch (Exception ex) {
			System.err.println(ex.getMessage());
			printUsage(main);
			return null;
		}
	}

	private Path inFile = null;
	private Path outFile = null;
	private Path prop = null;
	private int cores = 4;
	

	private Args(String inFile, String outFile, String prop, String cores) {
		this.inFile = Paths.get(inFile);
		this.outFile = Paths.get(outFile);
		if (cores != null) this.cores = Integer.parseInt(cores);
		if (prop  != null) this.prop = Paths.get(prop);
	}
	
	private boolean validate() {
		if (!Files.exists(inFile)) {
			System.err.println("Input file not found:" + inFile.toString());
			return false;
		}
		
		if (prop != null) {
			if (!Files.exists(prop)) {
				System.err.println("Properties file not found:" + prop.toString());
				return false;
			}
		}
		
		if (cores < 0 || cores > 100) {
			System.err.println("Invalid number of cores.  Must be a positive number less than 100.");
			return false;
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
	
	public int getCores() {
		return this.cores;
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
	
	private static void printUsage(String main) {
		new HelpFormatter().printHelp(main + " [OPTIONS] <INFILE> <OUTFILE>", options);
	}

}
