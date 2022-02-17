----------------------------------------------
CHyF Stream Order Tools
----------------------------------------------
This application provides tools for computing mainstems and
various stream order values on CHyF modelled data.   

This software was developed by Refractions Research (www.refractions.net) for
the Canadian Wildlife Federation

----------------------------------------------
--- Requirements ---
----------------------------------------------
Java version 11 (or possibly newer) must be installed and included on
the path.  To test "run java -version" from a command line.

A PostgreSQL database with data structured that matches the CHyF2 
data schema. 

---------------------------------------
--- Running ---
---------------------------------------
On windows use the .bat files, on linux use the .sh files.


---------------------------------------
--- Stream Order Tools  ---
---------------------------------------
To run the stream order tools use the chyf-streamorder-computer files. For example:

chyf-streamorder-computer.bat -d host=<host>;port=<port>;db=<db>;user=<user>;password=<pass> -ignorenames chyf chyf.eflowpath_properties


Usage:
chyf-streamorder-computer.bat -d <connectionstring> -ignorenames -usenames <inputschema> <outputtable>

connectionstring
The database connection string in the form "host=<host>;port=<port>;db=<db>;user=<user>;password=<pass>"

Only one of -ignorenames or -usenames can be provided.  
ignorenames - names will be ignored when computing mainstems and orders
usenames - names will affect how mainstems (and orders) are computed

inputschema
The schema name of the input data. At a minimum this schema musch contain 
eflowpath, nexus, and aoi tables.

outputtable
The table to write results to in the form <schema>.<tablename>. Any existing
table will be dropped and recreated.
