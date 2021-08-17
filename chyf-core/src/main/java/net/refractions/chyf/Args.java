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
package net.refractions.chyf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

/**
 * Class for parsing and validating input arguments for various CHyF tools.
 * 
 * @author Emily
 *
 */
public abstract class Args {

	final static Options options = new Options();
	
	protected String mainClass;
	
	public Args(String mainClass) {
		this.mainClass = mainClass;
		initOptions();
	}
	
	/**
	 * Initializes the various command line arguments.  Users should override if they
	 * have their own custom arguments.
	 */
	public void initOptions() {
		options.addOption("p", true, "custom properties file");
		options.addOption("c", true, "number of cores to use for multi-core processing (default 4)");
		options.addOption("g", false, "use geopackage data source");
		options.addOption("d", true, "use postgis data source");
		options.addOption("a", true, "if postgis is using this specifies the aoi to process; if not specified the next unprocessed aoi will be processed until no more to process");
	}
	
	protected String inData = null;
	protected String outData = null;
	protected Path prop = null;
	protected int cores = 4;

	protected boolean geopkg = false;
	protected boolean postgis = false;
	
	protected String dbstring = "";
	protected String aoi = "";
	
	/**
	 * Parses the command line arguments.  Returns true
	 * if parse completed successfully, false if error occurs.
	 * Will print usage before returning false if error occurs.
	 * 
	 * @param args
	 * @return
	 */
	public boolean parseArguments(String[] args) {
		
		try {
			CommandLineParser parser = new DefaultParser();
			CommandLine cmd = parser.parse( options, args);
			
			parseOptions(cmd);
			
			inData = null;
			outData = null;
			
			if (cmd.getArgList().size() == 2) {
				inData = cmd.getArgList().get(0);
				outData = cmd.getArgList().get(1);
			}else {
				printUsage();
				return false;
			}
			
			if (!validate()) return false;
			return true;
		}catch (Exception ex) {
			System.err.println(ex.getMessage());
			printUsage();
			return false;
		}
	}
	
	/**
	 * Parses command line arguments.  Users should override if they have custom 
	 * arguments.
	 * @param cmd
	 */
	protected void parseOptions(CommandLine cmd) {
		if (cmd.hasOption("p")) prop = Paths.get(cmd.getOptionValue("p"));
		if (cmd.hasOption("c")) cores = Integer.parseInt(cmd.getOptionValue("c"));
		if (cmd.hasOption("g")) geopkg = true;
		if (cmd.hasOption("d")) {
			postgis = true;
			dbstring = cmd.getOptionValue("d");
		}
		if (cmd.hasOption("a")) {
			aoi = cmd.getOptionValue("a").trim();
			if (aoi.isBlank()) aoi = null;
			
		}
	}
	
	
	protected boolean validate() {
		if (geopkg && postgis) {
			System.err.println("Can only specify one of geopkg OR postgis");
		}
		if (!geopkg && !postgis) {
			System.err.println("Must specify one of geopkg OR postgis");
		}
		if (geopkg) {
			Path inFile = Paths.get(inData);
			if (!Files.exists(inFile)) {
				System.err.println("Input file not found:" + inFile.toString());
				return false;
			}
		}
		if (postgis) {
			//parse host string
			String[] parts = dbstring.split(";");
			boolean hostfound = false;
			boolean dbfound = false;
			boolean userfound = false;
			for(String p : parts) {
				if (p.toLowerCase().startsWith("host=")) hostfound = true;
				if (p.toLowerCase().startsWith("db=")) dbfound = true;
				if (p.toLowerCase().startsWith("user=")) userfound = true;
			}
			if (!hostfound || !dbfound || !userfound) {
				System.err.println("Database connection string invalid. Must be of the form host=HOST;port=PORT;db=NAME;user=USERNAME;password=PASSWORD");
				return false;
			}
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
	 * @return if geopackage files are to be used
	 */
	public boolean isGeopackage() {
		return this.geopkg;
	}
	
	/**
	 * @return if postigs datasource to be used
	 */
	public boolean isPostigs() {
		return this.postgis;
	}
	
	/**
	 * if postgis is being used then this returns the aoi to process
	 * or null if just processes the next aoi in list
	 * @return
	 */
	public String getAoi() {
		return this.aoi;
	}
	
	/**
	 * @return the database connection string
	 */
	public String getDbConnectionString() {
		return this.dbstring;
	}
	
	
	/**
	 * 
	 * @return the number of cores argument
	 */
	public int getCores() {
		return this.cores;
	}
	
	/**
	 * 
	 * @return the input dataset argument (file or schema depending on datasource)
	 */
	public String getInput() {
		return this.inData;
	}
	
	/**
	 * 
	 * @return the output data argument (file or schema depending on datasource)
	 */
	public String getOutput() {
		return this.outData;
	}
	
	/**
	 * 
	 * @return the associated properties file or null if not provided
	 * @throws Exception
	 */
	public abstract IChyfProperties getPropertiesFile() throws Exception;
	
	public boolean hasAoi() {
		if (getAoi() == null || getAoi().isEmpty()) {
			return false;
		}
		return true;
	}
	private void printUsage() {
		new HelpFormatter().printHelp(mainClass + " [OPTIONS] <IN> <OUT>", options);
	}

}
