#!/bin/sh
java -cp lib\*:lib-chyf\chyf-core-${project.version}.jar;lib-chyf\chyf-catchment-delineator-${project.version}.jar net.refractions.chyf.watershed.builder.CatchmentDelineator $@