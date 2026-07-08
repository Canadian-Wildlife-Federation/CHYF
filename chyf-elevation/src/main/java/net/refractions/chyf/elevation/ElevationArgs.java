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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

/**
 * Class for parsing and validating input arguments for elevation processor.
 * 
 * @author Emily
 *
 */
public class ElevationArgs {

	protected final static Options options = new Options();

	protected String mainClass;
	protected Boolean isCont = null;
	protected String dbstring = "";
	
	protected String propertiesFile = null;
	
	public ElevationArgs(String mainClass) {
		this.mainClass = mainClass;
		initOptions();
	}
	
	/**
	 * Initializes the various command line arguments.  Users should override if they
	 * have their own custom arguments.
	 */
	public void initOptions() {
		options.addOption("docontinue", false, "continue processing where left off; does not create initial config table");
		options.addOption("d", true, "postgis data source connection string");
	}
	
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
			
			if (cmd.getArgList().size() == 1) {
				propertiesFile = cmd.getArgList().get(0).trim().toLowerCase();
			}else {
				printUsage();
				return false;
			}
			
			if (!validate()) {
				printUsage();
				return false;
			}
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
		this.isCont = false;
		if (cmd.hasOption("docontinue")) this.isCont = true;
		if (cmd.hasOption("d")) {
			dbstring = cmd.getOptionValue("d");
		}
	}
	
	
	protected boolean validate() {
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
		return true;
	}

	/**
	 * @return the database connection string
	 */
	public String getDbConnectionString() {
		return this.dbstring;
	}
	
	/**
	 * 
	 * @return the name of the flowpath table to read/update
	 */
	public String getPropertiesFile() {
		return this.propertiesFile;
	}
	
	
	/**
	 * 
	 * @return the name of the table to write results to
	 */
	public Boolean getDoContinue() {
		return this.isCont;
	}
	
	
	
	private void printUsage() {
		new HelpFormatter().printHelp(mainClass + " [OPTIONS] elevation.properties", options);
	}

}
