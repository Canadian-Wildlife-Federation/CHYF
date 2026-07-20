----------------------------------------------
CHyF Elevation
----------------------------------------------
This application provides tools for adding elevation and smoothed
elevation to the CHyF flowpath edges. Elevation is stored in the Z value, 
and smoothed elevation stored in the M value.

This software was developed by Refractions Research (www.refractions.net) for
the Canadian Wildlife Federation

----------------------------------------------
--- Requirements ---
----------------------------------------------
Java version 25 must be installed and included on
the path.  To test "run java -version" from a command line.

A PostgreSQL database with data structured that matches the CHyF2 
data schema. 


--------------------------------------
-- Pre Condition --
--------------------------------------

These scripts read and write to the same geometry column in the flowpath
table. In order to be able to hold 2d, 3d, and 4d geometries at the same time
the column must be a generic "geometry" column. It cannot have a geometry type
or srid associated with it.  After processing is complete you can add back
a geometry type/srid.

To configure generic geometry column:
alter table chyf2.eflowpath alter column geometry type geometry;

After elevation processing you can reset to 3d geometry:
alter table chyf2.eflowpath alter column geometry type geometry(linestringz, 4617);

After smoothing you can reset to 4d geometry:
alter table chyf2.eflowpath alter column geometry type geometry(linestringzm, 4617);

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

chyf-elevation-rawz.bat -d host=<host>;port=<port>;db=<db>;user=<user>;password=<pass> <properties_file>


Usage:
chyf-elevation-rawz.bat -d <connectionstring> -docontinue elevation.properties

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
--- Elevation - Smoothed values  ---
---------------------------------------
To run the delevation tools use the chyf-elevation files. For example:

chyf-elevation-smoothedz.bat -d host=<host>;port=<port>;db=<db>;user=<user>;password=<pass> <properties_file>


Usage:
chyf-elevation-smoothedz.bat -d <connectionstring> -docontinue elevation.properties

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
This file must contain the following properties below. The sample elevation.properties
file contains a description of each of these properties.

Example:
AOI_TABLE=chyf2.aoi
EFLOWPATH_TABLE=chyf2.eflowpath
EFLOWPATH_PROPERTIES_TABLE=chyf2.eflowpath_properties
GEOMETRY_COLUMN=geometry
SRID=4617
COG_PATH=https://canelevation-dem.s3.ca-central-1.amazonaws.com/mrdem-30/mrdem-30-dtm.tif
NUM_THREADS=2
BLOCK_SIZE=0.4
SMOOTHING_TARGET_GROUP_SIZE=100000
SMOOTHING_FLOWPATH_BATCH_SIZE=500
AOI_FILTER=08MF001
