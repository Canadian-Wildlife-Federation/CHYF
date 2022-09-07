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
 */
package net.refractions.chyf.exceptions;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * Exception mapper for CHyF exceptions
 * 
 * @author Emily
 *
 */
@ControllerAdvice
public class ExceptionMapper {
	
	@ExceptionHandler(RuntimeException.class)
	public ResponseEntity<ApiError> handleRuntimeException(RuntimeException re) {
		StringWriter sw = new StringWriter();
		re.printStackTrace(new PrintWriter(sw));
		String exceptionAsString = sw.toString();
		String message = "Unexpected internal problem: " + re.getMessage()
				+ "\n" + exceptionAsString;
		return new ResponseEntity<ApiError>(new ApiError(message),
				HttpStatus.INTERNAL_SERVER_ERROR);
	}
	

	@ExceptionHandler(TypeMismatchException.class)
	public ResponseEntity<ApiError> handleTypeMismatchException(TypeMismatchException tme) {
		String message = "Error in parameter " + tme.getPropertyName() + ": " + tme.getMessage();
		return new ResponseEntity<ApiError>(new ApiError(message), HttpStatus.BAD_REQUEST);
	}
	
	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ApiError> handleNoHandlerFoundException(NoHandlerFoundException ex) {
		return new ResponseEntity<ApiError>(new ApiError("Not Found"), HttpStatus.NOT_FOUND);
	}
	
	
	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ApiError> handleNotFoundException(
			NotFoundException nfe) {
		return new ResponseEntity<ApiError>(nfe.getError(), HttpStatus.NOT_FOUND);
	}
	
	@ExceptionHandler(InvalidParameterException.class)
	public ResponseEntity<ApiError> handleInvalidParameterException(
			InvalidParameterException ipe) {
		return new ResponseEntity<ApiError>(ipe.getError(), HttpStatus.BAD_REQUEST);
	}
}
