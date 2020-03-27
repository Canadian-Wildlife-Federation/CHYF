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
package net.refractions.chyf.watershed.builder;

import org.locationtech.jts.geom.Envelope;

import net.refractions.chyf.watershed.WatershedSettings;

public class DataBlock {

	private BlockState state = BlockState.READY;
	private String message;
	private int id;
	private Envelope env;
	private DataManager dm;

	public DataBlock(int id, Envelope env, BlockState state, DataManager dm) {
		this.id = id;
		this.env = env;
		this.dm = dm;
		this.state = state;
	}

	public BlockState getState() {
		return state;
	}

	public String getMessage() {
		return message;
	}

	public int getId() {
		return id;
	}

	public Envelope getBounds() {
		return env;
	}

	public Envelope getBufferedBounds() {
		Envelope expandedEnv = new Envelope(env);
		expandedEnv.expandBy(Math.max(expandedEnv.getWidth(), expandedEnv.getHeight()) * WatershedSettings.BLOCK_BUFFER_FACTOR);
		return expandedEnv;
	}

	public void setState(BlockState state, String message) {
		this.state = state;
		this.message = message;
		dm.updateBlock(this);
	}

	public void setState(BlockState state) {
		setState(state, " ");
	}

	public void setState(BlockState state, Exception exception) {
		StringBuffer exceptionMsg = new StringBuffer();
		StackTraceElement[] stackTrace = exception.getStackTrace();

		String[] exceptionClassNm = stackTrace[0].getClassName().split("\\.");

		exceptionMsg.append("(");
		exceptionMsg.append(exceptionClassNm[(exceptionClassNm.length - 1)]);
		exceptionMsg.append(".");
		exceptionMsg.append(stackTrace[0].getMethodName());
		exceptionMsg.append(":");
		exceptionMsg.append(stackTrace[0].getLineNumber());
		exceptionMsg.append(") ");
		exceptionMsg.append(exception.getMessage());

		setState(state, exceptionMsg.toString());
	}

	public String toString() {
		return id + ":" + env;
	}


}
