----------------------------------------------
CHyF Elevation
----------------------------------------------
This application provides tools for adding elevation and smoothed
elevation to the CHyF flowpath edges.

This software was developed by Refractions Research (www.refractions.net) for
the Canadian Wildlife Federation

----------------------------------------------
--- Requirements ---
----------------------------------------------
Java version 25 must be installed and included on
the path.  To test "run java -version" from a command line.

A PostgreSQL database with data structured that matches the CHyF2 
data schema. 

---------------------------------------
--- Running ---
---------------------------------------
On windows use the .bat files, on linux use the .sh files.

It creates the table public.elevation_processing in the database to 
track processing. If this table already exists it drops it and re-creates it (unless
the docontinue flag is used - see below).

---------------------------------------
--- Elevation - 3D values  ---
---------------------------------------
To run the delevation tools use the chyf-elevation files. For example:

chyf-elevation.bat -d host=<host>;port=<port>;db=<db>;user=<user>;password=<pass> <properties_file>


Usage:
chyf-streamorder-computer.bat -d <connectionstring>  -docontinue elevation.properties

connectionstring
The database connection string in the form "host=<host>;port=<port>;db=<db>;user=<user>;password=<pass>"

docontinue 
Optional.  The software processes the data in blocks, status tracked in the database. 
If you do not provide this flag it assumes it is the first time running the software 
and sets up the blocks for processing.  If you provide this flag the software just finds
the next block not processed and starts processing.

properties_file
A file that contains the properties for processing. This file is described below.

---------------------------------------
-- Properties File --
---------------------------------------
This file must contain the following properties:

EFLOWPATH_TABLE=Schema qualified table with geometries to add elevation to.
GEOMETRY_COLUMN=Name of the geometry column to read and write to.
SRID=SRID of the geometry
COG_PATH=Link to the Elevation COG Service (Cloud-optimized geotiff)
NUM_THREADS=Number of blocks to processes at the same time


Example:
EFLOWPATH_TABLE=chyf2.eflowpath
GEOMETRY_COLUMN=geometry
SRID=4617
COG_PATH=https://canelevation-dem.s3.ca-central-1.amazonaws.com/mrdem-30/mrdem-30-dtm.tif
NUM_THREADS=2
