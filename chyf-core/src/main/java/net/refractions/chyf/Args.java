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
import java.nio.file.StandardCopyOption;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;

import net.refractions.chyf.datasource.ChyfGeoPackageDataSource;

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
	}
	
	protected Path inFile = null;
	protected Path outFile = null;
	protected Path prop = null;
	protected int cores = 4;

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
			
			String infile = null;
			String outfile = null;
			
			if (cmd.getArgList().size() == 2) {
				infile = cmd.getArgList().get(0);
				outfile = cmd.getArgList().get(1);
			}else {
				printUsage();
				return false;
			}
			
			this.inFile = Paths.get(infile);
			this.outFile = Paths.get(outfile);
			
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
	}
	
	
	protected boolean validate() {
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
	 * 
	 * @throws Exception
	 */
	public void prepareOutput() throws Exception{
		ChyfGeoPackageDataSource.deleteOutputFile(getOutput());
		Files.copy(getInput(), getOutput(), StandardCopyOption.REPLACE_EXISTING);
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
	 * @return the input dataset argument
	 */
	public Path getInput() {
		return this.inFile;
	}
	
	/**
	 * 
	 * @return the output data argument
	 */
	public Path getOutput() {
		return this.outFile;
	}
	
	/**
	 * 
	 * @return the associated properties file or null if not provided
	 * @throws Exception
	 */
	public abstract IChyfProperties getPropertiesFile() throws Exception;
	
	private void printUsage() {
		new HelpFormatter().printHelp(mainClass + " [OPTIONS] <INFILE> <OUTFILE>", options);
	}

}
