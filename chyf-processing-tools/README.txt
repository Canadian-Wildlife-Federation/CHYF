----------------------------------------------
CHyF Processing Tools
----------------------------------------------
This application provides tools for processing hydrological datasets 
generating a reuslting dataset that meets CHyF requirements.  This package
includes tools to generate skeletons for waterbodies, directionalize datasets,
compute rank, and generate catchments.

More details can be found in the 
CHyF Tools: Flowpath Constructor and Catchment Delineator
document.

----------------------------------------------
--- Requirements ---
----------------------------------------------
Java version 11 or newer must be installed and included on
the path.  To test "run java -version" from a command line.

---------------------------------------
--- Sample Data ---
---------------------------------------
A sample dataset exist in the packaged application that can
be used for testing the running of the application.

* testdata/Richelieu.32618.gpkg


---------------------------------------
--- Running ---
---------------------------------------
On windows use the .bat files, on linux use the .sh files.


---------------------------------------
--- Flowpath Constructor ---
---------------------------------------
To run the flowpath constructor use the flowpath-constructor file.  For example to
run on the sample data provided use the following command:

flowpath-constructor.bat ./testdata/Richelieu.32618.gpkg ./testdata/out.gpkg

To run the bank only constructor use the bank-only-constructor file.  For example:

bank-only-constructor.bat ./testdata/Richelieu.32618.gpkg ./testdata/out.gpkg

Custom property files can be provided using the -p option.  If you have multiple 
cores you can specify the number of cores to use using the -c option.

Logging is written to the console.  In addition warnings and errors are written to a warnings.log file.

--- Advanced ---
These tools contain 5 separate processes:

1. Construction Point Generator - This tool generates a set of construction points on the boundary
of each polygonal waterbody.  These points represent
location where water enters or exists the waterbody, and are used
as input into the next process - the skeletonizer.  

2. Skeletonizer - This tool generated skeleton lines within a waterbody 
connecting the construction points generated in step 1.

3. Directionalizer - Directionalizes all skeletons and other undirectionalized
eflowpath features.

4. Rank Generator - Computes rank (primary and secondary flows) for each
eflowpath feature.

5. Bank Flowpath Generator - This tool uses existing skeletons and waterbody and
generates only bank flowpaths.



Each of these tools can be run independently as follows, modifying as required for your operating system.

1. Construction Point Generator
java -cp lib/* net.refractions.chyf.flowpathconstructor.skeletonizer.points.PointEngine [OPTIONS] <INFILE> <OUTFILE>

2. Skeletonizer
java -cp lib/* net.refractions.chyf.flowpathconstructor.skeletonizer.voronoi.SkeletonEngine [OPTIONS] <INFILE> <OUTFILE>

3.  Directionalizer
java -cp lib/* net.refractions.chyf.flowpathconstructor.directionalize.DirectionalizeEngine [OPTIONS] <INFILE> <OUTFILE>

4.  Rank Generator 
java -cp lib/* net.refractions.chyf.flowpathconstructor.rank.RankEngine [OPTIONS] <INFILE> <OUTFILE>

5.  Bank Flowpath Generator
java -cp lib/*  net.refractions.chyf.flowpathconstructor.skeletonizer.bank.Bank[OPTIONS] <INFILE> <OUTFILE>


---------------------------------------
--- Catchment Delineator ---
---------------------------------------
To run the catchment delineator use the catchment-delineator file.  For example to
run on the sample data provided use the following command:

catchment-delineator.bat ./testdata/Richelieu.32618.gpkg ./testdata/out.gpkg

Custom property files can be provided using the -p option.  If you have multiple 
cores you can specify the number of cores to use using the -c option.

