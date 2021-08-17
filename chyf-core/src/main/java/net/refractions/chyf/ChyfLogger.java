
/*
 * Copyright 2021 Canadian Wildlife Federation
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
 */package net.refractions.chyf;

import java.io.IOException;

import org.locationtech.jts.geom.Geometry;
import org.slf4j.LoggerFactory;

import net.refractions.chyf.datasource.ChyfDataSource;

/**
 * Logger for logging warning message to data source as well as 
 * org.slf4j logging. Users must set the data source in order to
 * have this log to data source.
 * 
 * @author Emily
 *
 */
public enum ChyfLogger {

	INSTANCE;
	
	private ChyfDataSource source;
	
	public void setDataSource(ChyfDataSource source) {
		this.source = source;
	}
	
	private boolean checkSource() {
		if (source == null) {
			System.out.println("Logging not configured");
			return false;
		}
		return true;
	}
	public void logException(ExceptionWithLocation ex) throws IOException {
		if (!checkSource()) return;
		source.logError(ex.getMessage(),  ex.getLocation());
	}
	
	public void logException(Exception ex) throws IOException {
		if (!checkSource()) return;
		source.logError(ex.getMessage(),  null);
	}
	
	public void logError(String message, Geometry location) throws IOException{
		if (!checkSource()) return;
		source.logError(message, location);
	}
	
	public void logError(String message, Geometry location, Exception exception, Class<?> clazz) throws IOException{
		LoggerFactory.getLogger(clazz).error(message, exception);
		if (!checkSource()) return;
		if (location == null && exception instanceof ExceptionWithLocation) {
			location = ((ExceptionWithLocation)exception).getLocation();
		}
		source.logError(message, location);
	}
	
	public void logWarning(String message, Geometry location) throws IOException{
		if (!checkSource()) return;
		source.logWarning(message, location);
	}
	
	public void logError(String message, Geometry location, Class<?> clazz) throws IOException{
		LoggerFactory.getLogger(clazz).error(message + " " + location.toText());
		if (!checkSource()) return;
		source.logError(message, location);
	}
	
	public void logWarning(String message, Geometry location, Class<?> clazz) throws IOException{
		LoggerFactory.getLogger(clazz).warn(message + " " + location.toText());
		if (!checkSource()) return;
		source.logWarning(message, location);
	}
}
