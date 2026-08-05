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

On the processing server, the temp directory had to be configured specifically to use the mounted extra space as neo4j makes larger data files that were 
using up the disk space on the main drive.  This was done using the -D option.  The -pagecachsize option ensures neo4j limits it memory use. This is the full command string used for running on the processing server:

/usr/lib/jvm/java-11-openjdk-amd64/bin/java -Djava.io.tmpdir=/mnt  -XX:MaxMetaspaceSize=512m -XX:MaxDirectMemorySize=512m  -Xmx4G  -cp ./lib/*:./lib-chyf/chyf-core-1.5.10.jar:./lib-chyf/chyf-streamorder-1.3.3.jar net.refractions.chyf.streamorder.StreamOrderComputer -d "host=<dbhost>;port=<port>;db=<databaes>;user=<username>;password=<password>"  -singlenames -pagecachesize 1g <inschema> <outschema>.<outtable> > log.txt


---------------------------------------
--- Stream Order Tools  ---
---------------------------------------
To run the stream order tools use the chyf-streamorder-computer files. For example:

chyf-streamorder-computer.bat -d host=<host>;port=<port>;db=<db>;user=<user>;password=<pass> -ignorenames chyf chyf.eflowpath_properties


Usage:
chyf-streamorder-computer.bat -d <connectionstring> -ignorenames -usenames <inputschema> <outputtable>

connectionstring
The database connection string in the form "host=<host>;port=<port>;db=<db>;user=<user>;password=<pass>"

Only one of -ignorenames, -usenames, singenames can be provided.  
ignorenames - names will be ignored when computing mainstems and orders
usenames - names will affect how mainstems (and orders) are computed
singlenames - names will only be used for mainstems when associated with single line flows (not inside waterbodies)

inputschema
The schema name of the input data. At a minimum this schema musch contain 
eflowpath, nexus, and aoi tables.

outputtable
The table to write results to in the form <schema>.<tablename>. Any existing
table will be dropped and recreated.
