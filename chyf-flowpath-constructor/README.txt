----------------------------------------------
CHyF EFlowpath Constructor
----------------------------------------------
This application provides tools for processing hydrological datasets 
generating a reuslting dataset that meets CHyF requirements.  This package
includes tools to generate skeletons for waterbodies, directionalize dataset and
compute rank. 

More details can be found in the 
CHyF Tools: Flowpath Constructor and Catchment Delineator
document.

----------------------------------------------
--- Requirements ---
----------------------------------------------
Java version 11 or newer must be installed and included on
the path.  To test "run java -version" from a command line.


---------------------------------------
--- Running ---
---------------------------------------
On windows use the .bat files, on linux use the .sh files.

To run the flowpath constructor use the flowpath-constructor file.  For example to
run on the sample data provided use the following command:

flowpath-constructor.bat ./testdata/Richelieu.32618.gpkg ./testdata/out.gpkg

To run the bank only constructor use the bank-only-constructor file.  For example:

bank-only-constructor.bat ./testdata/Richelieu.32618.gpkg ./testdata/out.gpkg


Custom property files can be provided using the -p option.  If you have multiple 
cores you can specify the number of cores to use using the -c option.

Logging is written to the console.  In addition warnings and errors are written to a warnings.log file.

---------------------------------------
--- Sample Data ---
---------------------------------------
A sample dataset exist in the packaged application that can
be used for testing the running of the application.

* testdata/Richelieu.32618.gpkg

