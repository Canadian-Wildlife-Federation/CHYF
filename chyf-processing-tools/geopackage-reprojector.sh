#!/bin/sh
java -cp lib\*:lib-chyf\chyf-core-${chyf.core.version}.jar;lib-chyf\chyf-catchment-delineator-${catchment.version}.jar net.refractions.chyf.util.gpkg.GeoPackageReprojector $@