#!/bin/sh
java -cp lib\*;lib-chyf\chyf-core-${project.version}.jar;lib-chyf\chyf-flowpath-constructor-${project.version}.jar  net.refractions.chyf.flowpathconstructor.skeletonizer.bank.BankEngine $@