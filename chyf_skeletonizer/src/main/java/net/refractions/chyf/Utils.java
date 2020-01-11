package net.refractions.chyf;

public class Utils {

	/**
	 * Parses arguments of the form [-p prop.properties] input output
	 * @param args
	 * @return
	 */
	public static String[] parseArguments(String[] args) {
		if (args.length < 2) return null;
		if (args.length > 3) return null;
		
		if (args[0].startsWith("-p=")) {
			if (args.length != 3) return null;
			String props = args[0].substring(3);
			
			String in = args[1];
			String out = args[2];
			return new String[] {in, out, props};
		}else {
			if (args.length != 2) return null;
			String in = args[0];
			String out = args[1];
			return new String[] {in, out, null};
		}
	}

	public static void printUsage(String main) {
		System.err.println("usage: " + main + " [-p=prop.properties] infile outfile");
		System.err.println("   infile               the input geopackage file");
		System.err.println("   outfile              the output geopackage file, will be overwritten");
		System.err.println("   -p=prop.properties   a properties file describing the parameters to use for the program");
	}
	
	public static void main(String args[]) {
		String[] out = parseArguments(args);
		if (out == null) printUsage("TEST");
			System.out.println(out[0]);
			System.out.println(out[1]);
			System.out.println(out[2]);
	}
}
