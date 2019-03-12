/*
 * Copyright 2019 Government of Canada
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
 */
package net.refractions.chyf.rest.exceptions;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import net.refractions.chyf.pourpoint.PourpointException;

@ControllerAdvice
public class ExceptionMapper {
	
	
	@ExceptionHandler(PourpointException.class)
	public ResponseEntity<ErrorMessage> handleRuntimeException(PourpointException pe) {
		return new ResponseEntity<ErrorMessage>(new ErrorMessage(pe.getMessage()), HttpStatus.INTERNAL_SERVER_ERROR);
	}
	
	
	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<ErrorMessage> handleRuntimeException(RuntimeException re) {
		StringWriter sw = new StringWriter();
		re.printStackTrace(new PrintWriter(sw));
		String exceptionAsString = sw.toString();
		String message = "Unexpected internal problem: " + re.getMessage()
				+ "\n" + exceptionAsString;
		return new ResponseEntity<ErrorMessage>(new ErrorMessage(message),
				HttpStatus.INTERNAL_SERVER_ERROR);
	}
	
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorMessage> handleIllegalArgumentException(IllegalArgumentException iae) {
		String message = "Invalid parameter: " + iae.getMessage();
		return new ResponseEntity<ErrorMessage>(new ErrorMessage(message),
				HttpStatus.BAD_REQUEST);
	}
	
	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ErrorMessage> handleMissingServletRequestParameterException(
			MissingServletRequestParameterException msrpe) {
		String message = "Missing parameter " + msrpe.getParameterName() + ": "
				+ msrpe.getMessage();
		return new ResponseEntity<ErrorMessage>(new ErrorMessage(message),
				HttpStatus.BAD_REQUEST);
	}
	
	@ExceptionHandler(TypeMismatchException.class)
	public ResponseEntity<ErrorMessage> handleTypeMismatchException(
			TypeMismatchException tme) {
		String message = "Error in parameter " + tme.getPropertyName() + ": "
				+ tme.getMessage();
		return new ResponseEntity<ErrorMessage>(new ErrorMessage(message),
				HttpStatus.BAD_REQUEST);
	}
	
	@ExceptionHandler(InvalidParameterException.class)
	public ResponseEntity<ErrorMessage> handleInvalidParameterException(
			InvalidParameterException ipe) {
		return new ResponseEntity<ErrorMessage>(ipe.getErrorMessage(),
				HttpStatus.BAD_REQUEST);
	}
	
	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ErrorMessage> handleNotFoundException(
			NotFoundException nfe) {
		return new ResponseEntity<ErrorMessage>(nfe.getErrorMessage(),
				HttpStatus.NOT_FOUND);
	}
	
}
