/*******************************************************************************
 * Copyright 2020 Government of Canada
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *******************************************************************************/

package net.refractions.chyf.util;

import java.text.DecimalFormat;
import org.locationtech.jts.util.Stopwatch;
import org.slf4j.Logger;
/**
 * Computes and outputs statistics about time and memory usage
 */
public class ProcessStatistics {
    private static DecimalFormat memFormat = new DecimalFormat("#,##0");
    private static DecimalFormat secondFormat = new DecimalFormat("#,##0.000");
    private static Stopwatch globalSw = new Stopwatch();
    static {
    	globalSw.start();
    }
    
    private Stopwatch     sw               = new Stopwatch();
    private long          splitTime        = 0;

    public ProcessStatistics() {
    	sw.start();
    }

	public void reportStatus(Logger logger, String message) {
		logger.info(getStatistics() + " -- " + message);
	}

    public String getStatistics() {
        String str = getTimeStatistics() + " Mem: " + getMemoryStatistics();
        return str;
    }

    public Stopwatch getStopwatch() {
        return sw;
    }

    public long getElapsedTime() {
        return sw.getTime() - splitTime;
    }

    public static String formatTime(long time) {
        return secondFormat.format((double) time / 1000.0) + "s";
    }

    public String getTimeStatistics() {
        String str = String.format("%1$10s %2$11s)", formatTime(globalSw.getTime()), "(" + formatTime(getElapsedTime()));
        splitTime = sw.getTime();
        return str;
    }

    public static String getMemoryStatistics() {
        long totalMem = Runtime.getRuntime().totalMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        long committedMem = totalMem - freeMem;
        return String.format("%1$6s/%2$sMB", formatMem(committedMem), formatMem(totalMem));
    }

    public static final double MEGABYTE = 1048576;

    public static String formatMem(long size) {
        double megs = size / (double) MEGABYTE;
        return memFormat.format(megs);
    }

}
