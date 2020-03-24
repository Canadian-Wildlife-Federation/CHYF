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
package net.refractions.chyf.watershed.builder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * Class for parsing and validation of input arguments
 */
public class Args {

	final static Options options = new Options();
	static {
		options.addOption("p", true, "custom properties file");
		options.addOption("c", true, "number of cores to use for multi-core processing (default 1)");
		options.addOption("r", false, "recover/continue previous output (output file must exist)");
	}

	private Path inFile = null;
	private Path tiffDir = null;
	private Path outFile = null;
	private Path propertiesFile = null;
	private int cores = 1;
	private boolean recover = false;
	

	/**
	 * Parses arguments of the form [-p prop.properties] input output
	 * @param args
	 * @return
	 * @throws ParseException 
	 */
	public static Args parseArguments(String[] inArgs, String main) {
	
		try {
			CommandLineParser parser = new DefaultParser();
			CommandLine cmd = parser.parse( options, inArgs);
			
			Args args = new Args();
			
			if (cmd.hasOption("p")) {
				args.propertiesFile = Paths.get(cmd.getOptionValue("p"));
				if(!Files.exists(args.propertiesFile)) {
					throw new ParseException("Properties file not found: " + args.propertiesFile);
				}
			}
			if (cmd.hasOption("c")) {
				args.cores = Integer.parseInt(cmd.getOptionValue("c"));
				if (args.cores < 0 || args.cores > 100) {
					throw new ParseException("Invalid number of cores.  Must be a positive number less than 100.");
				}
			}
			
			if(cmd.hasOption("r")) {
				args.recover = true;
			}


			if (cmd.getArgList().size() == 3) {
				args.inFile = Paths.get(cmd.getArgList().get(0));
				if(!Files.exists(args.inFile)) {
					throw new ParseException("Input file not found: " + args.inFile);
				}
				args.tiffDir = Paths.get(cmd.getArgList().get(1));
				if(!Files.isDirectory(args.tiffDir)) {
					throw new ParseException("Tiff directory does not exist: " + args.tiffDir);
				}
				args.outFile = Paths.get(cmd.getArgList().get(2));
				if(args.recover && !Files.exists(args.outFile)) {
					throw new ParseException("Output file not found for recovery: " + args.outFile);
				}
			} else {
				throw new ParseException("Incorrect number of arguments, must be 3: INFILE, TIFFDIR, OUTFILE");
			}
			return args;
			
		} catch (ParseException | NumberFormatException e) {
			System.err.println(e.getMessage());
			printUsage(main);
			return null;
		}
	}


	private Args() {
	}
	
	public int getCores() {
		return cores;
	}
	
	public Path getInput() {
		return inFile;
	}
	
	public Path getOutput() {
		return outFile;
	}

	public Path getTiffDir() {
		return tiffDir;
	}

	public Path getPropertiesFile() {
		return propertiesFile;
	}

	public boolean getRecover() {
		return recover;
	}

	private static void printUsage(String main) {
		new HelpFormatter().printHelp(main + " [OPTIONS] <INFILE> <TIFFDIR> <OUTFILE>", options);
	}

}
