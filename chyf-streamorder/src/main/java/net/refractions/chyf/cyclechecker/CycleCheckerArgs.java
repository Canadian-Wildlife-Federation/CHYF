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
package net.refractions.chyf.cyclechecker;

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
public class CycleCheckerArgs {

	protected final static Options options = new Options();

	protected String mainClass;
	protected String dbstring = "";
	protected String outputTable = "";
	protected String inputSchema = "";
	
	public CycleCheckerArgs(String mainClass) {
		this.mainClass = mainClass;
		initOptions();
	}
	
	/**
	 * Initializes the various command line arguments.  Users should override if they
	 * have their own custom arguments.
	 */
	public void initOptions() {
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
				inputSchema = cmd.getArgList().get(0).trim().toLowerCase();
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
		if (cmd.hasOption("d")) {
			dbstring = cmd.getOptionValue("d");
		}
	}
	
	
	protected boolean validate() {
		if (!inputSchema.matches("[a-z0-9_]+")) {
			System.err.println("Invalid input schema table. The schema can only contain a-z0-9 characters");
			return false;
		}
		
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
	 * @return the name of the table to write results to
	 */
	public String getOutputTable() {
		return this.outputTable;
	}
	
	/**
	 * 
	 * @return the name of the input dataset schema
	 */
	public String getInputSchema() {
		return this.inputSchema;
	}
	
	private void printUsage() {
		new HelpFormatter().printHelp(mainClass + " [OPTIONS] [INPUT_SCHEMA] [OUTPUT_TABLE] ", options);
	}

}
