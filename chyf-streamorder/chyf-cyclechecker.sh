#!/bin/sh
java -cp lib\*:lib-chyf\chyf-core-${chyf.core.version}.jar:lib-chyf\chyf-streamorder-${streamorder.version}.jar net.refractions.chyf.cyclechecker.CycleChecker $@